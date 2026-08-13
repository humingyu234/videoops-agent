package org.dromara.aivideo.task.dto;

import org.dromara.aivideo.timeline.dto.TimelineSubtitleAlignmentResultDTO;

public record AiTaskSubtitleAlignmentResultPayloadDTO(
    TimelineSubtitleAlignmentResultDTO result
) implements AiTaskResultPayloadDTO {
}
