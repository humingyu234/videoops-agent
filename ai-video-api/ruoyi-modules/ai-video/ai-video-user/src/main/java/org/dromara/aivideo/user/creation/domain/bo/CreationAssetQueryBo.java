package org.dromara.aivideo.user.creation.domain.bo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record CreationAssetQueryBo(
    @Size(max = 16) String assetType,
    @Size(max = 16) String status,
    @Min(1) Integer pageNum,
    @Min(1) @Max(100) Integer pageSize
) {
}
