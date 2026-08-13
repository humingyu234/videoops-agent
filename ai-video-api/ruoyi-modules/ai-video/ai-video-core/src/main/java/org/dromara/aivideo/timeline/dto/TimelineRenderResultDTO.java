package org.dromara.aivideo.timeline.dto;

public record TimelineRenderResultDTO(
    String fileName,
    String contentType,
    String sha256,
    long fileSize,
    long durationMs,
    int width,
    int height,
    int frameRate
) {
}
