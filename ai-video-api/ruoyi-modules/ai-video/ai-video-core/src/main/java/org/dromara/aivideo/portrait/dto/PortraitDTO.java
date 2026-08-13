package org.dromara.aivideo.portrait.dto;

import java.time.LocalDateTime;
import java.util.List;

/** 人物形象稳定服务响应。 */
public record PortraitDTO(String portraitId, String assetId, String name, String gender,
                          List<String> sceneTags, String note, String availabilityStatus,
                          String failureReason, String previewUrl, LocalDateTime previewExpiresAt,
                          String originalFileName, String contentType, String fileFormat,
                          Integer width, Integer height, Long fileSize,
                          String recordRevision, LocalDateTime createTime, LocalDateTime updateTime) {
}
