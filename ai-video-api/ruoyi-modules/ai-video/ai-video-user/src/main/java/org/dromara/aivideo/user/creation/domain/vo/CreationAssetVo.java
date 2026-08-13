package org.dromara.aivideo.user.creation.domain.vo;

import org.dromara.aivideo.creation.dto.CreationAssetDTO;

import java.time.Instant;

public record CreationAssetVo(
    String assetId,
    String originalName,
    String mimeType,
    String sha256,
    String assetType,
    String usageOrigin,
    String status,
    long sizeBytes,
    Long durationMs,
    Integer width,
    Integer height,
    boolean hasVideoStream,
    boolean hasAudioStream,
    Instant createdAt
) {
    public static CreationAssetVo from(CreationAssetDTO dto) {
        return new CreationAssetVo(dto.assetId(), dto.originalName(), dto.mimeType(), dto.sha256(),
            dto.assetType().value(), dto.usageOrigin().value(), dto.status().value(), dto.sizeBytes(),
            dto.durationMs(), dto.width(), dto.height(), dto.hasVideoStream(), dto.hasAudioStream(), dto.createdAt());
    }
}
