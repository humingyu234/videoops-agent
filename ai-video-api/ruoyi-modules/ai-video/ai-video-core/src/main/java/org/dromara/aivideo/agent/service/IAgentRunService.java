package org.dromara.aivideo.agent.service;

import org.dromara.aivideo.identity.dto.AppPrincipalSnapshotDTO;

import java.time.Instant;

/** Minimal T2 control-plane contract; planning and task execution are deliberately outside this interface. */
public interface IAgentRunService {

    DeliveryBriefVersionView appendDeliveryBrief(AppPrincipalSnapshotDTO principal,
                                                  AppendDeliveryBriefCommand command);

    AcceptanceProfileVersionView appendAcceptanceProfile(AppPrincipalSnapshotDTO principal,
                                                          AppendAcceptanceProfileCommand command);

    AgentRunView createRun(AppPrincipalSnapshotDTO principal, CreateAgentRunCommand command);

    AgentRunView getOwnedRun(AppPrincipalSnapshotDTO principal, long agentRunId);

    /** Claims queued/expired running work, or rotates the fence for the same expired waiting task. */
    AgentRunLease claim(AppPrincipalSnapshotDTO principal, ClaimAgentRunCommand command);

    WaitingReceipt waitForExternalTask(AppPrincipalSnapshotDTO principal,
                                       WaitForExternalTaskCommand command);

    boolean completeExternalTask(AppPrincipalSnapshotDTO principal,
                                 CompleteExternalTaskCommand command);

    boolean finishLease(AppPrincipalSnapshotDTO principal, FinishAgentRunCommand command);

    record AppendDeliveryBriefCommand(
        Long briefId,
        Long parentVersionId,
        String idempotencyKey,
        String briefJson
    ) {
    }

    record AppendAcceptanceProfileCommand(
        Long acceptanceProfileId,
        Long parentVersionId,
        long deliveryBriefVersionId,
        String idempotencyKey,
        String profileJson
    ) {
    }

    record CreateAgentRunCommand(
        long deliveryBriefVersionId,
        long acceptanceProfileVersionId,
        String idempotencyKey
    ) {
    }

    record ClaimAgentRunCommand(
        long agentRunId,
        long expectedRowVersion,
        long expectedContractRevision,
        String workerId,
        long leaseSeconds
    ) {
    }

    record LeaseProof(
        long agentRunId,
        long rowVersion,
        long contractRevision,
        long leaseGeneration,
        String leaseToken
    ) {
    }

    record WaitForExternalTaskCommand(
        LeaseProof lease,
        String taskSource,
        long taskId,
        Instant resumeAfter
    ) {
    }

    record CompleteExternalTaskCommand(
        LeaseProof lease,
        String taskSource,
        long taskId,
        long candidateAssetId,
        String resultSummaryJson
    ) {
    }

    record FinishAgentRunCommand(
        LeaseProof lease,
        String terminalStatus,
        Long candidateAssetId,
        String resultSummaryJson,
        String errorCode,
        String errorSummary
    ) {
    }

    record DeliveryBriefVersionView(
        long deliveryBriefVersionId,
        long briefId,
        long versionNo,
        Long parentVersionId,
        String schemaVersion,
        String deliveryType,
        String briefHash
    ) {
    }

    record AcceptanceProfileVersionView(
        long acceptanceProfileVersionId,
        long acceptanceProfileId,
        long deliveryBriefVersionId,
        long versionNo,
        Long parentVersionId,
        String schemaVersion,
        String policyVersion,
        String profileHash
    ) {
    }

    record AgentRunView(
        long agentRunId,
        long deliveryBriefVersionId,
        long acceptanceProfileVersionId,
        long contractRevision,
        String runStatus,
        long rowVersion,
        long leaseGeneration,
        String waitingTaskSource,
        Long waitingTaskId,
        Long candidateAssetId,
        Instant stateChangedAt
    ) {
    }

    /** Raw token exists only in this in-process lease receipt; persistence stores its SHA-256 digest. */
    record AgentRunLease(
        long agentRunId,
        long rowVersion,
        long contractRevision,
        long leaseGeneration,
        String leaseToken,
        Instant leaseExpiresAt,
        String waitingTaskSource,
        Long waitingTaskId
    ) {

        public LeaseProof proof() {
            return new LeaseProof(agentRunId, rowVersion, contractRevision, leaseGeneration, leaseToken);
        }
    }

    record WaitingReceipt(
        LeaseProof lease,
        String taskSource,
        long taskId,
        Instant resumeAfter
    ) {
    }
}
