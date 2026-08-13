package org.dromara.aivideo.task.dto;

import org.dromara.aivideo.task.enums.AiTaskResourceType;
import org.dromara.aivideo.task.enums.AiTaskType;

public record CreateFreeAiTaskDTO(
    AiTaskType taskType,
    AiTaskResourceType resourceType,
    String resourceId,
    String projectId,
    String draftRevision,
    String inputVersionId,
    String idempotencyKey,
    String requestDigest,
    String quotaPolicyVersion,
    long estimatedUsage,
    AiTaskRequestPayloadDTO payload
) {
    public CreateFreeAiTaskDTO {
        if (taskType == null || payload == null || !matches(taskType, payload)) {
            throw new IllegalArgumentException("payload must match taskType");
        }
    }

    private static boolean matches(AiTaskType taskType, AiTaskRequestPayloadDTO payload) {
        return switch (taskType) {
            case TIMELINE_IMAGE_PROMPT_GENERATE -> payload instanceof AiTaskImagePromptPayloadDTO;
            case TIMELINE_FANCY_TEXT_SUGGEST -> payload instanceof AiTaskFancyTextPayloadDTO;
            case TIMELINE_SUBTITLE_ALIGN -> payload instanceof AiTaskSubtitleAlignmentPayloadDTO;
            case TIMELINE_RENDER -> payload instanceof AiTaskRenderPayloadDTO;
            case WORKFLOW_TEMPLATE_GENERATE, WORKFLOW_TEMPLATE_TEST -> false;
        };
    }
}
