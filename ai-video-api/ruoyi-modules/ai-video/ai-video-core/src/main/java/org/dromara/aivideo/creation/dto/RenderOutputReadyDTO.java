package org.dromara.aivideo.creation.dto;

public record RenderOutputReadyDTO(
    String assetId,
    String taskId,
    String mimeType,
    String sha256,
    long sizeBytes,
    long durationMs,
    int width,
    int height,
    int frameRate,
    boolean hasVideoStream,
    boolean hasAudioStream
) {
}
