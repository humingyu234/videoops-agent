package org.dromara.aivideo.digitalhuman.dto;

public record CreateDigitalHumanVideoJobDTO(
    DigitalHumanOwnerDTO owner,
    String idempotencyKey,
    Long voiceJobId,
    String portraitName,
    String portraitType,
    byte[] portrait
) {
}
