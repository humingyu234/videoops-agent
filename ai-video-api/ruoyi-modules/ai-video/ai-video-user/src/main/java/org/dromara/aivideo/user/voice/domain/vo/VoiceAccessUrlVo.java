package org.dromara.aivideo.user.voice.domain.vo;

import java.time.LocalDateTime;

public record VoiceAccessUrlVo(String url, LocalDateTime expiresAt, String contentType, String fileName) {
}
