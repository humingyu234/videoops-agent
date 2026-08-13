package org.dromara.aivideo.task.dto;

import org.dromara.aivideo.timeline.dto.TimelineSubtitleAlignmentCommandDTO;

public record AiTaskSubtitleAlignmentPayloadDTO(
    TimelineSubtitleAlignmentCommandDTO command
) implements AiTaskRequestPayloadDTO {
}
