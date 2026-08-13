package org.dromara.aivideo.user.creation.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.dromara.aivideo.user.common.domain.bo.StrictAppRequestBo;

@Data
public class UpdateCreationProjectBo extends StrictAppRequestBo {

    @NotBlank
    @Size(max = 128)
    private String projectTitle;
}
