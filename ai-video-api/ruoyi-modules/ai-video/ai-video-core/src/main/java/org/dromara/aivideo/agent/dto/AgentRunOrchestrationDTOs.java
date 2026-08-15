package org.dromara.aivideo.agent.dto;

import java.util.List;

/**
 * T4 受限黄金链编排契约。
 */
public final class AgentRunOrchestrationDTOs {

    private AgentRunOrchestrationDTOs() {
    }

    public record PlanStep(
        int sequence,
        String stepType,
        String toolName,
        String disposition,
        String reason
    ) {
    }

    public record PlanResult(
        long agentRunId,
        String startAt,
        List<PlanStep> steps,
        List<String> missingFields,
        List<String> requiredPermissions,
        int requiredProviderSubmissions,
        boolean executable
    ) {
    }

    /** Caller supplies only the optimistic run identity; lease material never crosses this boundary. */
    public record AdvanceCommand(
        long agentRunId,
        long expectedRowVersion,
        long expectedContractRevision,
        String workerId
    ) {
    }

    public record CancelCommand(
        long agentRunId,
        long expectedRowVersion,
        long expectedContractRevision
    ) {
    }

    public record AdvanceResult(
        long agentRunId,
        String runStatus,
        String outcome,
        String waitingTaskSource,
        Long waitingTaskId,
        Long candidateAssetId,
        List<String> missingFields,
        String errorCode,
        String safeMessage
    ) {
    }
}
