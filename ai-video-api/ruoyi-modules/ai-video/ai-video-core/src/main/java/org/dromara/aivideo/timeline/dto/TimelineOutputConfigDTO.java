package org.dromara.aivideo.timeline.dto;

import org.dromara.aivideo.timeline.enums.TimelineOutputQuality;

public record TimelineOutputConfigDTO(
    String resolutionPreset,
    int frameRate,
    TimelineOutputQuality qualityPreset
) {
    public TimelineOutputConfigDTO {
        if (!"match_canvas".equals(resolutionPreset)) {
            throw new IllegalArgumentException("resolutionPreset must be match_canvas");
        }
        if (frameRate != 30) {
            throw new IllegalArgumentException("frameRate must be 30");
        }
    }
}
