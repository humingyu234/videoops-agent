package org.dromara.aivideo.user.agent.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.dromara.aivideo.user.common.domain.bo.StrictAppRequestBo;

/** Creates one immutable golden-path contract and its recoverable AgentRun. */
@Data
public class CreateAgentRunBo extends StrictAppRequestBo {

    @NotBlank
    @Pattern(regexp = "new|voice_job|video_job|project|render_task")
    private String startAt;

    @Size(max = 1_000)
    private String scriptText;

    @Pattern(regexp = "[1-9][0-9]{0,18}")
    private String referenceVoiceId;

    @Pattern(regexp = "[1-9][0-9]{0,18}")
    private String portraitId;

    @Size(max = 128)
    private String projectTitle;

    @Pattern(regexp = "[1-9][0-9]{0,18}")
    private String voiceJobId;

    @Pattern(regexp = "[1-9][0-9]{0,18}")
    private String videoJobId;

    @Pattern(regexp = "[1-9][0-9]{0,18}")
    private String projectId;

    @Pattern(regexp = "[1-9][0-9]{0,18}")
    private String expectedRevision;

    @Pattern(regexp = "[1-9][0-9]{0,18}")
    private String taskId;

    @NotBlank
    @Pattern(regexp = "[A-Za-z0-9._:-]{1,48}")
    private String idempotencyKey;
}
