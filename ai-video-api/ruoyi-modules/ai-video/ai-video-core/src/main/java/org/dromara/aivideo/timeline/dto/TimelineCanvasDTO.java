package org.dromara.aivideo.timeline.dto;

import tools.jackson.databind.annotation.JsonSerialize;

import java.math.BigDecimal;

public record TimelineCanvasDTO(
    int width,
    int height,
    int frameRate,
    long durationMs,
    @JsonSerialize(using = TimelineDecimalSerializer.class) BigDecimal safeMarginRatio
) {
}
