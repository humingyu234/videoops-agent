package org.dromara.aivideo.timeline.dto;

import org.dromara.aivideo.timeline.enums.TimelineAssetUsageType;

import java.util.List;

public record TimelineAssetReferenceDTO(
    String assetId,
    TimelineAssetUsageType usageType,
    List<String> elementIds,
    String sha256,
    long fileSize
) {
}
