package org.dromara.aivideo.digitalhuman.dto;

public record VoiceSynthesisRequestDTO(
    String text,
    String referenceAudioName,
    String referenceAudioType,
    byte[] referenceAudio
) {
}
