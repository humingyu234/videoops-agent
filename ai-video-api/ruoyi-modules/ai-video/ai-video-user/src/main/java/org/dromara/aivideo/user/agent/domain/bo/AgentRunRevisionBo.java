package org.dromara.aivideo.user.agent.domain.bo;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;
import org.dromara.aivideo.user.common.domain.bo.StrictAppRequestBo;

/** Optimistic identity for an AgentRun mutation; worker and lease facts are server-owned. */
@Data
public class AgentRunRevisionBo extends StrictAppRequestBo {

    @NotNull
    @PositiveOrZero
    private Long rowVersion;

    @NotNull
    @Positive
    private Long contractRevision;
}
