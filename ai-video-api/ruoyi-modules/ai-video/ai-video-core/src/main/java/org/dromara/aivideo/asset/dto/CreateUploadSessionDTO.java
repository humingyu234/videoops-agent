package org.dromara.aivideo.asset.dto;

/** User supplied metadata for one workflow input upload session. */
public record CreateUploadSessionDTO(
    String templateId,
    String schemaHash,
    String inputKey,
    String fileName,
    String declaredContentType,
    Long sizeBytes,
    String idempotencyKey
) {
}
