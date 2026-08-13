package org.dromara.aivideo.timeline.dto;

import org.dromara.aivideo.timeline.enums.TimelineElementType;
import org.dromara.aivideo.timeline.enums.TimelineFitMode;

public record TimelineMainVideoElementDTO(
    String elementId,
    TimelineElementType elementType,
    long startMs,
    long endMs,
    int zIndex,
    boolean enabled,
    boolean locked,
    String label,
    String assetId,
    long sourceDurationMs,
    long sourceStartMs,
    TimelineFitMode fitMode
) implements TimelineElementDTO {
}
