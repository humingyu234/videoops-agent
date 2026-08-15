package org.dromara.aivideo.user.timeline.service;

import lombok.RequiredArgsConstructor;
import org.dromara.aivideo.creation.dto.CreationAssetDTO;
import org.dromara.aivideo.creation.dto.DigitalHumanCreationSourceDTO;
import org.dromara.aivideo.creation.service.ICreationAssetService;
import org.dromara.aivideo.creation.service.ICreationProjectService;
import org.dromara.aivideo.task.dto.AiTaskDTO;
import org.dromara.aivideo.task.dto.AiTaskFancyTextPayloadDTO;
import org.dromara.aivideo.task.dto.AiTaskImagePromptPayloadDTO;
import org.dromara.aivideo.task.dto.AiTaskRenderPayloadDTO;
import org.dromara.aivideo.task.dto.AiTaskSubtitleAlignmentPayloadDTO;
import org.dromara.aivideo.task.dto.CreateFreeAiTaskDTO;
import org.dromara.aivideo.task.enums.AiTaskResourceType;
import org.dromara.aivideo.task.enums.AiTaskType;
import org.dromara.aivideo.task.service.IAiTaskService;
import org.dromara.aivideo.timeline.constant.TimelineContractLimits;
import org.dromara.aivideo.timeline.constant.TimelineErrorCodes;
import org.dromara.aivideo.timeline.dto.TimelineAssetReferenceDTO;
import org.dromara.aivideo.timeline.dto.TimelineAudioElementDTO;
import org.dromara.aivideo.timeline.dto.TimelineDocumentDTO;
import org.dromara.aivideo.timeline.dto.TimelineElementDTO;
import org.dromara.aivideo.timeline.dto.TimelineFancyTextSuggestionCommandDTO;
import org.dromara.aivideo.timeline.dto.TimelineImagePromptCommandDTO;
import org.dromara.aivideo.timeline.dto.TimelineMainVideoElementDTO;
import org.dromara.aivideo.timeline.dto.TimelineOutputConfigDTO;
import org.dromara.aivideo.timeline.dto.TimelinePipVideoElementDTO;
import org.dromara.aivideo.timeline.dto.TimelineRenderCommandDTO;
import org.dromara.aivideo.timeline.dto.TimelineSubtitleAlignmentCommandDTO;
import org.dromara.aivideo.timeline.dto.TimelineSubtitleElementDTO;
import org.dromara.aivideo.timeline.dto.TimelineTrackDTO;
import org.dromara.aivideo.timeline.dto.TimelineImageElementDTO;
import org.dromara.aivideo.timeline.enums.FancyTextTemplateCode;
import org.dromara.aivideo.timeline.enums.TimelineAssetUsageType;
import org.dromara.aivideo.timeline.enums.TimelineOutputQuality;
import org.dromara.aivideo.timeline.service.ITimelineDraftService;
import org.dromara.aivideo.user.timeline.domain.bo.CreateFancyTextSuggestionTaskBo;
import org.dromara.aivideo.user.timeline.domain.bo.CreateImagePromptTaskBo;
import org.dromara.aivideo.user.timeline.domain.bo.CreateSubtitleAlignmentTaskBo;
import org.dromara.aivideo.user.timeline.domain.bo.CreateTimelineRenderTaskBo;
import org.dromara.aivideo.user.timeline.domain.bo.TimelineSourceSelectionBo;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Builds task facts only from the owner's persisted project, draft and registered source/asset metadata.
 */
@Service
@RequiredArgsConstructor
public class TimelineTaskApplicationService {

    private static final String FREE_POLICY_VERSION = "timeline-free-1";

    private final ICreationProjectService projectService;
    private final ICreationAssetService assetService;
    private final ITimelineDraftService draftService;
    private final IAiTaskService taskService;

    public AiTaskDTO createImagePrompt(long actorId, String projectId, CreateImagePromptTaskBo body) {
        TaskContext context = taskContext(actorId, projectId, body.getExpectedRevision());
        if (!TimelineContractLimits.AI_IMAGE_STYLES.contains(body.getStyle())) {
            throw invalid("Unsupported image prompt style");
        }
        SourceSpan selection = sourceSpan(body.getSourceSelection(), context.sourceText(), context.draft().timeline());
        TimelineImagePromptCommandDTO command = new TimelineImagePromptCommandDTO(null, projectId,
            body.getExpectedRevision(), selection.startOffset(), selection.endOffset(), selection.sourceText(), "", "",
            "9:16", body.getStyle());
        return create(actorId, projectId, body.getExpectedRevision(), body.getIdempotencyKey(),
            digest(AiTaskType.TIMELINE_IMAGE_PROMPT_GENERATE.value(), projectId, body.getExpectedRevision(),
                selection.digestInput(), body.getStyle()),
            AiTaskType.TIMELINE_IMAGE_PROMPT_GENERATE, new AiTaskImagePromptPayloadDTO(command));
    }

    public AiTaskDTO createFancyText(long actorId, String projectId, CreateFancyTextSuggestionTaskBo body) {
        TaskContext context = taskContext(actorId, projectId, body.getExpectedRevision());
        if (!TimelineContractLimits.ANIMATION_INTENSITIES.contains(body.getAnimationIntensity())) {
            throw invalid("Unsupported animation intensity");
        }
        SourceSpan selection = sourceSpan(body.getSourceSelection(), context.sourceText(), context.draft().timeline());
        TimelineFancyTextSuggestionCommandDTO command = new TimelineFancyTextSuggestionCommandDTO(null, projectId,
            body.getExpectedRevision(), selection.startOffset(), selection.endOffset(), selection.sourceText(), "", "",
            List.of(FancyTextTemplateCode.values()));
        return create(actorId, projectId, body.getExpectedRevision(), body.getIdempotencyKey(),
            digest(AiTaskType.TIMELINE_FANCY_TEXT_SUGGEST.value(), projectId, body.getExpectedRevision(),
                selection.digestInput(), body.getAnimationIntensity()),
            AiTaskType.TIMELINE_FANCY_TEXT_SUGGEST, new AiTaskFancyTextPayloadDTO(command));
    }

    public AiTaskDTO createSubtitleAlignment(long actorId, String projectId, CreateSubtitleAlignmentTaskBo body) {
        TaskContext context = taskContext(actorId, projectId, body.getExpectedRevision());
        List<TimelineSubtitleElementDTO> subtitles = selectedSubtitles(body.getSubtitleElementIds(), context.draft().timeline());
        List<TimelineSubtitleAlignmentCommandDTO.TrustedCue> cues = subtitles.stream()
            .map(subtitle -> new TimelineSubtitleAlignmentCommandDTO.TrustedCue(subtitle.sourceTextSnapshot(),
                subtitle.startMs(), subtitle.endMs()))
            .toList();
        if (cues.stream().anyMatch(cue -> cue.text() == null || cue.text().isBlank()
            || cue.endMs() <= cue.startMs())) {
            throw invalid("Invalid subtitle alignment selection");
        }
        TimelineSubtitleAlignmentCommandDTO command = new TimelineSubtitleAlignmentCommandDTO(null, projectId,
            body.getExpectedRevision(), context.project().primaryAudioAssetId(), context.sourceText(), null, cues);
        List<String> selectedIds = subtitles.stream().map(TimelineSubtitleElementDTO::elementId)
            .sorted().toList();
        return create(actorId, projectId, body.getExpectedRevision(), body.getIdempotencyKey(),
            digest(AiTaskType.TIMELINE_SUBTITLE_ALIGN.value(), projectId, body.getExpectedRevision(),
                String.join(",", selectedIds)),
            AiTaskType.TIMELINE_SUBTITLE_ALIGN, new AiTaskSubtitleAlignmentPayloadDTO(command));
    }

    public AiTaskDTO createRender(long actorId, String projectId, CreateTimelineRenderTaskBo body) {
        CreateTimelineRenderTaskBo.OutputConfig input = body == null ? null : body.getOutputConfig();
        if (input == null || !"match_canvas".equals(input.getResolutionPreset())
            || input.getFrameRate() == null || input.getFrameRate() != 30
            || !TimelineContractLimits.OUTPUT_QUALITIES.contains(input.getQualityPreset())) {
            throw invalid("Invalid timeline output configuration");
        }
        TimelineOutputQuality quality;
        try {
            quality = TimelineOutputQuality.fromValue(input.getQualityPreset());
        } catch (IllegalArgumentException exception) {
            throw invalid("Invalid timeline output configuration");
        }
        String requestDigest = digest(AiTaskType.TIMELINE_RENDER.value(), projectId, body.getExpectedRevision(),
            input.getResolutionPreset(), Integer.toString(input.getFrameRate()), input.getQualityPreset());
        var replay = taskService.replayTimelineRender(actorId, projectId, body.getExpectedRevision(),
            body.getIdempotencyKey(), requestDigest);
        if (replay.isPresent()) {
            return replay.get();
        }
        TaskContext context = taskContext(actorId, projectId, body.getExpectedRevision());
        TimelineOutputConfigDTO output = new TimelineOutputConfigDTO(input.getResolutionPreset(), input.getFrameRate(),
            quality);
        TimelineRenderCommandDTO command = new TimelineRenderCommandDTO(null, null, null, null,
            TimelineContractLimits.FONT_REGISTRY_VERSION, TimelineContractLimits.FONT_REGISTRY_SHA256, null, output,
            renderAssets(actorId, context.draft().timeline()));
        return create(actorId, projectId, body.getExpectedRevision(), body.getIdempotencyKey(),
            requestDigest,
            AiTaskType.TIMELINE_RENDER, new AiTaskRenderPayloadDTO(command));
    }

    private AiTaskDTO create(long actorId, String projectId, String revision, String idempotencyKey,
                             String requestDigest, AiTaskType type,
                             org.dromara.aivideo.task.dto.AiTaskRequestPayloadDTO payload) {
        return taskService.createFreeTask(actorId, new CreateFreeAiTaskDTO(type, AiTaskResourceType.CREATION_PROJECT,
            projectId, projectId, revision, null, idempotencyKey, requestDigest, FREE_POLICY_VERSION, 0L, payload));
    }

    private TaskContext taskContext(long actorId, String projectId, String expectedRevision) {
        ICreationProjectService.CreationProjectDTO project = projectService.getOwned(actorId, projectId);
        ITimelineDraftService.TimelineDraftView draft = draftService.getOwned(actorId, projectId);
        if (!Objects.equals(expectedRevision, draft.revision())) {
            throw revisionConflict();
        }
        DigitalHumanCreationSourceDTO source = assetService.resolveDigitalHumanSource(actorId, project.sourceId());
        if (source == null || !Objects.equals(project.sourceId(), source.sourceId())
            || source.scriptTextSnapshot() == null || source.scriptTextSnapshot().isBlank()) {
            throw sourceInvalid("Creation source is unavailable");
        }
        return new TaskContext(project, draft, source.scriptTextSnapshot());
    }

    private SourceSpan sourceSpan(TimelineSourceSelectionBo selection, String sourceText, TimelineDocumentDTO document) {
        boolean rangeSupplied = selection != null
            && (selection.getSourceStartOffset() != null || selection.getSourceEndOffset() != null);
        List<String> ids = selection == null ? null : selection.getSubtitleElementIds();
        boolean subtitleIdsSupplied = ids != null && !ids.isEmpty();
        if (rangeSupplied == subtitleIdsSupplied) {
            throw invalid("Provide exactly one source selection form");
        }
        if (rangeSupplied) {
            if (selection.getSourceStartOffset() == null || selection.getSourceEndOffset() == null) {
                throw invalid("Incomplete source range");
            }
            return sourceSpan(sourceText, selection.getSourceStartOffset(), selection.getSourceEndOffset(), "range:"
                + selection.getSourceStartOffset() + ":" + selection.getSourceEndOffset());
        }
        List<TimelineSubtitleElementDTO> subtitles = selectedSubtitles(ids, document);
        int start = subtitles.stream().mapToInt(TimelineSubtitleElementDTO::sourceStartOffset).min().orElseThrow();
        int end = subtitles.stream().mapToInt(TimelineSubtitleElementDTO::sourceEndOffset).max().orElseThrow();
        String key = subtitles.stream().map(TimelineSubtitleElementDTO::elementId).sorted()
            .reduce("subtitles", (left, right) -> left + ":" + right);
        return sourceSpan(sourceText, start, end, key);
    }

    private SourceSpan sourceSpan(String sourceText, int start, int end, String digestInput) {
        int length = sourceText.codePointCount(0, sourceText.length());
        if (start < 0 || end <= start || end > length) {
            throw invalid("Source selection is outside the project snapshot");
        }
        int startIndex = sourceText.offsetByCodePoints(0, start);
        int endIndex = sourceText.offsetByCodePoints(0, end);
        String selected = sourceText.substring(startIndex, endIndex);
        if (selected.isBlank()) {
            throw invalid("Source selection is empty");
        }
        return new SourceSpan(start, end, selected, digestInput);
    }

    private List<TimelineSubtitleElementDTO> selectedSubtitles(List<String> requested, TimelineDocumentDTO document) {
        if (requested == null || requested.isEmpty() || document == null || document.tracks() == null) {
            throw invalid("Subtitle selection is required");
        }
        Set<String> ids = new HashSet<>(requested);
        if (ids.size() != requested.size()) {
            throw invalid("Subtitle selection contains duplicates");
        }
        Map<String, TimelineSubtitleElementDTO> subtitles = new HashMap<>();
        for (TimelineTrackDTO track : document.tracks()) {
            if (track == null || track.elements() == null) {
                continue;
            }
            for (TimelineElementDTO element : track.elements()) {
                if (element instanceof TimelineSubtitleElementDTO subtitle) {
                    subtitles.put(subtitle.elementId(), subtitle);
                }
            }
        }
        List<TimelineSubtitleElementDTO> selected = new ArrayList<>(requested.size());
        for (String id : requested) {
            TimelineSubtitleElementDTO subtitle = subtitles.get(id);
            if (subtitle == null) {
                throw invalid("Subtitle selection is not in the current draft");
            }
            selected.add(subtitle);
        }
        return List.copyOf(selected);
    }

    private List<TimelineAssetReferenceDTO> renderAssets(long actorId, TimelineDocumentDTO document) {
        if (document == null || document.tracks() == null) {
            throw invalid("Current timeline is invalid");
        }
        Map<String, RenderAsset> assets = new LinkedHashMap<>();
        for (TimelineTrackDTO track : document.tracks()) {
            if (track == null || track.elements() == null) {
                continue;
            }
            for (TimelineElementDTO element : track.elements()) {
                AssetInput input = assetInput(element);
                if (input == null) {
                    continue;
                }
                CreationAssetDTO asset = assetService.getOwned(actorId, input.assetId());
                String key = input.assetId() + "|" + input.usage().value();
                assets.computeIfAbsent(key, ignored -> new RenderAsset(asset, input.usage(), new ArrayList<>()))
                    .elementIds().add(element.elementId());
            }
        }
        return assets.values().stream().map(value -> new TimelineAssetReferenceDTO(value.asset().assetId(),
            value.usage(), List.copyOf(value.elementIds()), value.asset().sha256(), value.asset().sizeBytes())).toList();
    }

    private AssetInput assetInput(TimelineElementDTO element) {
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
            return new AssetInput(audio.assetId(), audio.usageType());
        }
        return null;
    }

    private String digest(String... values) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String value : values) {
                digest.update((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private ServiceException sourceInvalid(String message) {
        return new ServiceException(message, TimelineErrorCodes.CREATION_SOURCE_INVALID);
    }

    private ServiceException revisionConflict() {
        return new ServiceException("Timeline draft revision conflicts with the current draft",
            TimelineErrorCodes.TIMELINE_REVISION_CONFLICT);
    }

    private ServiceException invalid(String message) {
        return new ServiceException(message, TimelineErrorCodes.TIMELINE_DOCUMENT_INVALID);
    }

    private record TaskContext(ICreationProjectService.CreationProjectDTO project,
                               ITimelineDraftService.TimelineDraftView draft,
                               String sourceText) {
    }

    private record SourceSpan(int startOffset, int endOffset, String sourceText, String digestInput) {
    }

    private record AssetInput(String assetId, TimelineAssetUsageType usage) {
    }

    private record RenderAsset(CreationAssetDTO asset, TimelineAssetUsageType usage, List<String> elementIds) {
    }
}
