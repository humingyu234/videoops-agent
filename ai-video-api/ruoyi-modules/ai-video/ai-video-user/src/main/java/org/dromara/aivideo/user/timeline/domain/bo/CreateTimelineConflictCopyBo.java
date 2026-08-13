package org.dromara.aivideo.user.timeline.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.dromara.aivideo.user.common.domain.bo.StrictAppRequestBo;
import tools.jackson.databind.JsonNode;

@Data
public class CreateTimelineConflictCopyBo extends StrictAppRequestBo {

    @NotBlank
    @Pattern(regexp = "[A-Za-z0-9._:-]{1,64}")
    private String idempotencyKey;

    @NotBlank
    @Pattern(regexp = "[1-9][0-9]{0,18}")
    private String baseRevision;

    @NotBlank
    @Size(max = 32)
    private String schemaVersion;

    @NotNull
    private JsonNode timeline;
}
