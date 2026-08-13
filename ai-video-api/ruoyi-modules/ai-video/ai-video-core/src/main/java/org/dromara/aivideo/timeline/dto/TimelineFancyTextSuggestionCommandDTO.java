package org.dromara.aivideo.timeline.dto;

import org.dromara.aivideo.timeline.enums.FancyTextTemplateCode;

import java.util.List;

public record TimelineFancyTextSuggestionCommandDTO(
    String taskId,
    String projectId,
    String draftRevision,
    int sourceStartOffset,
    int sourceEndOffset,
    String sourceText,
    String contextBefore,
    String contextAfter,
    List<FancyTextTemplateCode> allowedTemplates
) {
}
