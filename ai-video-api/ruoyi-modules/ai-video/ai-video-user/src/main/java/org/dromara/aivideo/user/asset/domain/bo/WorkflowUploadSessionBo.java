package org.dromara.aivideo.user.asset.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.dromara.aivideo.user.common.domain.bo.StrictAppRequestBo;

/** Declares one private upload required by the visible template form. */
@Data
public class WorkflowUploadSessionBo extends StrictAppRequestBo {

    @NotBlank
    private String purpose;

    @NotBlank
    @Size(max = 32)
    private String templateId;

    @NotBlank
    @Size(max = 71)
    private String schemaHash;

    @NotBlank
    @Size(max = 48)
    private String inputKey;

    @NotBlank
    @Size(max = 255)
    private String fileName;

    @NotBlank
    @Size(max = 128)
    private String declaredContentType;

    @NotNull
    @Positive
    private Long sizeBytes;

    @NotBlank
    @Size(max = 128)
    private String idempotencyKey;
}
