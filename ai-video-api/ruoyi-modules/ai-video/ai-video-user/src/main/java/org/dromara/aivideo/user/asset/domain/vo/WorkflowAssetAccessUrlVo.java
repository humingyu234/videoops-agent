package org.dromara.aivideo.user.asset.domain.vo;

import org.dromara.aivideo.asset.dto.AssetAccessUrlDTO;

import java.time.LocalDateTime;

/** Short-lived URL for an owner-scoped workflow output. */
public record WorkflowAssetAccessUrlVo(String url, LocalDateTime expiresAt, String contentType) {
    public static WorkflowAssetAccessUrlVo from(AssetAccessUrlDTO source) {
        return new WorkflowAssetAccessUrlVo(source.url(), source.expiresAt(), source.contentType());
    }
}
