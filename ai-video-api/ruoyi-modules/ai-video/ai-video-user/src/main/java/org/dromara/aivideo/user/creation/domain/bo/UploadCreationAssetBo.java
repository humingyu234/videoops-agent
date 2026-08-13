package org.dromara.aivideo.user.creation.domain.bo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@JsonIgnoreProperties(ignoreUnknown = false)
public record UploadCreationAssetBo(
    @NotBlank @Size(max = 32) String usageIntent,
    @NotBlank @Size(max = 64) String idempotencyKey
) {
}
