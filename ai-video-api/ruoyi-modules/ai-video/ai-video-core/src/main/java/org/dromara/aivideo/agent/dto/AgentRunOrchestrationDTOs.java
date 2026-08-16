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

    /** Human decision is fenced by the exact pending approval identity and revision. */
    public record ApprovalCommand(
        long agentRunId,
        long expectedRowVersion,
        long expectedContractRevision,
        long approvalId,
        long expectedApprovalRevision,
        String approvalType,
        boolean approved
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
        String safeMessage,
        Long pendingApprovalId,
        Long approvalRevision,
        String approvalType
    ) {

        public AdvanceResult(
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
            this(agentRunId, runStatus, outcome, waitingTaskSource, waitingTaskId, candidateAssetId,
                missingFields, errorCode, safeMessage, null, null, null);
        }
    }
}
