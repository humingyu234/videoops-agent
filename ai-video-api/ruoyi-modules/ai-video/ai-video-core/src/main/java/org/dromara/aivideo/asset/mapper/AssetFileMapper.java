package org.dromara.aivideo.asset.mapper;

import org.apache.ibatis.annotations.Param;
import org.dromara.aivideo.asset.domain.AssetFile;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

import java.time.LocalDateTime;
import java.util.List;

/** 私有文件资产 Mapper。 */
public interface AssetFileMapper extends BaseMapperPlus<AssetFile, AssetFile> {
    AssetFile selectOwnedPortraitAssetForUpdate(@Param("assetId") Long assetId,
                                                 @Param("tenantId") Long tenantId,
                                                 @Param("workspaceId") String workspaceId,
                                                 @Param("ownerId") Long ownerId);

    List<AssetFile> selectUnboundPortraitAssetsBefore(@Param("cutoff") LocalDateTime cutoff,
                                                       @Param("limit") int limit);

    int reserveUnboundPortraitAsset(@Param("assetId") Long assetId,
                                    @Param("cutoff") LocalDateTime cutoff);

    int logicalDeleteUnboundPortraitAsset(@Param("assetId") Long assetId);

    int markUnboundPortraitAssetDeleteFailed(@Param("assetId") Long assetId,
                                              @Param("failureReason") String failureReason);
}
