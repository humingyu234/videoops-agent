package org.dromara.aivideo.timeline.dto;

import java.util.List;

public record TimelineImagePromptResultDTO(
    String taskId,
    List<Suggestion> suggestions
) {
    public TimelineImagePromptResultDTO {
        if (suggestions != null && suggestions.size() > 20) {
            throw new IllegalArgumentException("suggestions must not contain more than 20 items");
        }
    }

    public record Suggestion(
        String prompt,
        String negativePrompt,
        List<String> styleTags,
        String reason
    ) {
    }
}
