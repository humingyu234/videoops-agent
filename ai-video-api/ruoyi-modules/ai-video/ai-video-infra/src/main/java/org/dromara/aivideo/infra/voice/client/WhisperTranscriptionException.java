package org.dromara.aivideo.infra.voice.client;

import lombok.Getter;

@Getter
public class WhisperTranscriptionException extends RuntimeException {
    private final String failureCode;
    private final boolean retryable;

    public WhisperTranscriptionException(String failureCode, String message, boolean retryable) {
        super(message);
        this.failureCode = failureCode;
        this.retryable = retryable;
    }
}
