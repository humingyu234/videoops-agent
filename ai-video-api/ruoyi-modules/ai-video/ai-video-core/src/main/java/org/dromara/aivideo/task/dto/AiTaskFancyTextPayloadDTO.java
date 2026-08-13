package org.dromara.aivideo.task.dto;

import org.dromara.aivideo.timeline.dto.TimelineFancyTextSuggestionCommandDTO;

public record AiTaskFancyTextPayloadDTO(
    TimelineFancyTextSuggestionCommandDTO command
) implements AiTaskRequestPayloadDTO {
}
