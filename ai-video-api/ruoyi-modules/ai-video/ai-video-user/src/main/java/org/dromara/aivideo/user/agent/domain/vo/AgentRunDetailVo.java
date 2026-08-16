package org.dromara.aivideo.user.agent.domain.vo;

import java.time.Instant;
import java.util.List;

/** Safe HTTP projection for one owner-scoped AgentRun and its durable execution facts. */
public record AgentRunDetailVo(
    RunVo run,
    PlanVo plan,
    TraceVo trace,
    ApprovalVo pendingApproval,
    String finalOutputAssetId,
    ActionVo action
) {

    public record RunVo(
        String runId,
        String status,
        long rowVersion,
        long contractRevision,
        String waitingTaskSource,
        String waitingTaskId,
        String candidateAssetId,
        long qualityRepairCount,
        String pendingApprovalId,
        long approvalRevision,
        Instant resumeAfter,
        Instant finishedAt,
        String errorCode,
        String safeMessage
    ) {
    }

    public record PlanVo(
        String startAt,
        List<PlanStepVo> steps,
        List<String> missingFields,
        int requiredProviderSubmissions,
        boolean executable
    ) {
    }

    public record PlanStepVo(
        int sequence,
        String stepType,
        String toolName,
        String disposition,
        String reason
    ) {
    }

    public record TraceVo(
        String completeness,
        List<TraceItemVo> items
    ) {
    }

    public record TraceItemVo(
        Instant occurredAt,
        String type,
        String status,
        String subjectType,
        String subjectId,
        String label,
        String errorCode,
        String safeMessage
    ) {
    }

    public record ApprovalVo(
        String approvalId,
        String type,
        String status,
        long revision,
        String requestSummary
    ) {
    }

    /** Immediate mutation outcome; GET responses leave this null. */
    public record ActionVo(
        String outcome,
        String errorCode,
        String safeMessage,
        List<String> missingFields
    ) {
    }
}
