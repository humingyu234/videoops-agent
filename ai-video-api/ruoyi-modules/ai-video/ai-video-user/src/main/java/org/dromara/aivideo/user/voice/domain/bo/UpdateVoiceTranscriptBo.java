package org.dromara.aivideo.user.voice.domain.bo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@JsonIgnoreProperties(ignoreUnknown = false)
public record UpdateVoiceTranscriptBo(
    @NotBlank @Size(max = 20000) String transcriptText,
    @NotBlank String expectedRevision
) {
}
