package org.dromara.aivideo.timeline.dto;

import org.dromara.aivideo.timeline.enums.TimelineElementType;
import org.dromara.aivideo.timeline.enums.TimelineVisualEffectCode;

import java.math.BigDecimal;

public record TimelineVisualEffectElementDTO(
    String elementId,
    TimelineElementType elementType,
    long startMs,
    long endMs,
    int zIndex,
    boolean enabled,
    boolean locked,
    String label,
    TimelineVisualEffectCode effectCode,
    long durationMs,
    BigDecimal scale,
    BigDecimal radius
) implements TimelineElementDTO {
}
