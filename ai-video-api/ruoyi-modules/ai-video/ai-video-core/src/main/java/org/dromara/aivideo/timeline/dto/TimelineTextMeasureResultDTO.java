package org.dromara.aivideo.timeline.dto;

public record TimelineTextMeasureResultDTO(
    String requestId,
    String fontCode,
    String fontVersion,
    String fontSha256,
    String fontRegistrySha256,
    int widthPx,
    int heightPx,
    boolean allCodePointsSupported
) {
}
