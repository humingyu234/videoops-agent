package org.dromara.aivideo.timeline.dto;

import java.math.BigDecimal;

public record TimelineCropDTO(
    BigDecimal xRatio,
    BigDecimal yRatio,
    BigDecimal widthRatio,
    BigDecimal heightRatio
) {
}
