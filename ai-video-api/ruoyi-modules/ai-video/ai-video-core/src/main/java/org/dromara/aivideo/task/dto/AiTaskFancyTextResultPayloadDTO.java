package org.dromara.aivideo.task.dto;

import org.dromara.aivideo.timeline.dto.TimelineFancyTextSuggestionResultDTO;

public record AiTaskFancyTextResultPayloadDTO(
    TimelineFancyTextSuggestionResultDTO result
) implements AiTaskResultPayloadDTO {
}
