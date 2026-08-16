package org.dromara.aivideo.timeline.dto;

import java.util.Objects;

public record TimelineMediaQualityInspectionDTO(
    TimelineMediaProbeDTO probe,
    boolean fullyDecoded
) {

    public TimelineMediaQualityInspectionDTO {
        Objects.requireNonNull(probe, "probe");
    }
}
