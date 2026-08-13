package org.dromara.aivideo.timeline.dto;

import java.util.List;

public record TimelineSubtitleAlignmentResultDTO(
    String taskId,
    String sourceType,
    List<AlignedSubtitle> subtitles
) {
    public TimelineSubtitleAlignmentResultDTO {
        if (!"trusted_cue".equals(sourceType) && !"whisper".equals(sourceType)) {
            throw new IllegalArgumentException("sourceType must be trusted_cue or whisper");
        }
    }

    public record AlignedSubtitle(
        int sourceStartOffset,
        int sourceEndOffset,
        String displayText,
        long startMs,
        long endMs
    ) {
    }
}
