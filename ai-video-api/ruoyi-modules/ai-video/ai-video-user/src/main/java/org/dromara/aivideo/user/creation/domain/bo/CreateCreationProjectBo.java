package org.dromara.aivideo.user.creation.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.dromara.aivideo.user.common.domain.bo.StrictAppRequestBo;

@Data
public class CreateCreationProjectBo extends StrictAppRequestBo {

    @NotBlank
    @Size(max = 32)
    private String sourceType;

    @NotBlank
    @Pattern(regexp = "[1-9][0-9]{0,18}")
    private String sourceId;

    @Size(max = 128)
    private String projectTitle;

    @NotBlank
    @Pattern(regexp = "[A-Za-z0-9._:-]{1,64}")
    private String idempotencyKey;
}
