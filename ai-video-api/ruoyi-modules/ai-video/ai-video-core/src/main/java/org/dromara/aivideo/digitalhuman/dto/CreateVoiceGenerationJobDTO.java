package org.dromara.aivideo.digitalhuman.dto;

public record CreateVoiceGenerationJobDTO(
    DigitalHumanOwnerDTO owner,
    String idempotencyKey,
    String scriptText,
    String referenceAudioName,
    String referenceAudioType,
    byte[] referenceAudio
) {
}
