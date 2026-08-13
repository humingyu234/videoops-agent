package org.dromara.aivideo.task.dto;

import org.dromara.aivideo.timeline.dto.TimelineImagePromptCommandDTO;

public record AiTaskImagePromptPayloadDTO(
    TimelineImagePromptCommandDTO command
) implements AiTaskRequestPayloadDTO {
}
