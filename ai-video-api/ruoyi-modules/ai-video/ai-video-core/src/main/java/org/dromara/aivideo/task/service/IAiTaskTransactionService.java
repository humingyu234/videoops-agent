package org.dromara.aivideo.task.service;

import org.dromara.aivideo.task.dto.AiTaskCompletionDTO;
import org.dromara.aivideo.task.dto.AiTaskAccessScopeDTO;
import org.dromara.aivideo.task.dto.AiTaskActorDTO;
import org.dromara.aivideo.task.dto.AiTaskDTO;
import org.dromara.aivideo.task.dto.AiTaskLeaseDTO;
import org.dromara.aivideo.task.dto.AiTaskProgressDTO;
import org.dromara.aivideo.task.dto.AiTaskQueryDTO;
import org.dromara.aivideo.task.dto.AiTaskRequestPayloadDTO;
import org.dromara.aivideo.task.dto.AiTaskSummaryDTO;
import org.dromara.aivideo.task.dto.CreateFreeAiTaskDTO;
import org.dromara.aivideo.task.dto.CreateWorkflowAiTaskDTO;
import org.dromara.aivideo.task.dto.RetryAiTaskDTO;
import org.dromara.aivideo.task.enums.AiTaskType;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.mybatis.core.page.PageQuery;

import java.time.Instant;
import java.util.Optional;

/** Short, owner-audited database transactions for the durable task state machine. */
public interface IAiTaskTransactionService {

    AiTaskDTO createFreeTask(long actorId, CreateFreeAiTaskDTO command);

    Optional<AiTaskDTO> replayTimelineRender(long actorId, String projectId, String draftRevision,
                                             String idempotencyKey, String requestDigest);

    AiTaskDTO createWorkflowTask(AiTaskActorDTO actor, CreateWorkflowAiTaskDTO command);

    AiTaskDTO getOwned(long actorId, String taskId);

    AiTaskDTO getOwned(AiTaskAccessScopeDTO scope, String taskId);

    PageResult<AiTaskSummaryDTO> pageOwned(long actorId, AiTaskQueryDTO query, PageQuery pageQuery);

    PageResult<AiTaskSummaryDTO> pageOwned(AiTaskAccessScopeDTO scope, AiTaskQueryDTO query, PageQuery pageQuery);

    AiTaskDTO requestCancellation(long actorId, String taskId, String cancellationKey);

    AiTaskDTO requestCancellation(AiTaskAccessScopeDTO scope, String taskId, String cancellationKey);

    AiTaskDTO retryOwned(long actorId, RetryAiTaskDTO command);

    AiTaskLeaseDTO claimNext(String workerId, int perUserConcurrencyLimit, int systemConcurrencyLimit);

    AiTaskLeaseDTO claimNextWorkflow(String workerId, int concurrencyLimit);

    boolean releaseClaimedWorkflow(AiTaskLeaseDTO lease);

    AiTaskLeaseDTO beginAttempt(AiTaskLeaseDTO lease, Instant now);

    DispatchContext loadDispatchContext(AiTaskLeaseDTO lease);

    AiTaskLeaseDTO renew(AiTaskLeaseDTO lease, Instant now);

    AiTaskLeaseDTO reportProgress(AiTaskLeaseDTO lease, AiTaskProgressDTO progress, Instant now);

    boolean complete(AiTaskLeaseDTO lease, AiTaskCompletionDTO completion, Instant now);

    boolean cancel(AiTaskLeaseDTO lease, String safeMessage, Instant now);

    boolean cancellationRequested(AiTaskLeaseDTO lease);

    int recoverExpired(Instant now, int limit);

    record DispatchContext(AiTaskActorDTO actor, AiTaskType taskType, AiTaskRequestPayloadDTO payload,
                           String outputConfigDigest) {
        public DispatchContext(long ownerUserId, AiTaskType taskType, AiTaskRequestPayloadDTO payload,
                               String outputConfigDigest) {
            this(new AiTaskActorDTO("app_user", ownerUserId, ownerUserId), taskType, payload, outputConfigDigest);
        }

        public Long ownerUserId() {
            return actor.ownerUserId();
        }
    }
}
