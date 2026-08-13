package org.dromara.aivideo.user.portrait.domain.vo;

/** 私有人物照片短期访问地址。 */
import java.time.LocalDateTime;

public record PortraitAccessUrlVo(String url, LocalDateTime expiresAt, String contentType) {
}
