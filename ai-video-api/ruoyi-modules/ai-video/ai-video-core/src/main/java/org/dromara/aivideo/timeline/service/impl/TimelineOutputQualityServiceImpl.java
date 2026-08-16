package org.dromara.aivideo.timeline.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.dromara.aivideo.creation.domain.CreationProject;
import org.dromara.aivideo.creation.dto.CreationAssetDTO;
import org.dromara.aivideo.creation.enums.CreationAssetStatus;
import org.dromara.aivideo.creation.enums.CreationAssetType;
import org.dromara.aivideo.creation.enums.CreationAssetUsageOrigin;
import org.dromara.aivideo.creation.mapper.CreationProjectMapper;
import org.dromara.aivideo.creation.service.CreationMediaHandle;
import org.dromara.aivideo.creation.service.ICreationAssetService;
import org.dromara.aivideo.task.dto.AiTaskDTO;
import org.dromara.aivideo.timeline.constant.TimelineErrorCodes;
import org.dromara.aivideo.timeline.domain.TimelineVersion;
import org.dromara.aivideo.timeline.dto.TimelineDocumentDTO;
import org.dromara.aivideo.timeline.dto.TimelineMediaProbeDTO;
import org.dromara.aivideo.timeline.dto.TimelineMediaQualityInspectionDTO;
import org.dromara.aivideo.timeline.dto.TimelineOutputQualityDTO;
import org.dromara.aivideo.timeline.dto.TimelineSubtitleElementDTO;
import org.dromara.aivideo.timeline.enums.TimelineTrackType;
import org.dromara.aivideo.timeline.mapper.TimelineVersionMapper;
import org.dromara.aivideo.timeline.service.ITimelineMediaRenderService;
import org.dromara.aivideo.timeline.service.ITimelineOutputQualityService;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.dromara.aivideo.timeline.dto.TimelineOutputQualityDTO.Confidence.HIGH;
import static org.dromara.aivideo.timeline.dto.TimelineOutputQualityDTO.Confidence.LOW;
import static org.dromara.aivideo.timeline.dto.TimelineOutputQualityDTO.Layer.CONTENT_LAYOUT;
import static org.dromara.aivideo.timeline.dto.TimelineOutputQualityDTO.Layer.MEDIA;
import static org.dromara.aivideo.timeline.dto.TimelineOutputQualityDTO.Layer.PERCEPTUAL;
import static org.dromara.aivideo.timeline.dto.TimelineOutputQualityDTO.Verdict.FAIL;
import static org.dromara.aivideo.timeline.dto.TimelineOutputQualityDTO.Verdict.PASS;
import static org.dromara.aivideo.timeline.dto.TimelineOutputQualityDTO.Verdict.REVIEW;

/** Evaluates one owner-scoped output against its immutable render-input timeline. */
@Service
public class TimelineOutputQualityServiceImpl implements ITimelineOutputQualityService {

    static final String RULE_SET_VERSION = "t5-quality-1";
    private static final String MEDIA_RULE_VERSION = "media-ffmpeg-v1";
    private static final String CONTENT_RULE_VERSION = "content-frozen-script-v1";
    private static final String SUBTITLE_RULE_VERSION = "subtitle-frozen-timeline-v1";
    private static final String HUMAN_RULE_VERSION = "human-review-v1";
    private static final BigDecimal REQUIRED_SAFE_MARGIN = new BigDecimal("0.05");
    private static final long MAX_DURATION_DELTA_MS = 250L;
    private static final Set<String> SAFE_AREA_ANCHORS = Set.of("upper", "center", "lower");
    private static final Set<String> ALIGNMENTS = Set.of("left", "center", "right");

    private final CreationProjectMapper projectMapper;
    private final TimelineVersionMapper versionMapper;
    private final ICreationAssetService assetService;
    private final ITimelineMediaRenderService mediaRenderService;
    private final JsonMapper jsonMapper;
    private final Policy policy;

    @Autowired
    public TimelineOutputQualityServiceImpl(CreationProjectMapper projectMapper,
                                            TimelineVersionMapper versionMapper,
                                            ICreationAssetService assetService,
                                            ObjectProvider<ITimelineMediaRenderService> mediaRenderServiceProvider,
                                            JsonMapper jsonMapper) {
        this(projectMapper, versionMapper, assetService,
            Objects.requireNonNull(mediaRenderServiceProvider, "mediaRenderServiceProvider").getIfAvailable(),
            jsonMapper, Policy.empty());
    }

    TimelineOutputQualityServiceImpl(CreationProjectMapper projectMapper,
                                     TimelineVersionMapper versionMapper,
                                     ICreationAssetService assetService,
                                     ITimelineMediaRenderService mediaRenderService,
                                     JsonMapper jsonMapper,
                                     Policy policy) {
        this.projectMapper = Objects.requireNonNull(projectMapper, "projectMapper");
        this.versionMapper = Objects.requireNonNull(versionMapper, "versionMapper");
        this.assetService = Objects.requireNonNull(assetService, "assetService");
        this.mediaRenderService = mediaRenderService;
        this.jsonMapper = Objects.requireNonNull(jsonMapper, "jsonMapper");
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    @Override
    public TimelineOutputQualityDTO evaluate(long actorId, AiTaskDTO task, CreationAssetDTO asset) {
        EvaluationContext context = loadContext(actorId, task, asset);
        List<TimelineOutputQualityDTO.Criterion> criteria = new ArrayList<>(16);
        criteria.addAll(evaluateMedia(actorId, task, context));
        criteria.addAll(evaluateContent(context));
        criteria.addAll(evaluateSubtitles(context));
        criteria.add(review("perceptual.identity_similarity", PERCEPTUAL));
        criteria.add(review("perceptual.lip_sync", PERCEPTUAL));
        criteria.add(review("perceptual.voice_consistency", PERCEPTUAL));
        criteria.add(review("perceptual.visual_stability", PERCEPTUAL));
        criteria.add(review("style.tone_match", PERCEPTUAL));
        return new TimelineOutputQualityDTO(task.taskId(), asset.assetId(), asset.sha256(), task.inputVersionId(),
            context.version().getContentHash(), RULE_SET_VERSION, criteria);
    }

    private EvaluationContext loadContext(long actorId, AiTaskDTO task, CreationAssetDTO asset) {
        if (actorId <= 0 || task == null || asset == null || !"timeline_render".equals(task.taskType())
            || !"success".equals(task.status()) || !validId(task.taskId()) || !validId(task.projectId())
            || !validId(task.inputVersionId()) || !validId(task.resultAssetId())
            || !task.resultAssetId().equals(asset.assetId()) || asset.status() != CreationAssetStatus.READY
            || asset.assetType() != CreationAssetType.VIDEO
            || asset.usageOrigin() != CreationAssetUsageOrigin.TIMELINE_RENDER_OUTPUT
            || asset.sha256() == null || !asset.sha256().matches("[0-9a-fA-F]{64}")) {
            throw invalid("质量验收对象无效");
        }
        long projectId = parseId(task.projectId());
        long inputVersionId = parseId(task.inputVersionId());
        CreationProject project = projectMapper.selectOne(new LambdaQueryWrapper<CreationProject>()
            .eq(CreationProject::getProjectId, projectId)
            .eq(CreationProject::getOwnerUserId, actorId)
            .eq(CreationProject::getDelFlag, "0"));
        if (project == null || !Long.valueOf(actorId).equals(project.getOwnerUserId())
            || !Long.valueOf(projectId).equals(project.getProjectId())) {
            throw new ServiceException("创作项目不存在", TimelineErrorCodes.CREATION_PROJECT_NOT_FOUND);
        }
        TimelineVersion version = versionMapper.selectOne(new LambdaQueryWrapper<TimelineVersion>()
            .eq(TimelineVersion::getTimelineVersionId, inputVersionId)
            .eq(TimelineVersion::getProjectId, projectId)
            .eq(TimelineVersion::getOwnerUserId, actorId)
            .eq(TimelineVersion::getVersionReason, "render_input"));
        if (version == null || !Long.valueOf(inputVersionId).equals(version.getTimelineVersionId())
            || !Long.valueOf(projectId).equals(version.getProjectId())
            || !Long.valueOf(actorId).equals(version.getOwnerUserId())
            || !"render_input".equals(version.getVersionReason()) || version.getContentJson() == null
            || version.getContentHash() == null || !version.getContentHash().matches("[0-9a-f]{64}")) {
            throw new ServiceException("渲染输入版本不存在", TimelineErrorCodes.TIMELINE_VERSION_NOT_FOUND);
        }
        TimelineDocumentDTO timeline = parseImmutableTimeline(version);
        return new EvaluationContext(project, version, timeline, asset);
    }

    private TimelineDocumentDTO parseImmutableTimeline(TimelineVersion version) {
        try {
            TimelineDocumentDTO timeline = jsonMapper.readerFor(TimelineDocumentDTO.class)
                .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).readValue(version.getContentJson());
            String canonicalJson = jsonMapper.writeValueAsString(timeline);
            if (!digest(canonicalJson).equals(version.getContentHash())) {
                throw invalid("渲染输入版本校验失败");
            }
            if (timeline.canvas() == null || timeline.tracks() == null) {
                throw invalid("渲染输入版本无效");
            }
            return timeline;
        } catch (ServiceException exception) {
            throw exception;
        } catch (Exception exception) {
            throw invalid("渲染输入版本无效");
        }
    }

    private List<TimelineOutputQualityDTO.Criterion> evaluateMedia(long actorId, AiTaskDTO task,
                                                                   EvaluationContext context) {
        if (mediaRenderService == null) {
            return mediaUnavailable("renderer_unavailable");
        }
        TimelineMediaQualityInspectionDTO inspection;
        try (CreationMediaHandle media = assetService.openOwnedTimelineRenderOutput(actorId, task.taskId(),
            context.asset().assetId())) {
            inspection = mediaRenderService.inspectQuality(media);
        } catch (Exception exception) {
            List<TimelineOutputQualityDTO.Criterion> criteria = new ArrayList<>(5);
            criteria.add(criterion("media.playable", MEDIA, MEDIA_RULE_VERSION, FAIL, HIGH,
                evidence("reason", "media_open_or_decode_failed")));
            criteria.addAll(mediaUnavailable("media_facts_unavailable").subList(1, 5));
            return criteria;
        }
        if (inspection == null || inspection.probe() == null) {
            List<TimelineOutputQualityDTO.Criterion> criteria = new ArrayList<>(5);
            criteria.add(criterion("media.playable", MEDIA, MEDIA_RULE_VERSION, FAIL, HIGH,
                evidence("reason", "media_inspection_invalid")));
            criteria.addAll(mediaUnavailable("media_facts_unavailable").subList(1, 5));
            return criteria;
        }
        TimelineMediaProbeDTO probe = inspection.probe();
        TimelineDocumentDTO timeline = context.timeline();
        CreationAssetDTO asset = context.asset();
        List<TimelineOutputQualityDTO.Criterion> criteria = new ArrayList<>(5);
        criteria.add(criterion("media.playable", MEDIA, MEDIA_RULE_VERSION,
            inspection.fullyDecoded() ? PASS : FAIL, HIGH,
            evidence("fullyDecoded", inspection.fullyDecoded())));

        boolean exactMp4Token = formatTokens(probe.formatName()).contains("mp4");
        boolean codecsValid = "h264".equals(normalizeToken(probe.videoCodec()))
            && "aac".equals(normalizeToken(probe.audioCodec()));
        boolean mimeValid = "video/mp4".equalsIgnoreCase(asset.mimeType());
        criteria.add(criterion("media.container_codec", MEDIA, MEDIA_RULE_VERSION,
            exactMp4Token && codecsValid && mimeValid ? PASS : FAIL, HIGH,
            evidence("mp4Token", exactMp4Token, "mimeVideoMp4", mimeValid,
                "videoCodec", normalizeToken(probe.videoCodec()), "audioCodec", normalizeToken(probe.audioCodec()))));

        boolean dimensionsValid = probe.width() != null && probe.height() != null && probe.frameRate() != null
            && probe.width() == 1080 && probe.height() == 1920 && probe.frameRate() == 30
            && probe.width() == timeline.canvas().width() && probe.height() == timeline.canvas().height()
            && probe.frameRate() == timeline.canvas().frameRate();
        criteria.add(criterion("media.video_dimensions", MEDIA, MEDIA_RULE_VERSION,
            dimensionsValid ? PASS : FAIL, HIGH,
            evidence("width", probe.width(), "height", probe.height(), "frameRate", probe.frameRate(),
                "canvasWidth", timeline.canvas().width(), "canvasHeight", timeline.canvas().height(),
                "canvasFrameRate", timeline.canvas().frameRate())));

        boolean audioPresent = probe.audioStream() && asset.hasAudioStream();
        criteria.add(criterion("media.audio_present", MEDIA, MEDIA_RULE_VERSION,
            audioPresent ? PASS : FAIL, HIGH,
            evidence("probeAudioStream", probe.audioStream(), "assetAudioStream", asset.hasAudioStream())));

        Long assetDuration = asset.durationMs();
        long canvasDelta = Math.abs(probe.durationMs() - timeline.canvas().durationMs());
        Long assetDelta = assetDuration == null ? null : Math.abs(probe.durationMs() - assetDuration);
        boolean durationValid = probe.durationMs() > 0 && assetDuration != null && assetDuration > 0
            && canvasDelta <= MAX_DURATION_DELTA_MS && assetDelta <= MAX_DURATION_DELTA_MS;
        criteria.add(criterion("media.duration", MEDIA, MEDIA_RULE_VERSION,
            durationValid ? PASS : FAIL, HIGH,
            evidence("probeDurationMs", probe.durationMs(), "canvasDurationMs", timeline.canvas().durationMs(),
                "assetDurationMs", assetDuration, "canvasDeltaMs", canvasDelta, "assetDeltaMs", assetDelta,
                "maxDeltaMs", MAX_DURATION_DELTA_MS)));
        return criteria;
    }

    private List<TimelineOutputQualityDTO.Criterion> evaluateContent(EvaluationContext context) {
        String script = nfc(context.project().getScriptTextSnapshot());
        List<TimelineSubtitleElementDTO> subtitles = orderedSubtitles(context.timeline());
        boolean spanIntegrity = exactSourceSpans(script, subtitles);
        List<TimelineOutputQualityDTO.Criterion> criteria = new ArrayList<>(3);
        criteria.add(criterion("content.script_integrity", CONTENT_LAYOUT, CONTENT_RULE_VERSION,
            spanIntegrity ? PASS : FAIL, HIGH,
            evidence("scriptSha256", digest(script), "subtitleCount", subtitles.size(),
                "exactSourceSpans", spanIntegrity)));

        if (policy.mustInclude().isEmpty()) {
            criteria.add(criterion("content.must_include", CONTENT_LAYOUT, CONTENT_RULE_VERSION,
                REVIEW, LOW, evidence("configured", 0, "reason", "policy_unconfigured")));
        } else {
            List<String> missingRequired = policy.mustInclude().stream()
                .map(this::nfc).filter(required -> !script.contains(required)).map(this::digest).toList();
            criteria.add(criterion("content.must_include", CONTENT_LAYOUT, CONTENT_RULE_VERSION,
                missingRequired.isEmpty() ? PASS : FAIL, HIGH,
                evidence("configured", policy.mustInclude().size(), "missing", missingRequired.size(),
                    "missingPhraseHashes", missingRequired)));
        }

        if (policy.prohibited().isEmpty()) {
            criteria.add(criterion("content.prohibited", CONTENT_LAYOUT, CONTENT_RULE_VERSION,
                REVIEW, LOW, evidence("configured", 0, "reason", "policy_unconfigured")));
        } else {
            List<String> matchedProhibited = policy.prohibited().stream()
                .map(this::nfc).filter(prohibited -> script.contains(prohibited)).map(this::digest).toList();
            criteria.add(criterion("content.prohibited", CONTENT_LAYOUT, CONTENT_RULE_VERSION,
                matchedProhibited.isEmpty() ? PASS : FAIL, HIGH,
                evidence("configured", policy.prohibited().size(), "matched", matchedProhibited.size(),
                    "matchedPhraseHashes", matchedProhibited)));
        }
        return criteria;
    }

    private List<TimelineOutputQualityDTO.Criterion> evaluateSubtitles(EvaluationContext context) {
        TimelineDocumentDTO timeline = context.timeline();
        String script = nfc(context.project().getScriptTextSnapshot());
        List<TimelineSubtitleElementDTO> subtitles = orderedSubtitles(timeline);
        boolean textIntegrity = !subtitles.isEmpty()
            && subtitles.stream().allMatch(subtitle -> normalizeDisplay(subtitle.sourceTextSnapshot())
                .equals(normalizeDisplay(subtitle.displayText())))
            && normalizeDisplay(script).equals(subtitles.stream()
                .map(TimelineSubtitleElementDTO::displayText).map(this::normalizeDisplay).reduce("", String::concat));
        boolean safeArea = timeline.canvas().safeMarginRatio() != null
            && timeline.canvas().safeMarginRatio().compareTo(REQUIRED_SAFE_MARGIN) == 0 && !subtitles.isEmpty()
            && subtitles.stream().allMatch(subtitle -> SAFE_AREA_ANCHORS.contains(subtitle.safeAreaAnchor())
                && ALIGNMENTS.contains(subtitle.alignment()));
        boolean timing = validTiming(subtitles, timeline.canvas().durationMs());
        return List.of(
            criterion("subtitle.text_integrity", CONTENT_LAYOUT, SUBTITLE_RULE_VERSION,
                textIntegrity ? PASS : FAIL, HIGH,
                evidence("scriptVisibleSha256", digest(normalizeDisplay(script)), "subtitleCount", subtitles.size(),
                    "equivalent", textIntegrity)),
            criterion("subtitle.safe_area", CONTENT_LAYOUT, SUBTITLE_RULE_VERSION,
                safeArea ? PASS : FAIL, HIGH,
                evidence("safeMarginRatio", timeline.canvas().safeMarginRatio(),
                    "requiredSafeMarginRatio", REQUIRED_SAFE_MARGIN, "validAnchorAndAlignment", safeArea)),
            criterion("subtitle.timing", CONTENT_LAYOUT, SUBTITLE_RULE_VERSION,
                timing ? PASS : FAIL, HIGH,
                evidence("subtitleCount", subtitles.size(), "firstStartMs",
                    subtitles.isEmpty() ? null : subtitles.getFirst().startMs(), "lastEndMs",
                    subtitles.isEmpty() ? null : subtitles.getLast().endMs(),
                    "canvasDurationMs", timeline.canvas().durationMs(), "contiguousAndInBounds", timing))
        );
    }

    private boolean exactSourceSpans(String script, List<TimelineSubtitleElementDTO> subtitles) {
        if (script.isEmpty() || subtitles.isEmpty()) {
            return false;
        }
        int scriptPoints = script.codePointCount(0, script.length());
        int expectedStart = 0;
        for (TimelineSubtitleElementDTO subtitle : subtitles) {
            if (subtitle.sourceStartOffset() != expectedStart || subtitle.sourceEndOffset() <= expectedStart
                || subtitle.sourceEndOffset() > scriptPoints) {
                return false;
            }
            String expected = codePointSubstring(script, subtitle.sourceStartOffset(), subtitle.sourceEndOffset());
            if (!expected.equals(nfc(subtitle.sourceTextSnapshot()))) {
                return false;
            }
            expectedStart = subtitle.sourceEndOffset();
        }
        return expectedStart == scriptPoints;
    }

    private boolean validTiming(List<TimelineSubtitleElementDTO> subtitles, long durationMs) {
        if (durationMs <= 0 || subtitles.isEmpty() || subtitles.getFirst().startMs() != 0) {
            return false;
        }
        long previousEnd = 0;
        for (TimelineSubtitleElementDTO subtitle : subtitles) {
            if (subtitle.startMs() != previousEnd || subtitle.endMs() <= subtitle.startMs()
                || subtitle.endMs() > durationMs) {
                return false;
            }
            previousEnd = subtitle.endMs();
        }
        return previousEnd == durationMs;
    }

    private List<TimelineSubtitleElementDTO> orderedSubtitles(TimelineDocumentDTO timeline) {
        return timeline.tracks().stream()
            .filter(track -> track.trackType() == TimelineTrackType.SUBTITLE)
            .flatMap(track -> track.elements().stream())
            .filter(TimelineSubtitleElementDTO.class::isInstance)
            .map(TimelineSubtitleElementDTO.class::cast)
            .filter(TimelineSubtitleElementDTO::enabled)
            .sorted(Comparator.comparingLong(TimelineSubtitleElementDTO::startMs)
                .thenComparing(TimelineSubtitleElementDTO::elementId))
            .toList();
    }

    private List<TimelineOutputQualityDTO.Criterion> mediaUnavailable(String reason) {
        return List.of(
            mediaReview("media.playable", reason),
            mediaReview("media.container_codec", reason),
            mediaReview("media.video_dimensions", reason),
            mediaReview("media.audio_present", reason),
            mediaReview("media.duration", reason)
        );
    }

    private TimelineOutputQualityDTO.Criterion mediaReview(String code, String reason) {
        return criterion(code, MEDIA, MEDIA_RULE_VERSION, REVIEW, LOW, evidence("reason", reason));
    }

    private TimelineOutputQualityDTO.Criterion review(String code, TimelineOutputQualityDTO.Layer layer) {
        return criterion(code, layer, HUMAN_RULE_VERSION, REVIEW, LOW,
            evidence("evaluator", "human", "reason", "no_approved_deterministic_evaluator"));
    }

    private TimelineOutputQualityDTO.Criterion criterion(String code, TimelineOutputQualityDTO.Layer layer,
                                                          String ruleVersion,
                                                          TimelineOutputQualityDTO.Verdict verdict,
                                                          TimelineOutputQualityDTO.Confidence confidence,
                                                          Map<String, Object> evidence) {
        return new TimelineOutputQualityDTO.Criterion(code, layer, ruleVersion, verdict, confidence, evidence);
    }

    private Map<String, Object> evidence(Object... entries) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        for (int index = 0; index < entries.length; index += 2) {
            evidence.put((String) entries[index], entries[index + 1]);
        }
        return evidence;
    }

    private Set<String> formatTokens(String formatName) {
        if (formatName == null || formatName.isBlank()) {
            return Set.of();
        }
        Set<String> tokens = new HashSet<>();
        for (String token : formatName.toLowerCase(Locale.ROOT).split(",")) {
            tokens.add(token.trim());
        }
        return Set.copyOf(tokens);
    }

    private String normalizeToken(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeDisplay(String value) {
        String nfc = nfc(value);
        StringBuilder result = new StringBuilder(nfc.length());
        nfc.codePoints().forEach(codePoint -> {
            if (!Character.isWhitespace(codePoint) && !isPunctuation(codePoint)) {
                result.appendCodePoint(codePoint);
            }
        });
        return result.toString();
    }

    private boolean isPunctuation(int codePoint) {
        if (codePoint == '%') {
            return false;
        }
        return switch (Character.getType(codePoint)) {
            case Character.CONNECTOR_PUNCTUATION, Character.DASH_PUNCTUATION,
                Character.START_PUNCTUATION, Character.END_PUNCTUATION,
                Character.INITIAL_QUOTE_PUNCTUATION, Character.FINAL_QUOTE_PUNCTUATION,
                Character.OTHER_PUNCTUATION -> true;
            default -> false;
        };
    }

    private String codePointSubstring(String value, int start, int end) {
        return value.substring(value.offsetByCodePoints(0, start), value.offsetByCodePoints(0, end));
    }

    private String nfc(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFC);
    }

    private boolean validId(String value) {
        if (value == null || !value.matches("[1-9][0-9]{0,18}")) {
            return false;
        }
        try {
            return Long.parseLong(value) > 0;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private long parseId(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw invalid("质量验收对象无效");
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

    private ServiceException invalid(String message) {
        return new ServiceException(message, TimelineErrorCodes.TIMELINE_DOCUMENT_INVALID);
    }

    record Policy(List<String> mustInclude, List<String> prohibited) {
        Policy {
            mustInclude = mustInclude == null ? List.of() : List.copyOf(mustInclude);
            prohibited = prohibited == null ? List.of() : List.copyOf(prohibited);
        }

        static Policy empty() {
            return new Policy(List.of(), List.of());
        }
    }

    private record EvaluationContext(CreationProject project, TimelineVersion version,
                                     TimelineDocumentDTO timeline, CreationAssetDTO asset) {
    }
}
