package org.dromara.aivideo.task.dto;

import org.dromara.aivideo.timeline.dto.TimelineImagePromptResultDTO;

public record AiTaskImagePromptResultPayloadDTO(
    TimelineImagePromptResultDTO result
) implements AiTaskResultPayloadDTO {
}
