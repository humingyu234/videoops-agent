package org.dromara.aivideo.user.agent.service;

import org.dromara.aivideo.identity.dto.AppPrincipalSnapshotDTO;
import org.dromara.aivideo.user.agent.domain.bo.AgentApprovalDecisionBo;
import org.dromara.aivideo.user.agent.domain.bo.AgentRunRevisionBo;
import org.dromara.aivideo.user.agent.domain.bo.CreateAgentRunBo;
import org.dromara.aivideo.user.agent.domain.vo.AgentRunDetailVo;

/** HTTP-facing application boundary for the constrained VideoOps Agent product entry. */
public interface IAgentRunApplicationService {

    AgentRunDetailVo create(AppPrincipalSnapshotDTO principal, CreateAgentRunBo body);

    AgentRunDetailVo detail(AppPrincipalSnapshotDTO principal, String agentRunId);

    AgentRunDetailVo advance(AppPrincipalSnapshotDTO principal, String agentRunId, AgentRunRevisionBo body);

    AgentRunDetailVo cancel(AppPrincipalSnapshotDTO principal, String agentRunId, AgentRunRevisionBo body);

    AgentRunDetailVo decideApproval(AppPrincipalSnapshotDTO principal, String agentRunId, String approvalId,
                                    AgentApprovalDecisionBo body);
}
