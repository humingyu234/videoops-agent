package org.dromara.aivideo.timeline.service.impl;

import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import org.dromara.aivideo.creation.dto.CreationAssetResolveDTO;
import org.dromara.aivideo.creation.enums.CreationAssetType;
import org.dromara.aivideo.creation.service.ICreationAssetService;
import org.dromara.aivideo.timeline.constant.TimelineContractLimits;
import org.dromara.aivideo.timeline.constant.TimelineErrorCodes;
import org.dromara.aivideo.timeline.dto.TimelineAssetReferenceDTO;
import org.dromara.aivideo.timeline.dto.TimelineAudioElementDTO;
import org.dromara.aivideo.timeline.dto.TimelineDocumentDTO;
import org.dromara.aivideo.timeline.dto.TimelineElementDTO;
import org.dromara.aivideo.timeline.dto.TimelineImageElementDTO;
import org.dromara.aivideo.timeline.dto.TimelineMainVideoElementDTO;
import org.dromara.aivideo.timeline.dto.TimelinePipVideoElementDTO;
import org.dromara.aivideo.timeline.dto.TimelineSubtitleElementDTO;
import org.dromara.aivideo.timeline.dto.TimelineTrackDTO;
import org.dromara.aivideo.timeline.enums.TimelineAssetUsageType;
import org.dromara.aivideo.timeline.enums.TimelineTrackArea;
import org.dromara.aivideo.timeline.enums.TimelineTrackType;
import org.dromara.aivideo.timeline.service.ISubtitleNormalizationService;
import org.dromara.aivideo.timeline.service.ITimelineDocumentService;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.stereotype.Service;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Validates the frozen schema before any owner-scoped asset resolution or draft write. */
@Service
public class TimelineDocumentServiceImpl implements ITimelineDocumentService {

    private static final Map<TimelineTrackType, TrackRule> TRACK_RULES = Map.of(
        TimelineTrackType.FANCY_TEXT, new TrackRule(TimelineTrackArea.TOP, 0),
        TimelineTrackType.SUBTITLE, new TrackRule(TimelineTrackArea.TOP, 1),
        TimelineTrackType.VISUAL_EFFECT, new TrackRule(TimelineTrackArea.TOP, 2),
        TimelineTrackType.IMAGE_OVERLAY, new TrackRule(TimelineTrackArea.TOP, 3),
        TimelineTrackType.PIP_VIDEO, new TrackRule(TimelineTrackArea.TOP, 4),
        TimelineTrackType.MAIN_VIDEO, new TrackRule(TimelineTrackArea.CENTER, 0),
        TimelineTrackType.PRIMARY_AUDIO, new TrackRule(TimelineTrackArea.BOTTOM, 0),
        TimelineTrackType.BACKGROUND_MUSIC, new TrackRule(TimelineTrackArea.BOTTOM, 1),
        TimelineTrackType.SOUND_EFFECT, new TrackRule(TimelineTrackArea.BOTTOM, 2)
    );

    private final ICreationAssetService assetService;
    private final ISubtitleNormalizationService subtitleNormalizationService;
    private final JsonMapper jsonMapper;
    private final Schema schema;

    public TimelineDocumentServiceImpl(ICreationAssetService assetService,
                                       ISubtitleNormalizationService subtitleNormalizationService,
                                       JsonMapper jsonMapper) {
        this.assetService = Objects.requireNonNull(assetService, "assetService");
        this.subtitleNormalizationService = Objects.requireNonNull(subtitleNormalizationService,
            "subtitleNormalizationService");
        this.jsonMapper = Objects.requireNonNull(jsonMapper, "jsonMapper");
        this.schema = loadSchema(jsonMapper);
    }

    @Override
    public ValidatedTimeline validate(long actorId, ProjectContext project, JsonNode rawTimeline) {
        if (actorId <= 0 || !validProjectContext(project) || rawTimeline == null || rawTimeline.isNull()) {
            throw documentInvalid("时间轴请求无效");
        }
        validateInputLimits(rawTimeline);
        if (!schema.validate(rawTimeline).isEmpty()) {
            throw documentInvalid("时间轴结构无效");
        }
        TimelineDocumentDTO parsed = parseStrictly(rawTimeline);
        NormalizationOutcome outcome = normalizeSubtitles(project, parsed);
        List<TimelineAssetReferenceDTO> assets = validateSemanticsAndAssets(actorId, project, outcome.timeline());
        String canonicalJson = jsonMapper.writeValueAsString(outcome.timeline());
        if (canonicalJson.getBytes(StandardCharsets.UTF_8).length
            > TimelineContractLimits.NUMERIC_LIMITS.get("canonicalJsonMaxBytes").intValue()) {
            throw documentInvalid("时间轴内容过大");
        }
        return new ValidatedTimeline(outcome.timeline(), canonicalJson, digest(canonicalJson), assets,
            outcome.normalizationChanges());
    }

    private NormalizationOutcome normalizeSubtitles(ProjectContext project, TimelineDocumentDTO document) {
        List<TimelineTrackDTO> normalizedTracks = new ArrayList<>();
        List<TimelineSubtitleElementDTO> subtitles = document.tracks().stream()
            .filter(track -> track.trackType() == TimelineTrackType.SUBTITLE)
            .flatMap(track -> track.elements().stream())
            .filter(TimelineSubtitleElementDTO.class::isInstance)
            .map(TimelineSubtitleElementDTO.class::cast)
            .toList();
        ISubtitleNormalizationService.NormalizationResult subtitleResult = subtitleNormalizationService.normalize(
            project.scriptTextSnapshot(), subtitles, document.canvas().width(), document.canvas().safeMarginRatio());
        int subtitleOffset = 0;
        for (TimelineTrackDTO track : document.tracks()) {
            if (track.trackType() != TimelineTrackType.SUBTITLE) {
                normalizedTracks.add(track);
                continue;
            }
            List<TimelineElementDTO> elements = new ArrayList<>();
            for (TimelineElementDTO element : track.elements()) {
                if (element instanceof TimelineSubtitleElementDTO) {
                    int originalIndex = subtitles.indexOf(element);
                    TimelineSubtitleElementDTO original = subtitles.get(originalIndex);
                    while (subtitleOffset < subtitleResult.subtitles().size()) {
                        TimelineSubtitleElementDTO candidate = subtitleResult.subtitles().get(subtitleOffset++);
                        elements.add(candidate);
                        if (candidate.sourceEndOffset() >= original.sourceEndOffset()) {
                            break;
                        }
                    }
                } else {
                    elements.add(element);
                }
            }
            normalizedTracks.add(new TimelineTrackDTO(track.trackId(), track.trackType(), track.area(), track.order(),
                track.locked(), track.muted(), List.copyOf(elements)));
        }
        return new NormalizationOutcome(new TimelineDocumentDTO(document.schemaVersion(), document.canvas(),
            List.copyOf(normalizedTracks)), subtitleResult.normalizationChanges());
    }

    private List<TimelineAssetReferenceDTO> validateSemanticsAndAssets(long actorId, ProjectContext project,
                                                                         TimelineDocumentDTO document) {
        if (!TimelineContractLimits.SCHEMA_VERSION.equals(document.schemaVersion())
            || document.canvas() == null || document.canvas().width() != project.width()
            || document.canvas().height() != project.height() || document.canvas().frameRate() != project.frameRate()
            || document.canvas().durationMs() != project.durationMs()
            || document.canvas().safeMarginRatio().compareTo(new BigDecimal("0.05")) != 0) {
            throw documentInvalid("时间轴画布与项目不一致");
        }
        Set<String> trackIds = new HashSet<>();
        Set<TimelineTrackType> trackTypes = new HashSet<>();
        Set<String> elementIds = new HashSet<>();
        Map<String, AssetAccumulator> assets = new LinkedHashMap<>();
        int elementCount = 0;
        for (TimelineTrackDTO track : document.tracks()) {
            TrackRule rule = TRACK_RULES.get(track.trackType());
            if (track.trackId() == null || !trackIds.add(track.trackId()) || !trackTypes.add(track.trackType())
                || rule == null || track.area() != rule.area() || track.order() != rule.order()
                || track.elements() == null || track.elements().isEmpty()
                || track.elements().size() > TimelineContractLimits.NUMERIC_LIMITS.get("maxElementsPerTrack").intValue()) {
                throw documentInvalid("时间轴轨道无效");
            }
            if (track.trackType() == TimelineTrackType.MAIN_VIDEO && track.elements().size() != 1) {
                throw documentInvalid("主视频轨道无效");
            }
            for (TimelineElementDTO element : track.elements()) {
                elementCount++;
                validateElementTime(document, element, elementIds);
                validateTrackElementKind(track, element);
                validateElementSpecifics(document, track, element);
                AssetInput asset = assetInput(track, element);
                if (asset != null) {
                    if (asset.usage() == TimelineAssetUsageType.BASE_VIDEO
                        && !project.baseVideoAssetId().equals(asset.assetId())) {
                        throw assetInvalid("主视频素材不属于项目");
                    }
                    if (asset.usage() == TimelineAssetUsageType.PRIMARY_AUDIO
                        && (project.primaryAudioAssetId() == null
                            || !project.primaryAudioAssetId().equals(asset.assetId()))) {
                        throw assetInvalid("主音频素材不属于项目");
                    }
                    CreationAssetResolveDTO resolved = resolveAsset(actorId, asset);
                    validateResolvedMediaDuration(element, resolved);
                    String key = asset.assetId() + "|" + asset.usage().value();
                    assets.computeIfAbsent(key, ignored -> new AssetAccumulator(resolved, asset.usage()))
                        .elementIds().add(element.elementId());
                }
            }
        }
        if (elementCount > TimelineContractLimits.NUMERIC_LIMITS.get("maxTotalElements").intValue()
            || !trackTypes.contains(TimelineTrackType.MAIN_VIDEO)) {
            throw documentInvalid("时间轴元素无效");
        }
        if (assets.size() > TimelineContractLimits.NUMERIC_LIMITS.get("maxDistinctAssets").intValue()
            || assets.values().stream().mapToInt(value -> value.elementIds().size()).sum()
                > TimelineContractLimits.NUMERIC_LIMITS.get("maxAssetReferences").intValue()) {
            throw assetInvalid("时间轴素材引用过多");
        }
        return assets.values().stream().map(value -> new TimelineAssetReferenceDTO(value.resolved().assetId(),
            value.usage(), List.copyOf(value.elementIds()), value.resolved().sha256(), value.resolved().sizeBytes())).toList();
    }

    private void validateElementTime(TimelineDocumentDTO document, TimelineElementDTO element, Set<String> elementIds) {
        if (element == null || element.elementId() == null || !elementIds.add(element.elementId())
            || element.startMs() < 0 || element.endMs() <= element.startMs()
            || element.endMs() > document.canvas().durationMs()) {
            throw documentInvalid("时间轴元素时间无效");
        }
    }

    private void validateTrackElementKind(TimelineTrackDTO track, TimelineElementDTO element) {
        boolean valid = switch (track.trackType()) {
            case MAIN_VIDEO -> element instanceof TimelineMainVideoElementDTO;
            case IMAGE_OVERLAY -> element instanceof TimelineImageElementDTO;
            case PIP_VIDEO -> element instanceof TimelinePipVideoElementDTO;
            case SUBTITLE -> element instanceof TimelineSubtitleElementDTO;
            case PRIMARY_AUDIO, BACKGROUND_MUSIC, SOUND_EFFECT -> element instanceof TimelineAudioElementDTO;
            case FANCY_TEXT -> element instanceof org.dromara.aivideo.timeline.dto.TimelineFancyTextElementDTO;
            case VISUAL_EFFECT -> element instanceof org.dromara.aivideo.timeline.dto.TimelineVisualEffectElementDTO;
        };
        if (!valid) {
            throw documentInvalid("元素与轨道类型不匹配");
        }
    }

    private void validateElementSpecifics(TimelineDocumentDTO document, TimelineTrackDTO track,
                                          TimelineElementDTO element) {
        if (element instanceof TimelineImageElementDTO image) {
            validateCropAndFade(image.crop().xRatio(), image.crop().yRatio(), image.crop().widthRatio(),
                image.crop().heightRatio(), image.fade().fadeInMs(), image.fade().fadeOutMs(), element);
        } else if (element instanceof TimelinePipVideoElementDTO pip) {
            validateCropAndFade(pip.crop().xRatio(), pip.crop().yRatio(), pip.crop().widthRatio(),
                pip.crop().heightRatio(), pip.fade().fadeInMs(), pip.fade().fadeOutMs(), element);
            if (!pip.loopWhenOverflow() || pip.audioEnabled() || pip.sourceStartMs() >= pip.sourceDurationMs()
                || !insideSafePipArea(pip, document.canvas().safeMarginRatio())) {
                throw documentInvalid("画中画参数无效");
            }
        } else if (element instanceof TimelineMainVideoElementDTO main) {
            if (main.sourceStartMs() >= main.sourceDurationMs()
                || main.sourceStartMs() + (main.endMs() - main.startMs()) > main.sourceDurationMs()) {
                throw documentInvalid("主视频源范围无效");
            }
        } else if (element instanceof TimelineAudioElementDTO audio) {
            if (audio.usageType() != usageFor(track.trackType()) || audio.sourceStartMs() < 0
                || audio.sourceEndMs() <= audio.sourceStartMs() || audio.sourceEndMs() > audio.sourceDurationMs()
                || (!audio.loopWhenOverflow() && audio.sourceStartMs() + (audio.endMs() - audio.startMs())
                    > audio.sourceEndMs()) || audio.fade().fadeInMs() + audio.fade().fadeOutMs()
                    > audio.endMs() - audio.startMs()) {
                throw documentInvalid("音频参数无效");
            }
        }
    }

    private void validateCropAndFade(BigDecimal x, BigDecimal y, BigDecimal width, BigDecimal height,
                                     long fadeIn, long fadeOut, TimelineElementDTO element) {
        if (x.add(width).compareTo(BigDecimal.ONE) > 0 || y.add(height).compareTo(BigDecimal.ONE) > 0
            || fadeIn + fadeOut > element.endMs() - element.startMs()) {
            throw documentInvalid("视觉元素参数无效");
        }
    }

    private boolean insideSafePipArea(TimelinePipVideoElementDTO pip, BigDecimal safeMargin) {
        return pip.transform().xRatio().compareTo(safeMargin) >= 0
            && pip.transform().yRatio().compareTo(safeMargin) >= 0
            && pip.transform().xRatio().add(pip.transform().widthRatio()).compareTo(BigDecimal.ONE.subtract(safeMargin)) <= 0
            && pip.transform().yRatio().add(pip.transform().heightRatio()).compareTo(BigDecimal.ONE.subtract(safeMargin)) <= 0;
    }

    private AssetInput assetInput(TimelineTrackDTO track, TimelineElementDTO element) {
        if (element instanceof TimelineMainVideoElementDTO main) {
            return new AssetInput(main.assetId(), TimelineAssetUsageType.BASE_VIDEO);
        }
        if (element instanceof TimelineImageElementDTO image) {
            return new AssetInput(image.assetId(), TimelineAssetUsageType.IMAGE);
        }
        if (element instanceof TimelinePipVideoElementDTO pip) {
            return new AssetInput(pip.assetId(), TimelineAssetUsageType.PIP_VIDEO);
        }
        if (element instanceof TimelineAudioElementDTO audio) {
            return new AssetInput(audio.assetId(), usageFor(track.trackType()));
        }
        return null;
    }

    private TimelineAssetUsageType usageFor(TimelineTrackType trackType) {
        return switch (trackType) {
            case PRIMARY_AUDIO -> TimelineAssetUsageType.PRIMARY_AUDIO;
            case BACKGROUND_MUSIC -> TimelineAssetUsageType.BACKGROUND_MUSIC;
            case SOUND_EFFECT -> TimelineAssetUsageType.SOUND_EFFECT;
            default -> throw documentInvalid("素材轨道无效");
        };
    }

    private CreationAssetResolveDTO resolveAsset(long actorId, AssetInput input) {
        try {
            CreationAssetResolveDTO resolved = assetService.resolveOwned(actorId, input.assetId(), input.usage());
            CreationAssetType expected = switch (input.usage()) {
                case BASE_VIDEO, PIP_VIDEO -> CreationAssetType.VIDEO;
                case IMAGE -> CreationAssetType.IMAGE;
                default -> CreationAssetType.AUDIO;
            };
            if (resolved == null || !input.assetId().equals(resolved.assetId()) || resolved.usageType() != input.usage()
                || resolved.assetType() != expected || resolved.sha256() == null || !resolved.sha256().matches("[0-9a-f]{64}")
                || resolved.sizeBytes() <= 0 || (expected == CreationAssetType.VIDEO && !resolved.hasVideoStream())
                || (expected == CreationAssetType.AUDIO && !resolved.hasAudioStream())) {
                throw assetInvalid("时间轴素材不可用");
            }
            return resolved;
        } catch (ServiceException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw assetInvalid("时间轴素材不可用");
        }
    }

    private void validateResolvedMediaDuration(TimelineElementDTO element, CreationAssetResolveDTO resolved) {
        Long serverDurationMs = resolved.durationMs();
        if (element instanceof TimelineMainVideoElementDTO main
            && (serverDurationMs == null || serverDurationMs <= 0 || main.sourceDurationMs() != serverDurationMs
                || main.sourceStartMs() + (main.endMs() - main.startMs()) > serverDurationMs)) {
            throw assetInvalid("主视频源时长与服务端探测不一致");
        }
        if (element instanceof TimelinePipVideoElementDTO pip
            && (serverDurationMs == null || serverDurationMs <= 0 || pip.sourceDurationMs() != serverDurationMs
                || pip.sourceStartMs() >= serverDurationMs)) {
            throw assetInvalid("画中画源时长与服务端探测不一致");
        }
        if (element instanceof TimelineAudioElementDTO audio
            && (serverDurationMs == null || serverDurationMs <= 0 || audio.sourceDurationMs() != serverDurationMs
                || audio.sourceEndMs() > serverDurationMs)) {
            throw assetInvalid("音频源时长与服务端探测不一致");
        }
    }

    private TimelineDocumentDTO parseStrictly(JsonNode rawTimeline) {
        try {
            return jsonMapper.readerFor(TimelineDocumentDTO.class)
                .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).readValue(rawTimeline);
        } catch (Exception exception) {
            throw documentInvalid("时间轴映射无效");
        }
    }

    private void validateInputLimits(JsonNode rawTimeline) {
        try {
            if (jsonMapper.writeValueAsBytes(rawTimeline).length
                > TimelineContractLimits.NUMERIC_LIMITS.get("canonicalJsonMaxBytes").intValue()
                || maxDepth(rawTimeline, 1) > TimelineContractLimits.NUMERIC_LIMITS.get("maxNestingDepth").intValue()) {
                throw documentInvalid("时间轴输入超限");
            }
        } catch (ServiceException exception) {
            throw exception;
        } catch (Exception exception) {
            throw documentInvalid("时间轴输入无效");
        }
    }

    private int maxDepth(JsonNode node, int depth) {
        int max = depth;
        for (JsonNode child : node) {
            max = Math.max(max, maxDepth(child, depth + 1));
        }
        return max;
    }

    private boolean validProjectContext(ProjectContext project) {
        return project != null && project.projectId() != null && project.baseVideoAssetId() != null
            && project.scriptTextSnapshot() != null && project.durationMs() > 0 && project.width() == 1080
            && project.height() == 1920 && project.frameRate() == 30;
    }

    private Schema loadSchema(JsonMapper mapper) {
        try (InputStream input = TimelineDocumentServiceImpl.class.getClassLoader()
            .getResourceAsStream("contracts/creation-timeline/timeline-1.schema.json")) {
            if (input == null) {
                throw new IllegalStateException("timeline-1 schema resource is missing");
            }
            return SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12).getSchema(mapper.readTree(input));
        } catch (IOException exception) {
            throw new IllegalStateException("timeline-1 schema cannot be loaded", exception);
        }
    }

    private String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private ServiceException documentInvalid(String message) {
        return new ServiceException(message, TimelineErrorCodes.TIMELINE_DOCUMENT_INVALID);
    }

    private ServiceException assetInvalid(String message) {
        return new ServiceException(message, TimelineErrorCodes.TIMELINE_ASSET_INVALID);
    }

    private record TrackRule(TimelineTrackArea area, int order) {
    }

    private record AssetInput(String assetId, TimelineAssetUsageType usage) {
    }

    private record NormalizationOutcome(TimelineDocumentDTO timeline,
                                        List<org.dromara.aivideo.timeline.dto.TimelineNormalizationChangeDTO>
                                            normalizationChanges) {
    }

    private static final class AssetAccumulator {
        private final CreationAssetResolveDTO resolved;
        private final TimelineAssetUsageType usage;
        private final List<String> elementIds = new ArrayList<>();

        private AssetAccumulator(CreationAssetResolveDTO resolved, TimelineAssetUsageType usage) {
            this.resolved = resolved;
            this.usage = usage;
        }

        private CreationAssetResolveDTO resolved() {
            return resolved;
        }

        private TimelineAssetUsageType usage() {
            return usage;
        }

        private List<String> elementIds() {
            return elementIds;
        }
    }
}
