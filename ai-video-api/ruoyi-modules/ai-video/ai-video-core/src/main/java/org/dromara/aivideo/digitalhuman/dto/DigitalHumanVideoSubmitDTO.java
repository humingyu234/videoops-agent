package org.dromara.aivideo.digitalhuman.dto;

public record DigitalHumanVideoSubmitDTO(
    String portraitName,
    String portraitType,
    byte[] portrait,
    String audioName,
    String audioType,
    byte[] audio
) {
}
