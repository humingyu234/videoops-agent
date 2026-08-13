package org.dromara.aivideo.timeline.dto;

import org.dromara.aivideo.timeline.enums.FancyTextTemplateCode;

import java.math.BigDecimal;
import java.util.List;

public record TimelineFancyTextSuggestionResultDTO(
    String taskId,
    List<Suggestion> suggestions
) {
    public TimelineFancyTextSuggestionResultDTO {
        if (suggestions != null && suggestions.size() > 20) {
            throw new IllegalArgumentException("suggestions must not contain more than 20 items");
        }
    }

    public record Suggestion(
        String sourceText,
        int sourceStartOffset,
        int sourceEndOffset,
        long startMs,
        long durationMs,
        FancyTextTemplateCode templateCode,
        BigDecimal xRatio,
        BigDecimal yRatio,
        String primaryColor,
        String accentColor,
        String reason
    ) {
    }
}
