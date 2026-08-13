package org.dromara.aivideo.voice.dto;

public record VoiceTranscriptionFailureDTO(String code, String message, boolean retryable) {
}
