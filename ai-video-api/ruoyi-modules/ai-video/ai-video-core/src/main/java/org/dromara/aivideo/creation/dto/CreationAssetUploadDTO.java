package org.dromara.aivideo.creation.dto;

public record CreationAssetUploadDTO(
    String originalName,
    String contentType,
    String usageIntent,
    String idempotencyKey,
    String requestDigest,
    long contentLength
) {
}
