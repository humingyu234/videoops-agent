package org.dromara.aivideo.timeline.dto;

import java.util.List;

public record TimelineSubtitleAlignmentCommandDTO(
    String taskId,
    String projectId,
    String draftRevision,
    String primaryAudioAssetId,
    String scriptTextSnapshot,
    String language,
    List<TrustedCue> trustedCues
) {
    public record TrustedCue(
        String text,
        long startMs,
        long endMs
    ) {
    }
}
