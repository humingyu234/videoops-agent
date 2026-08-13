package org.dromara.aivideo.timeline.dto;

import java.math.BigDecimal;

public record TimelineVisualTransformDTO(
    BigDecimal xRatio,
    BigDecimal yRatio,
    BigDecimal widthRatio,
    BigDecimal heightRatio,
    BigDecimal rotationDeg,
    BigDecimal opacity
) {
}
