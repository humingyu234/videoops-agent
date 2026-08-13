package org.dromara.aivideo.digitalhuman.dto;

public record DigitalHumanVideoPollDTO(
    DigitalHumanVideoProviderStatus status,
    Integer progress,
    byte[] video,
    String mediaType,
    String fileExtension,
    String failureCode
) {
}
