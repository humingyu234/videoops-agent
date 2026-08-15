package org.dromara.aivideo.agent.service;

import org.dromara.aivideo.agent.dto.AgentToolDTOs;
import org.dromara.aivideo.identity.dto.AppPrincipalSnapshotDTO;

/** Explicit, owner-scoped tool entry point for the T1 golden chain. */
public interface IAgentToolService {

    AgentToolDTOs.Result execute(AppPrincipalSnapshotDTO principal, AgentToolDTOs.Call call);
}
