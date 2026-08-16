package org.dromara.aivideo.user.agent.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.dromara.aivideo.user.common.domain.bo.StrictAppRequestBo;

/** Creates one immutable NEW golden-path contract and its recoverable AgentRun. */
@Data
public class CreateAgentRunBo extends StrictAppRequestBo {

    @NotBlank
    @Pattern(regexp = "new")
    private String startAt;

    @NotBlank
    @Size(max = 1_000)
    private String scriptText;

    @NotBlank
    @Pattern(regexp = "[1-9][0-9]{0,18}")
    private String referenceVoiceId;

    @NotBlank
    @Pattern(regexp = "[1-9][0-9]{0,18}")
    private String portraitId;

    @NotBlank
    @Size(max = 128)
    private String projectTitle;

    @NotBlank
    @Pattern(regexp = "[A-Za-z0-9._:-]{1,48}")
    private String idempotencyKey;
}
