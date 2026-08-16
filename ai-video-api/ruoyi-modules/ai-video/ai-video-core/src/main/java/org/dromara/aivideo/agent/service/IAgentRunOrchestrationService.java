package org.dromara.aivideo.agent.service;

import org.dromara.aivideo.agent.dto.AgentRunOrchestrationDTOs;
import org.dromara.aivideo.identity.dto.AppPrincipalSnapshotDTO;

/** Closed, owner-scoped execution entry point that composes AgentRun with the T3 tool whitelist. */
public interface IAgentRunOrchestrationService {

    AgentRunOrchestrationDTOs.PlanResult plan(AppPrincipalSnapshotDTO principal, long agentRunId);

    AgentRunOrchestrationDTOs.AdvanceResult advance(AppPrincipalSnapshotDTO principal,
                                                     AgentRunOrchestrationDTOs.AdvanceCommand command);

    AgentRunOrchestrationDTOs.AdvanceResult cancel(AppPrincipalSnapshotDTO principal,
                                                    AgentRunOrchestrationDTOs.CancelCommand command);

    AgentRunOrchestrationDTOs.AdvanceResult decideApproval(
        AppPrincipalSnapshotDTO principal, AgentRunOrchestrationDTOs.ApprovalCommand command);
}
