package org.dromara.aivideo.asset.dto;

import java.time.LocalDateTime;

/** 私有文件资产服务契约。 */
public record AssetDTO(String assetId, String availabilityStatus, String failureReason,
                       String originalName, String contentType, String fileFormat,
                       Integer width, Integer height, Long fileSize, LocalDateTime createTime) {
}
