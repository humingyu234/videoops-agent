package org.dromara.aivideo.infra.voice.client;

import java.util.List;

public record WhisperTranscriptionResponse(String requestId, String text, String language,
                                           Long durationMillis, List<WhisperWordResponse> words) {

    public record WhisperWordResponse(String text, Long startMillis, Long endMillis) {
    }
}
