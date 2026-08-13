package org.dromara.aivideo.voice.dto;

public record WhisperTranscriptionInputDTO(
    String requestId,
    String originalName,
    String contentType,
    long fileSize
) {
}
