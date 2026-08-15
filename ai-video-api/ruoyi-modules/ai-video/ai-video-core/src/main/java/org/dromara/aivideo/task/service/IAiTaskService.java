package org.dromara.aivideo.task.service;

import org.dromara.aivideo.task.dto.AiTaskDTO;
import org.dromara.aivideo.task.dto.AiTaskAccessScopeDTO;
import org.dromara.aivideo.task.dto.AiTaskActorDTO;
import org.dromara.aivideo.task.dto.AiTaskDispatchResultDTO;
import org.dromara.aivideo.task.dto.AiTaskLeaseDTO;
import org.dromara.aivideo.task.dto.AiTaskQueryDTO;
import org.dromara.aivideo.task.dto.AiTaskSummaryDTO;
import org.dromara.aivideo.task.dto.CreateFreeAiTaskDTO;
import org.dromara.aivideo.task.dto.CreateWorkflowAiTaskDTO;
import org.dromara.aivideo.task.dto.RetryAiTaskDTO;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.mybatis.core.page.PageQuery;

import java.time.Instant;
import java.util.Optional;

public interface IAiTaskService {
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
    AiTaskDispatchResultDTO dispatchNext(
        String workerId, int perUserConcurrencyLimit, int systemConcurrencyLimit);
    AiTaskLeaseDTO claimNextWorkflow(String workerId, int concurrencyLimit);
    AiTaskDispatchResultDTO dispatchClaimedWorkflow(AiTaskLeaseDTO lease);
    boolean releaseClaimedWorkflow(AiTaskLeaseDTO lease);
    int recoverExpired(Instant now, int limit);
    int compensatePendingOutputs(Instant now, int limit);
}
