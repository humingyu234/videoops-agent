package org.dromara.aivideo.creation.dto;

public record RegisterPendingRenderOutputDTO(
    String taskId,
    String inputVersionId,
    String outputConfigDigest,
    String idempotencyKey
) {
}
