package org.dromara.aivideo.task.dto;

public sealed interface AiTaskResultPayloadDTO permits
    AiTaskImagePromptResultPayloadDTO,
    AiTaskFancyTextResultPayloadDTO,
    AiTaskSubtitleAlignmentResultPayloadDTO,
    WorkflowAiTaskResultPayloadDTO {
}
