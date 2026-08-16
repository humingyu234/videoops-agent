package org.dromara.aivideo.creation.dto;

import org.dromara.aivideo.creation.enums.CreationAssetType;
import org.dromara.aivideo.creation.enums.CreationAssetUsageOrigin;
import org.dromara.aivideo.timeline.enums.TimelineAssetUsageType;

public record CreationAssetResolveDTO(
    String assetId,
    String mimeType,
    String sha256,
    CreationAssetType assetType,
    TimelineAssetUsageType usageType,
    long sizeBytes,
    Long durationMs,
    Integer width,
    Integer height,
    boolean hasVideoStream,
    boolean hasAudioStream,
    CreationAssetUsageOrigin usageOrigin
) {

    public CreationAssetResolveDTO(String assetId,
                                   String mimeType,
                                   String sha256,
                                   CreationAssetType assetType,
                                   TimelineAssetUsageType usageType,
                                   long sizeBytes,
                                   Long durationMs,
                                   Integer width,
                                   Integer height,
                                   boolean hasVideoStream,
                                   boolean hasAudioStream) {
        this(assetId, mimeType, sha256, assetType, usageType, sizeBytes, durationMs, width, height,
            hasVideoStream, hasAudioStream, null);
    }
}
