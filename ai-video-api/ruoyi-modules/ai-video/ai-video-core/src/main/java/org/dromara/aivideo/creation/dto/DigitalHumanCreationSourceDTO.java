package org.dromara.aivideo.creation.dto;

import org.dromara.aivideo.timeline.dto.TimelineSubtitleAlignmentCommandDTO;

import java.util.List;

public record DigitalHumanCreationSourceDTO(
    String sourceId,
    String baseVideoAssetId,
    String primaryAudioAssetId,
    String scriptTextSnapshot,
    long durationMs,
    int width,
    int height,
    int frameRate,
    List<TimelineSubtitleAlignmentCommandDTO.TrustedCue> trustedCues
) {
}
