package org.dromara.aivideo.asset.dto;

import java.time.LocalDateTime;

/** 私有素材短期访问地址。 */
public record AssetAccessUrlDTO(String url, LocalDateTime expiresAt, String contentType) {
}
