package org.dromara.aivideo.user.workflow.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import org.dromara.aivideo.user.common.domain.bo.StrictAppRequestBo;
import tools.jackson.databind.JsonNode;

import java.util.Map;

/** Public discovery order payload; provider and execution configuration are deliberately absent. */
@Data
public class CreateWorkflowOrderBo extends StrictAppRequestBo {
    @NotBlank
    @Pattern(regexp = "[1-9][0-9]{0,18}")
    private String templateId;
    @NotBlank
    @Pattern(regexp = "sha256:[0-9a-f]{64}")
    private String schemaHash;
    @NotNull
    private Map<String, JsonNode> inputs;
}
