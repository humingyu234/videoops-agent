package org.dromara.aivideo.user.digitalhuman.domain.bo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

@JsonIgnoreProperties(ignoreUnknown = false)
public record CreateVideoJobByResourceBo(
    @Positive Long voiceJobId,
    @NotBlank String portraitId
) {
}
