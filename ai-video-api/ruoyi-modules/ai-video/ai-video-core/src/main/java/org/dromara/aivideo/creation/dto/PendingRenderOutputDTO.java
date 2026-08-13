package org.dromara.aivideo.creation.dto;

import org.dromara.aivideo.creation.enums.CreationAssetStatus;

import java.time.Instant;

public record PendingRenderOutputDTO(
    String assetId,
    String taskId,
    String inputVersionId,
    String outputConfigDigest,
    CreationAssetStatus status,
    Instant createdAt
) {
}
