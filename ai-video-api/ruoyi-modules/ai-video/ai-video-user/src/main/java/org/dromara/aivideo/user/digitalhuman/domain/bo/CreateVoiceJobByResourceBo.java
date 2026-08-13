package org.dromara.aivideo.user.digitalhuman.domain.bo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@JsonIgnoreProperties(ignoreUnknown = false)
public record CreateVoiceJobByResourceBo(
    @NotBlank @Size(max = 1000) String scriptText,
    @NotBlank String referenceVoiceId
) {
}
