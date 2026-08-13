package org.dromara.aivideo.user.voice.domain.bo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;

@JsonIgnoreProperties(ignoreUnknown = false)
public record RetryVoiceTranscriptionBo(@NotBlank String expectedRevision) {
}
