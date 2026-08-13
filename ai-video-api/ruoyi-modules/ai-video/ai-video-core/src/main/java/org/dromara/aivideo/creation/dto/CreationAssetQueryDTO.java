package org.dromara.aivideo.creation.dto;

public record CreationAssetQueryDTO(
    String assetType,
    String usageIntent,
    String status,
    String keyword
) {
}
