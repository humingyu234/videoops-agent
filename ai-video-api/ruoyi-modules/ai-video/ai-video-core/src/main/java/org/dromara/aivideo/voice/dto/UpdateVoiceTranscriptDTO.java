package org.dromara.aivideo.voice.dto;

public record UpdateVoiceTranscriptDTO(String voiceId, String transcriptText, String expectedRevision) {
}
