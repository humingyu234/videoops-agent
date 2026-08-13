package org.dromara.aivideo.timeline.dto;

public record TimelineMediaProbeDTO(
    String assetId,
    String mediaType,
    String formatName,
    long durationMs,
    long fileSize,
    Integer width,
    Integer height,
    Integer frameRate,
    Integer sampleRate,
    Integer channels,
    boolean videoStream,
    boolean audioStream
) {
}
