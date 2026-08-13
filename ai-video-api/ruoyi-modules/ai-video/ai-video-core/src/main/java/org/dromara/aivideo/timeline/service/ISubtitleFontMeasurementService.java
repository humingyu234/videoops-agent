package org.dromara.aivideo.timeline.service;

import java.math.BigDecimal;
import java.util.List;

/** Measures only fonts registered by the frozen timeline font registry. */
public interface ISubtitleFontMeasurementService {

    SubtitleLayout fit(MeasureRequest request);

    record MeasureRequest(
        String requestId,
        String fontCode,
        String displayText,
        int outlineWidthPx,
        int canvasWidthPx,
        BigDecimal safeMarginRatio
    ) {
    }

    record SubtitleLayout(
        String fontCode,
        String fontVersion,
        String fontSha256,
        int fontSizePx,
        List<String> displaySegments
    ) {
        public SubtitleLayout {
            displaySegments = displaySegments == null ? List.of() : List.copyOf(displaySegments);
        }
    }
}
