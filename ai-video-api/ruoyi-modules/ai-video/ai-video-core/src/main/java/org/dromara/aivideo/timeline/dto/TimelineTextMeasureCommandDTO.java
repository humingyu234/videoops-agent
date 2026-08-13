package org.dromara.aivideo.timeline.dto;

import java.math.BigDecimal;

public record TimelineTextMeasureCommandDTO(
    String requestId,
    String fontCode,
    String text,
    int fontSizePx,
    int canvasWidthPx,
    int outlineWidthPx,
    BigDecimal safeMarginRatio
) {
}
