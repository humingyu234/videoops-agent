package org.dromara.aivideo.timeline.dto;

public record TimelineImagePromptCommandDTO(
    String taskId,
    String projectId,
    String draftRevision,
    int sourceStartOffset,
    int sourceEndOffset,
    String sourceText,
    String contextBefore,
    String contextAfter,
    String canvasAspect,
    String styleCode
) {
}
