package org.dromara.aivideo.creation.dto;

import org.dromara.aivideo.creation.enums.CreationAssetStatus;
import org.dromara.aivideo.creation.enums.CreationAssetType;
import org.dromara.aivideo.creation.enums.CreationAssetUsageOrigin;

import java.time.Instant;

public record CreationAssetDTO(
    String assetId,
    String originalName,
    String mimeType,
    String sha256,
    CreationAssetType assetType,
    CreationAssetUsageOrigin usageOrigin,
    CreationAssetStatus status,
    long sizeBytes,
    Long durationMs,
    Integer width,
    Integer height,
    boolean hasVideoStream,
    boolean hasAudioStream,
    Instant createdAt
) {
}
