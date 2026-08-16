package org.dromara.aivideo.user.agent.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;
import org.dromara.aivideo.user.common.domain.bo.StrictAppRequestBo;

/** Exact pending-approval decision fenced by run and approval revisions. */
@Data
public class AgentApprovalDecisionBo extends StrictAppRequestBo {

    @NotNull
    @PositiveOrZero
    private Long rowVersion;

    @NotNull
    @Positive
    private Long contractRevision;

    @NotNull
    @Positive
    private Long approvalRevision;

    @NotBlank
    @Pattern(regexp = "initial|conditional|final")
    private String type;

    @NotNull
    private Boolean approved;
}
