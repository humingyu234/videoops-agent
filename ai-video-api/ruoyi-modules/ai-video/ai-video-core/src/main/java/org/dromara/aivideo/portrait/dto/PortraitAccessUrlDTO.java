package org.dromara.aivideo.portrait.dto;

import java.time.LocalDateTime;

/** 人物照片短期访问地址。 */
public record PortraitAccessUrlDTO(String url, LocalDateTime expiresAt, String contentType) {
}
