package org.dromara.aivideo.task.dto;

import org.dromara.aivideo.timeline.dto.TimelineRenderCommandDTO;

public record AiTaskRenderPayloadDTO(
    TimelineRenderCommandDTO command
) implements AiTaskRequestPayloadDTO {
}
