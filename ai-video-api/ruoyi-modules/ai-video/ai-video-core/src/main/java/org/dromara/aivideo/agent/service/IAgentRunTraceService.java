package org.dromara.aivideo.agent.service;

import org.dromara.aivideo.agent.dto.AgentRunTraceDTO;
import org.dromara.aivideo.identity.dto.AppPrincipalSnapshotDTO;

/** Read-only durable-fact trace for an owned AgentRun. */
public interface IAgentRunTraceService {

    AgentRunTraceDTO getOwnedTrace(AppPrincipalSnapshotDTO principal, long agentRunId);
}
