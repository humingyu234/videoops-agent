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
    boolean audioStream,
    String videoCodec,
    String audioCodec
) {

    public TimelineMediaProbeDTO(String assetId,
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
                                 boolean audioStream) {
        this(assetId, mediaType, formatName, durationMs, fileSize, width, height, frameRate,
            sampleRate, channels, videoStream, audioStream, null, null);
    }
}
