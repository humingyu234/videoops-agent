package org.dromara.aivideo.task.dto;

public sealed interface AiTaskRequestPayloadDTO permits
    AiTaskImagePromptPayloadDTO,
    AiTaskFancyTextPayloadDTO,
    AiTaskSubtitleAlignmentPayloadDTO,
    AiTaskRenderPayloadDTO,
    WorkflowAiTaskPayloadDTO {
}
