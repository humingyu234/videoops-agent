package org.dromara.aivideo.task.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.dromara.aivideo.creation.domain.CreationProject;
import org.dromara.aivideo.creation.mapper.CreationProjectMapper;
import org.dromara.aivideo.task.domain.AiTask;
import org.dromara.aivideo.task.domain.AiTaskAttempt;
import org.dromara.aivideo.task.domain.AiTaskExecution;
import org.dromara.aivideo.task.dto.AiTaskCompletionDTO;
import org.dromara.aivideo.task.dto.AiTaskAccessScopeDTO;
import org.dromara.aivideo.task.dto.AiTaskActorDTO;
import org.dromara.aivideo.task.dto.AiTaskDTO;
import org.dromara.aivideo.task.dto.AiTaskFancyTextPayloadDTO;
import org.dromara.aivideo.task.dto.AiTaskFancyTextResultPayloadDTO;
import org.dromara.aivideo.task.dto.AiTaskImagePromptPayloadDTO;
import org.dromara.aivideo.task.dto.AiTaskImagePromptResultPayloadDTO;
import org.dromara.aivideo.task.dto.AiTaskLeaseDTO;
import org.dromara.aivideo.task.dto.AiTaskProgressDTO;
import org.dromara.aivideo.task.dto.AiTaskQueryDTO;
import org.dromara.aivideo.task.dto.AiTaskRenderPayloadDTO;
import org.dromara.aivideo.task.dto.AiTaskRequestPayloadDTO;
import org.dromara.aivideo.task.dto.AiTaskResultPayloadDTO;
import org.dromara.aivideo.task.dto.AiTaskSubtitleAlignmentPayloadDTO;
import org.dromara.aivideo.task.dto.AiTaskSubtitleAlignmentResultPayloadDTO;
import org.dromara.aivideo.task.dto.WorkflowAiTaskPayloadDTO;
import org.dromara.aivideo.task.dto.WorkflowAiTaskResultPayloadDTO;
import org.dromara.aivideo.task.dto.AiTaskSummaryDTO;
import org.dromara.aivideo.task.dto.CreateFreeAiTaskDTO;
import org.dromara.aivideo.task.dto.CreateWorkflowAiTaskDTO;
import org.dromara.aivideo.task.dto.RetryAiTaskDTO;
import org.dromara.aivideo.task.enums.AiTaskAttemptStatus;
import org.dromara.aivideo.task.enums.AiTaskExecutionStatus;
import org.dromara.aivideo.task.enums.AiTaskResourceType;
import org.dromara.aivideo.task.enums.AiTaskStage;
import org.dromara.aivideo.task.enums.AiTaskStatus;
import org.dromara.aivideo.task.enums.AiTaskType;
import org.dromara.aivideo.task.mapper.AiTaskAttemptMapper;
import org.dromara.aivideo.task.mapper.AiTaskExecutionMapper;
import org.dromara.aivideo.task.mapper.AiTaskMapper;
import org.dromara.aivideo.task.service.IAiTaskTransactionService;
import org.dromara.aivideo.task.service.IFreeAiTaskQuotaPolicyService;
import org.dromara.aivideo.timeline.constant.TimelineContractLimits;
import org.dromara.aivideo.timeline.constant.TimelineErrorCodes;
import org.dromara.aivideo.timeline.domain.TimelineAssetRef;
import org.dromara.aivideo.timeline.domain.TimelineDraft;
import org.dromara.aivideo.timeline.domain.TimelineVersion;
import org.dromara.aivideo.timeline.dto.TimelineDocumentDTO;
import org.dromara.aivideo.timeline.dto.TimelineFancyTextSuggestionCommandDTO;
import org.dromara.aivideo.timeline.dto.TimelineImagePromptCommandDTO;
import org.dromara.aivideo.timeline.dto.TimelineRenderCommandDTO;
import org.dromara.aivideo.timeline.dto.TimelineSubtitleAlignmentCommandDTO;
import org.dromara.aivideo.timeline.mapper.TimelineAssetRefMapper;
import org.dromara.aivideo.timeline.mapper.TimelineDraftMapper;
import org.dromara.aivideo.timeline.mapper.TimelineVersionMapper;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.mybatis.audit.AuditFillContext;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Durable, owner-scoped task facts. External AI and media work is intentionally kept out of this class.
 */
@Service
public class AiTaskTransactionServiceImpl implements IAiTaskTransactionService {

    private static final String APP_USER = "app_user";
    private static final String DRAFT = "draft";
    private static final String VERSION = "version";
    private static final String RENDER_INPUT = "render_input";
    private static final String EDITING = "editing";
    private static final String READY = "ready";
    private static final String RENDERING = "rendering";
    private static final String ARCHIVED = "archived";
    private static final Pattern POSITIVE_ID = Pattern.compile("[1-9][0-9]{0,18}");
    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("[A-Za-z0-9._:-]{1,64}");
    private static final Pattern LOWER_HEX = Pattern.compile("[0-9a-f]{64}");
    private static final int SAFE_MESSAGE_MAX_CODE_POINTS = 200;
    private static final int LEASE_SECONDS = 60;
    private static final int CLAIM_SCAN_LIMIT = 16;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AiTaskMapper taskMapper;
    private final AiTaskExecutionMapper executionMapper;
    private final AiTaskAttemptMapper attemptMapper;
    private final CreationProjectMapper projectMapper;
    private final TimelineDraftMapper draftMapper;
    private final TimelineVersionMapper versionMapper;
    private final TimelineAssetRefMapper assetRefMapper;
    private final JsonMapper jsonMapper;
    private final IFreeAiTaskQuotaPolicyService quotaPolicyService;
    private final TransactionTemplate transactionTemplate;

    AiTaskTransactionServiceImpl(AiTaskMapper taskMapper, AiTaskExecutionMapper executionMapper,
                                 AiTaskAttemptMapper attemptMapper, CreationProjectMapper projectMapper,
                                 TimelineDraftMapper draftMapper, TimelineVersionMapper versionMapper,
                                 TimelineAssetRefMapper assetRefMapper, JsonMapper jsonMapper,
                                 IFreeAiTaskQuotaPolicyService quotaPolicyService) {
        this(taskMapper, executionMapper, attemptMapper, projectMapper, draftMapper, versionMapper, assetRefMapper,
            jsonMapper, quotaPolicyService, (TransactionTemplate) null);
    }

    @Autowired
    public AiTaskTransactionServiceImpl(AiTaskMapper taskMapper, AiTaskExecutionMapper executionMapper,
                                        AiTaskAttemptMapper attemptMapper, CreationProjectMapper projectMapper,
                                        TimelineDraftMapper draftMapper, TimelineVersionMapper versionMapper,
                                        TimelineAssetRefMapper assetRefMapper, JsonMapper jsonMapper,
                                        IFreeAiTaskQuotaPolicyService quotaPolicyService,
                                        PlatformTransactionManager transactionManager) {
        this(taskMapper, executionMapper, attemptMapper, projectMapper, draftMapper, versionMapper, assetRefMapper,
            jsonMapper, quotaPolicyService, new TransactionTemplate(transactionManager));
    }

    private AiTaskTransactionServiceImpl(AiTaskMapper taskMapper, AiTaskExecutionMapper executionMapper,
                                         AiTaskAttemptMapper attemptMapper, CreationProjectMapper projectMapper,
                                         TimelineDraftMapper draftMapper, TimelineVersionMapper versionMapper,
                                         TimelineAssetRefMapper assetRefMapper, JsonMapper jsonMapper,
                                         IFreeAiTaskQuotaPolicyService quotaPolicyService,
                                         TransactionTemplate transactionTemplate) {
        this.taskMapper = Objects.requireNonNull(taskMapper, "taskMapper");
        this.executionMapper = Objects.requireNonNull(executionMapper, "executionMapper");
        this.attemptMapper = Objects.requireNonNull(attemptMapper, "attemptMapper");
        this.projectMapper = Objects.requireNonNull(projectMapper, "projectMapper");
        this.draftMapper = Objects.requireNonNull(draftMapper, "draftMapper");
        this.versionMapper = Objects.requireNonNull(versionMapper, "versionMapper");
        this.assetRefMapper = Objects.requireNonNull(assetRefMapper, "assetRefMapper");
        this.jsonMapper = Objects.requireNonNull(jsonMapper, "jsonMapper");
        this.quotaPolicyService = Objects.requireNonNull(quotaPolicyService, "quotaPolicyService");
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public AiTaskDTO createFreeTask(long actorId, CreateFreeAiTaskDTO command) {
        CreateSpec spec = validateCreate(actorId, command);
        AiTask existing = findByIdempotency(actorId, spec.idempotencyKey());
        if (existing != null) {
            return replayOrConflict(existing, spec.requestDigest());
        }
        try {
            return inTransaction(actorId, () -> createInTransaction(actorId, spec));
        } catch (DuplicateKeyException exception) {
            AiTask winner = findByIdempotency(actorId, spec.idempotencyKey());
            if (winner != null) {
                return replayOrConflict(winner, spec.requestDigest());
            }
            throw taskInvalid("任务创建冲突");
        }
    }

    @Override
    public Optional<AiTaskDTO> replayTimelineRender(long actorId, String projectId, String draftRevision,
                                                    String idempotencyKey, String requestDigest) {
        if (actorId <= 0 || !POSITIVE_ID.matcher(nullToEmpty(projectId)).matches()
            || !POSITIVE_ID.matcher(nullToEmpty(draftRevision)).matches()
            || !IDEMPOTENCY_KEY.matcher(nullToEmpty(idempotencyKey)).matches()
            || !LOWER_HEX.matcher(nullToEmpty(requestDigest)).matches()) {
            throw taskInvalid("渲染任务幂等回放请求无效");
        }
        long requestedProjectId = parsePositiveId(projectId, "创作项目不存在");
        AiTask existing = findByIdempotency(actorId, idempotencyKey);
        if (existing == null) {
            return Optional.empty();
        }
        if (!AiTaskType.TIMELINE_RENDER.value().equals(existing.getTaskType())
            || !AiTaskResourceType.CREATION_PROJECT.value().equals(existing.getResourceType())
            || !Objects.equals(existing.getResourceId(), requestedProjectId)
            || !Objects.equals(existing.getRequestDigest(), requestDigest)) {
            throw idempotencyConflict();
        }
        AiTaskDTO replay = toDto(existing, true);
        if (!Objects.equals(replay.draftRevision(), draftRevision)) {
            throw idempotencyConflict();
        }
        return Optional.of(replay);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public AiTaskDTO createWorkflowTask(AiTaskActorDTO actor, CreateWorkflowAiTaskDTO command) {
        if (actor == null || command == null) {
            throw taskInvalid("工作流任务创建请求无效");
        }
        IFreeAiTaskQuotaPolicyService.FrozenQuota quota = quotaPolicyService.freeze(command.taskType(),
            FreeAiTaskQuotaPolicyServiceImpl.WORKFLOW_POLICY_VERSION, 0L);
        AiTask existing = findByIdempotency(actor, command.idempotencyKey());
        if (existing != null) {
            return replayOrConflict(existing, command.requestDigest());
        }
        try {
            return inTransaction(actor.actorId(), () -> createWorkflowInTransaction(actor, command, quota));
        } catch (DuplicateKeyException exception) {
            AiTask winner = findByIdempotency(actor, command.idempotencyKey());
            if (winner != null) {
                return replayOrConflict(winner, command.requestDigest());
            }
            throw taskInvalid("工作流任务创建冲突");
        }
    }

    @Override
    public AiTaskDTO getOwned(long actorId, String taskId) {
        return toDto(requireOwnedTask(actorId, taskId), true);
    }

    @Override
    public AiTaskDTO getOwned(AiTaskAccessScopeDTO scope, String taskId) {
        return toDto(requireScopedTask(scope, taskId), true);
    }

    @Override
    public PageResult<AiTaskSummaryDTO> pageOwned(long actorId, AiTaskQueryDTO query, PageQuery pageQuery) {
        return pageOwnedInternal(actorId, null, query, pageQuery);
    }

    @Override
    public PageResult<AiTaskSummaryDTO> pageOwned(AiTaskAccessScopeDTO scope, AiTaskQueryDTO query, PageQuery pageQuery) {
        if (scope == null) {
            throw taskNotFound();
        }
        return pageOwnedInternal(scope.ownerUserId(), scope, query, pageQuery);
    }

    private PageResult<AiTaskSummaryDTO> pageOwnedInternal(long actorId, AiTaskAccessScopeDTO scope,
                                                            AiTaskQueryDTO query, PageQuery pageQuery) {
        if (actorId <= 0) {
            throw taskNotFound();
        }
        int pageNum = pageQuery == null || pageQuery.getPageNum() == null ? 1 : pageQuery.getPageNum();
        int pageSize = pageQuery == null || pageQuery.getPageSize() == null ? 20 : pageQuery.getPageSize();
        if (pageNum < 1 || pageSize < 1 || pageSize > 100) {
            throw taskInvalid("分页参数无效");
        }
        LambdaQueryWrapper<AiTask> wrapper = new LambdaQueryWrapper<AiTask>()
            .eq(AiTask::getOwnerUserId, actorId)
            .eq(AiTask::getActorType, APP_USER)
            .eq(AiTask::getActorId, actorId)
            .orderByDesc(AiTask::getCreateTime)
            .orderByDesc(AiTask::getTaskId);
        if (scope == null) {
            wrapper.ne(AiTask::getResourceType, AiTaskResourceType.WORKFLOW_ORDER.value());
        } else {
            wrapper.apply("(resource_type <> 'workflow_order' OR EXISTS (SELECT 1 FROM av_workflow_order o "
                    + "WHERE o.order_id = av_ai_task.resource_id AND o.tenant_id = {0} AND o.owner_user_id = {1} "
                    + "AND o.workspace_id = {2}))",
                scope.tenantId(), scope.ownerUserId(), scope.workspaceId());
        }
        if (query != null) {
            if (notBlank(query.taskType())) {
                wrapper.eq(AiTask::getTaskType, AiTaskType.fromValue(query.taskType()).value());
            }
            if (notBlank(query.status())) {
                wrapper.eq(AiTask::getTaskStatus, AiTaskStatus.fromValue(query.status()).value());
            }
            if (notBlank(query.resourceType())) {
                wrapper.eq(AiTask::getResourceType, AiTaskResourceType.fromValue(query.resourceType()).value());
            }
            if (notBlank(query.resourceId())) {
                wrapper.eq(AiTask::getResourceId, parsePositiveId(query.resourceId(), "任务不存在"));
            }
            if (notBlank(query.projectId())) {
                wrapper.eq(AiTask::getResourceId, parsePositiveId(query.projectId(), "任务不存在"));
            }
            if (notBlank(query.keyword())) {
                wrapper.and(item -> item.like(AiTask::getTaskType, query.keyword())
                    .or().like(AiTask::getErrorCode, query.keyword()));
            }
        }
        Page<AiTask> page = taskMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        List<AiTask> records = page.getRecords() == null ? List.of() : page.getRecords();
        return PageResult.build(records.stream().map(this::toSummary).toList(), page.getTotal());
    }

    @Override
    public AiTaskDTO requestCancellation(long actorId, String taskId, String cancellationKey) {
        return requestCancellationInternal(actorId, null, taskId, cancellationKey);
    }

    @Override
    public AiTaskDTO requestCancellation(AiTaskAccessScopeDTO scope, String taskId, String cancellationKey) {
        if (scope == null) {
            throw taskNotFound();
        }
        return requestCancellationInternal(scope.ownerUserId(), scope, taskId, cancellationKey);
    }

    private AiTaskDTO requestCancellationInternal(long actorId, AiTaskAccessScopeDTO scope, String taskId,
                                                   String cancellationKey) {
        if (actorId <= 0 || !IDEMPOTENCY_KEY.matcher(nullToEmpty(cancellationKey)).matches()) {
            throw taskInvalid("取消请求无效");
        }
        return inTransaction(actorId, () -> {
            AiTask task = scope == null ? requireOwnedTask(actorId, taskId) : requireScopedTask(scope, taskId);
            if (isTerminal(task.getTaskStatus())) {
                return toDto(task, true);
            }
            AiTaskExecution execution = requireActiveExecution(actorId, task);
            if (AiTaskExecutionStatus.QUEUED.value().equals(execution.getExecutionStatus())) {
                cancelQueued(actorId, task, execution, Instant.now());
                if (isRenderTask(task) && restoreProjectAfterRenderTerminal(actorId, task) != 1) {
                    throw new LeaseLostException();
                }
                task.setTaskStatus(AiTaskStatus.CANCELLED.value());
                task.setStage(AiTaskStage.CANCELLED.value());
                task.setCancelRequested(true);
                task.setRowVersion(number(task.getRowVersion()) + 1L);
                return toDto(task, true);
            }
            if (!AiTaskExecutionStatus.RUNNING.value().equals(execution.getExecutionStatus())) {
                return toDto(task, true);
            }
            int rootUpdated = updateTask(actorId, task, new LambdaUpdateWrapper<AiTask>()
                .eq(AiTask::getTaskId, task.getTaskId())
                .eq(AiTask::getOwnerUserId, actorId)
                .eq(AiTask::getTaskStatus, AiTaskStatus.RUNNING.value())
                .eq(AiTask::getRowVersion, number(task.getRowVersion()))
                .eq(AiTask::getActiveExecutionId, execution.getTaskExecutionId())
                .set(AiTask::getCancelRequested, true)
                .set(AiTask::getRowVersion, number(task.getRowVersion()) + 1L));
            if (rootUpdated != 1) {
                throw new LeaseLostException();
            }
            int executionUpdated = updateExecution(actorId, new LambdaUpdateWrapper<AiTaskExecution>()
                .eq(AiTaskExecution::getTaskExecutionId, execution.getTaskExecutionId())
                .eq(AiTaskExecution::getOwnerUserId, actorId)
                .eq(AiTaskExecution::getExecutionStatus, AiTaskExecutionStatus.RUNNING.value())
                .eq(AiTaskExecution::getRowVersion, number(execution.getRowVersion()))
                .set(AiTaskExecution::getCancelRequestedSnapshot, true)
                .set(AiTaskExecution::getRowVersion, number(execution.getRowVersion()) + 1L));
            if (executionUpdated != 1) {
                throw new LeaseLostException();
            }
            task.setCancelRequested(true);
            task.setRowVersion(number(task.getRowVersion()) + 1L);
            return toDto(task, true);
        });
    }

    @Override
    public AiTaskDTO retryOwned(long actorId, RetryAiTaskDTO command) {
        if (actorId <= 0 || command == null) {
            throw taskInvalid("重试请求无效");
        }
        AiTask source = requireOwnedTask(actorId, command.sourceTaskId());
        if (!AiTaskStatus.FAILED.value().equals(source.getTaskStatus())
            && !AiTaskStatus.CANCELLED.value().equals(source.getTaskStatus())) {
            throw taskInvalid("当前任务不可重试");
        }
        AiTaskRequestPayloadDTO payload = readRequestPayload(source);
        String draftRevision = draftRevision(source, payload);
        if (!notBlank(draftRevision)) {
            throw taskInvalid("任务冻结输入损坏");
        }
        return createFreeTask(actorId, new CreateFreeAiTaskDTO(AiTaskType.fromValue(source.getTaskType()),
            AiTaskResourceType.fromValue(source.getResourceType()), Long.toString(source.getResourceId()),
            Long.toString(source.getResourceId()), draftRevision, null, command.idempotencyKey(),
            command.requestDigest(), FreeAiTaskQuotaPolicyServiceImpl.POLICY_VERSION, 0L, payload));
    }

    @Override
    public AiTaskLeaseDTO claimNext(String workerId, int perUserConcurrencyLimit, int systemConcurrencyLimit) {
        if (!notBlank(workerId) || perUserConcurrencyLimit < 1 || perUserConcurrencyLimit > 100
            || systemConcurrencyLimit < 1 || systemConcurrencyLimit > 100
            || perUserConcurrencyLimit > systemConcurrencyLimit) {
            throw taskInvalid("任务领取参数无效");
        }
        if (transactionTemplate == null) {
            return claimNextInTransaction(workerId, perUserConcurrencyLimit, systemConcurrencyLimit);
        }
        return transactionTemplate.execute(status ->
            claimNextInTransaction(workerId, perUserConcurrencyLimit, systemConcurrencyLimit));
    }

    private AiTaskLeaseDTO claimNextInTransaction(String workerId, int perUserConcurrencyLimit,
                                                  int systemConcurrencyLimit) {
        LocalDateTime current = taskMapper.selectDatabaseNow();
        if (current == null) {
            throw taskInvalid("数据库时间不可用");
        }
        List<AiTaskExecution> candidates = executionMapper.selectList(new LambdaQueryWrapper<AiTaskExecution>()
            .eq(AiTaskExecution::getExecutionStatus, AiTaskExecutionStatus.QUEUED.value())
            .le(AiTaskExecution::getNextRunAt, current)
            .orderByAsc(AiTaskExecution::getNextRunAt)
            .orderByAsc(AiTaskExecution::getTaskExecutionId)
            .last("LIMIT " + CLAIM_SCAN_LIMIT + " FOR UPDATE SKIP LOCKED"));
        if (candidates == null || candidates.isEmpty() || taskMapper.lockDispatchCapacityGuard() == null) {
            return null;
        }
        if (executionMapper.countLiveRunningNonWorkflow(current) >= systemConcurrencyLimit) {
            return null;
        }
        for (AiTaskExecution candidate : candidates) {
            if (candidate == null || candidate.getActorType() == null || candidate.getActorId() == null
                || candidate.getTaskId() == null) {
                continue;
            }
            AiTaskActorDTO actor;
            try {
                actor = new AiTaskActorDTO(candidate.getActorType(), candidate.getActorId(), candidate.getOwnerUserId());
            } catch (IllegalArgumentException exception) {
                continue;
            }
            AiTask root = findTask(actor, candidate.getTaskId());
            if (root == null || !Objects.equals(root.getOwnerUserId(), actor.ownerUserId())) {
                continue;
            }
            if (workflowTask(AiTaskType.fromValue(root.getTaskType()))) {
                continue;
            }
            if (executionMapper.countLiveRunningByActor(actor.actorType(), actor.actorId(), current)
                >= perUserConcurrencyLimit) {
                continue;
            }
            try (AuditFillContext.Scope ignored = AuditFillContext.open(actor.actorId())) {
                AiTaskLeaseDTO lease = claimCandidate(actor, candidate.getTaskExecutionId(), workerId,
                    current);
                if (lease != null) {
                    return lease;
                }
            }
        }
        return null;
    }

    @Override
    public AiTaskLeaseDTO claimNextWorkflow(String workerId, int concurrencyLimit) {
        if (!notBlank(workerId) || concurrencyLimit < 1 || concurrencyLimit > 100) {
            throw taskInvalid("workflow dispatch parameters are invalid");
        }
        if (transactionTemplate == null) {
            return claimNextWorkflowInTransaction(workerId, concurrencyLimit);
        }
        return transactionTemplate.execute(status -> claimNextWorkflowInTransaction(workerId, concurrencyLimit));
    }

    @Override
    public boolean releaseClaimedWorkflow(AiTaskLeaseDTO lease) {
        if (lease == null || lease.getAttemptId() != null) {
            return false;
        }
        long actorId = actorId(lease);
        return Boolean.TRUE.equals(inTransaction(actorId, () -> releaseClaimedWorkflowInTransaction(lease, actorId)));
    }

    private boolean releaseClaimedWorkflowInTransaction(AiTaskLeaseDTO lease, long actorId) {
        AiTaskExecution execution = findExecution(actor(lease, null), parsePositiveId(lease.getExecutionId(), "任务执行不存在"));
        if (execution == null || !AiTaskExecutionStatus.RUNNING.value().equals(execution.getExecutionStatus())
            || !Objects.equals(execution.getLeaseToken(), lease.getLeaseToken())) {
            return false;
        }
        AiTask task = findTask(actor(lease, execution.getOwnerUserId()), execution.getTaskId());
        if (task == null || !workflowTask(AiTaskType.fromValue(task.getTaskType()))
            || !AiTaskStatus.RUNNING.value().equals(task.getTaskStatus())
            || !Objects.equals(task.getActiveExecutionId(), execution.getTaskExecutionId())
            || Boolean.TRUE.equals(task.getCancelRequested())) {
            return false;
        }
        LocalDateTime current = taskMapper.selectDatabaseNow();
        if (current == null) {
            throw taskInvalid("数据库时间不可用");
        }
        int executionUpdated = updateExecution(actorId, new LambdaUpdateWrapper<AiTaskExecution>()
            .eq(AiTaskExecution::getTaskExecutionId, execution.getTaskExecutionId())
            .eq(AiTaskExecution::getActorType, lease.getActorType())
            .eq(AiTaskExecution::getActorId, actorId)
            .eq(AiTaskExecution::getExecutionStatus, AiTaskExecutionStatus.RUNNING.value())
            .eq(AiTaskExecution::getLeaseToken, lease.getLeaseToken())
            .eq(AiTaskExecution::getRowVersion, number(execution.getRowVersion()))
            .set(AiTaskExecution::getExecutionStatus, AiTaskExecutionStatus.QUEUED.value())
            .set(AiTaskExecution::getLeaseOwner, null)
            .set(AiTaskExecution::getLeaseToken, null)
            .set(AiTaskExecution::getLeaseExpiresAt, null)
            .set(AiTaskExecution::getNextRunAt, current)
            .set(AiTaskExecution::getRowVersion, number(execution.getRowVersion()) + 1L));
        if (executionUpdated != 1) {
            return false;
        }
        int taskUpdated = taskMapper.update(new AiTask(), new LambdaUpdateWrapper<AiTask>()
            .eq(AiTask::getTaskId, task.getTaskId())
            .eq(AiTask::getActorType, lease.getActorType())
            .eq(AiTask::getActorId, actorId)
            .eq(AiTask::getTaskStatus, AiTaskStatus.RUNNING.value())
            .eq(AiTask::getActiveExecutionId, execution.getTaskExecutionId())
            .eq(AiTask::getRowVersion, number(task.getRowVersion()))
            .set(AiTask::getTaskStatus, AiTaskStatus.QUEUED.value())
            .set(AiTask::getStage, AiTaskStage.WAITING_FOR_DISPATCH.value())
            .set(AiTask::getRowVersion, number(task.getRowVersion()) + 1L));
        if (taskUpdated != 1) {
            throw new LeaseLostException();
        }
        return true;
    }

    private AiTaskLeaseDTO claimNextWorkflowInTransaction(String workerId, int concurrencyLimit) {
        LocalDateTime current = taskMapper.selectDatabaseNow();
        if (current == null) {
            throw taskInvalid("database time is unavailable");
        }
        List<AiTaskExecution> candidates = executionMapper.selectQueuedWorkflowForUpdate(current, CLAIM_SCAN_LIMIT);
        if (candidates == null || candidates.isEmpty() || taskMapper.lockDispatchCapacityGuard() == null
            || executionMapper.countLiveRunningWorkflow(current) >= concurrencyLimit) {
            return null;
        }
        for (AiTaskExecution candidate : candidates) {
            if (candidate == null || candidate.getActorType() == null || candidate.getActorId() == null
                || candidate.getTaskId() == null) {
                continue;
            }
            AiTaskActorDTO actor;
            try {
                actor = new AiTaskActorDTO(candidate.getActorType(), candidate.getActorId(), candidate.getOwnerUserId());
            } catch (IllegalArgumentException exception) {
                continue;
            }
            AiTask root = findTask(actor, candidate.getTaskId());
            if (root == null || !Objects.equals(root.getOwnerUserId(), actor.ownerUserId())
                || !workflowTask(AiTaskType.fromValue(root.getTaskType()))) {
                continue;
            }
            try (AuditFillContext.Scope ignored = AuditFillContext.open(actor.actorId())) {
                AiTaskLeaseDTO lease = claimCandidate(actor, candidate.getTaskExecutionId(), workerId, current);
                if (lease != null) {
                    return lease;
                }
            }
        }
        return null;
    }

    @Override
    public AiTaskLeaseDTO beginAttempt(AiTaskLeaseDTO lease, Instant now) {
        if (now == null) {
            throw taskInvalid("任务尝试参数无效");
        }
        long actorId = actorId(lease);
        return inTransaction(actorId, () -> {
            LeaseState state = loadLiveLeaseState(actorId, lease, now);
            if (state == null) {
                return null;
            }
            List<AiTaskAttempt> attempts = attemptMapper.selectList(new LambdaQueryWrapper<AiTaskAttempt>()
                .eq(AiTaskAttempt::getActorType, lease.getActorType())
                .eq(AiTaskAttempt::getActorId, actorId)
                .eq(AiTaskAttempt::getTaskExecutionId, state.execution().getTaskExecutionId())
                .orderByDesc(AiTaskAttempt::getAttemptNo));
            if (attempts != null && attempts.stream().anyMatch(attempt ->
                AiTaskAttemptStatus.RUNNING.value().equals(attempt.getAttemptStatus()))) {
                return null;
            }
            long attemptNo = attempts == null ? 1L : attempts.stream().map(AiTaskAttempt::getAttemptNo)
                .filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(0L) + 1L;
            AiTaskAttempt attempt = new AiTaskAttempt();
            attempt.setTaskAttemptId(IdWorker.getId());
            attempt.setOwnerUserId(state.task().getOwnerUserId());
            attempt.setTaskId(state.task().getTaskId());
            attempt.setTaskExecutionId(state.execution().getTaskExecutionId());
            attempt.setAttemptNo(attemptNo);
            attempt.setAttemptStatus(AiTaskAttemptStatus.RUNNING.value());
            attempt.setRowVersion(0L);
            attempt.setWorkerId(lease.getWorkerId());
            attempt.setLeaseTokenDigest(sha256(lease.getLeaseToken()));
            attempt.setStartedAt(local(now));
            auditCreate(attempt, actor(lease, state.task().getOwnerUserId()));
            if (attemptMapper.insert(attempt) != 1) {
                throw new LeaseLostException();
            }
            return copyLease(lease, Long.toString(attempt.getTaskAttemptId()), (int) attemptNo,
                Math.toIntExact(number(state.execution().getRowVersion())));
        });
    }

    @Override
    public DispatchContext loadDispatchContext(AiTaskLeaseDTO lease) {
        long actorId = actorId(lease);
        LeaseState state = loadLeaseState(actorId, lease);
        if (state == null) {
            return null;
        }
        return new DispatchContext(actor(lease, state.task().getOwnerUserId()),
            AiTaskType.fromValue(state.task().getTaskType()),
            readRequestPayload(state.task()), state.execution().getOutputConfigDigest());
    }

    @Override
    public AiTaskLeaseDTO renew(AiTaskLeaseDTO lease, Instant now) {
        if (now == null) {
            throw taskInvalid("租约续期参数无效");
        }
        long actorId = actorId(lease);
        return inTransaction(actorId, () -> {
            LeaseState state = loadLiveLeaseState(actorId, lease, now);
            if (state == null) {
                return null;
            }
            long nextVersion = number(state.execution().getRowVersion()) + 1L;
            int changed = updateExecution(actorId, new LambdaUpdateWrapper<AiTaskExecution>()
                .eq(AiTaskExecution::getTaskExecutionId, state.execution().getTaskExecutionId())
                .eq(AiTaskExecution::getActorType, lease.getActorType())
                .eq(AiTaskExecution::getActorId, actorId)
                .eq(AiTaskExecution::getExecutionStatus, AiTaskExecutionStatus.RUNNING.value())
                .eq(AiTaskExecution::getLeaseToken, lease.getLeaseToken())
                .eq(AiTaskExecution::getRowVersion, number(state.execution().getRowVersion()))
                .gt(AiTaskExecution::getLeaseExpiresAt, local(now))
                .set(AiTaskExecution::getLeaseExpiresAt, local(now.plusSeconds(LEASE_SECONDS)))
                .set(AiTaskExecution::getRowVersion, nextVersion));
            return changed == 1 ? copyLease(lease, lease.getAttemptId(), lease.getAttemptNo(),
                Math.toIntExact(nextVersion)) : null;
        });
    }

    @Override
    public AiTaskLeaseDTO reportProgress(AiTaskLeaseDTO lease, AiTaskProgressDTO progress, Instant now) {
        if (progress == null || now == null || progress.getStage() == null || progress.getPercent() < 0
            || progress.getPercent() > 100 || !Objects.equals(progress.getExecutionId(), lease.getExecutionId())
            || !Objects.equals(progress.getLeaseToken(), lease.getLeaseToken())
            || progress.getExpectedRowVersion() != lease.getRowVersion()) {
            return null;
        }
        long actorId = actorId(lease);
        return inTransaction(actorId, () -> {
            LeaseState state = loadLiveLeaseState(actorId, lease, now);
            if (state == null) {
                return null;
            }
            if (progress.getPercent() < number(state.execution().getProgressPercent())) {
                return lease;
            }
            boolean stageChanged = !Objects.equals(state.execution().getStage(), progress.getStage().value());
            if (!stageChanged && progress.getPercent() == number(state.execution().getProgressPercent())
                && isThrottled(state.execution().getUpdateTime(), now)) {
                return lease;
            }
            if (!stageChanged && isThrottled(state.execution().getUpdateTime(), now)) {
                return lease;
            }
            long nextExecutionVersion = number(state.execution().getRowVersion()) + 1L;
            int executionUpdated = updateExecution(actorId, new LambdaUpdateWrapper<AiTaskExecution>()
                .eq(AiTaskExecution::getTaskExecutionId, state.execution().getTaskExecutionId())
                .eq(AiTaskExecution::getActorType, lease.getActorType())
                .eq(AiTaskExecution::getActorId, actorId)
                .eq(AiTaskExecution::getExecutionStatus, AiTaskExecutionStatus.RUNNING.value())
                .eq(AiTaskExecution::getLeaseToken, lease.getLeaseToken())
                .eq(AiTaskExecution::getRowVersion, number(state.execution().getRowVersion()))
                .gt(AiTaskExecution::getLeaseExpiresAt, local(now))
                .set(AiTaskExecution::getStage, progress.getStage().value())
                .set(AiTaskExecution::getProgressPercent, progress.getPercent())
                .set(AiTaskExecution::getRowVersion, nextExecutionVersion));
            if (executionUpdated != 1) {
                throw new LeaseLostException();
            }
            long nextTaskVersion = number(state.task().getRowVersion()) + 1L;
            int taskUpdated = updateTask(actorId, state.task(), new LambdaUpdateWrapper<AiTask>()
                .eq(AiTask::getTaskId, state.task().getTaskId())
                .eq(AiTask::getActorType, lease.getActorType())
                .eq(AiTask::getActorId, actorId)
                .eq(AiTask::getTaskStatus, AiTaskStatus.RUNNING.value())
                .eq(AiTask::getActiveExecutionId, state.execution().getTaskExecutionId())
                .eq(AiTask::getRowVersion, number(state.task().getRowVersion()))
                .set(AiTask::getStage, progress.getStage().value())
                .set(AiTask::getProgressPercent, progress.getPercent())
                .set(AiTask::getRowVersion, nextTaskVersion));
            if (taskUpdated != 1) {
                throw new LeaseLostException();
            }
            return copyLease(lease, lease.getAttemptId(), lease.getAttemptNo(), Math.toIntExact(nextExecutionVersion));
        });
    }

    @Override
    public boolean complete(AiTaskLeaseDTO lease, AiTaskCompletionDTO completion, Instant now) {
        if (completion == null || now == null || !Objects.equals(completion.getExecutionId(), lease.getExecutionId())
            || !Objects.equals(completion.getLeaseToken(), lease.getLeaseToken())
            || completion.getExpectedRowVersion() != lease.getRowVersion()) {
            return false;
        }
        long actorId = actorId(lease);
        try {
            return inTransaction(actorId, () -> completeInTransaction(actorId, lease, completion, now));
        } catch (LeaseLostException ignored) {
            return false;
        }
    }

    @Override
    public boolean cancel(AiTaskLeaseDTO lease, String safeMessage, Instant now) {
        if (now == null) {
            return false;
        }
        long actorId = actorId(lease);
        try {
            return inTransaction(actorId, () -> {
                LeaseState state = loadCancellationState(actorId, lease);
                if (state == null) {
                    return false;
                }
                AiTaskLeaseDTO currentLease = copyLease(lease, lease.getAttemptId(), lease.getAttemptNo(),
                    Math.toIntExact(number(state.execution().getRowVersion())));
                return terminalInTransaction(actorId, currentLease, AiTaskStatus.CANCELLED.value(),
                    AiTaskExecutionStatus.CANCELLED.value(), "CANCELLED", safeSummary(safeMessage), null, null,
                    now, true);
            });
        } catch (LeaseLostException ignored) {
            return false;
        }
    }

    @Override
    public boolean cancellationRequested(AiTaskLeaseDTO lease) {
        long actorId = actorId(lease);
        LeaseState state = loadLeaseState(actorId, lease);
        return state == null || Boolean.TRUE.equals(state.task().getCancelRequested())
            || Boolean.TRUE.equals(state.execution().getCancelRequestedSnapshot());
    }

    @Override
    public int recoverExpired(Instant now, int limit) {
        if (now == null || limit < 1 || limit > 100) {
            throw taskInvalid("任务恢复参数无效");
        }
        LocalDateTime current = local(now);
        List<AiTaskExecution> candidates = executionMapper.selectList(new LambdaQueryWrapper<AiTaskExecution>()
            .eq(AiTaskExecution::getExecutionStatus, AiTaskExecutionStatus.RUNNING.value())
            .le(AiTaskExecution::getLeaseExpiresAt, current)
            .orderByAsc(AiTaskExecution::getLeaseExpiresAt)
            .last("LIMIT " + limit));
        if (candidates == null) {
            return 0;
        }
        int recovered = 0;
        for (AiTaskExecution candidate : candidates) {
            if (candidate == null || candidate.getActorType() == null || candidate.getActorId() == null
                || candidate.getTaskExecutionId() == null) {
                continue;
            }
            try {
                AiTaskActorDTO actor = new AiTaskActorDTO(candidate.getActorType(), candidate.getActorId(),
                    candidate.getOwnerUserId());
                Boolean changed = inTransaction(actor.actorId(),
                    () -> recoverExecution(actor, candidate.getTaskExecutionId(), now));
                if (Boolean.TRUE.equals(changed)) {
                    recovered++;
                }
            } catch (LeaseLostException ignored) {
                // Another recovery worker already owns this execution.
            }
        }
        return recovered;
    }

    private AiTaskDTO createInTransaction(long actorId, CreateSpec spec) {
        AiTask existing = findByIdempotency(actorId, spec.idempotencyKey());
        if (existing != null) {
            return replayOrConflict(existing, spec.requestDigest());
        }
        CreationProject project = requireProject(actorId, spec.projectId(), spec.taskType());
        TimelineDraft draft = requireDraft(actorId, project.getProjectId());
        if (!Objects.equals(draft.getRevision(), spec.draftRevision())) {
            throw revisionConflict();
        }
        long taskId = IdWorker.getId();
        AiTaskRequestPayloadDTO frozenPayload = freezePayload(spec.taskType(), spec.payload(), Long.toString(taskId),
            Long.toString(project.getProjectId()), Long.toString(draft.getRevision()), null, draft);
        AiTask task = newTask(actorId, taskId, project.getProjectId(), spec, serializeRequest(frozenPayload));
        if (taskMapper.insert(task) != 1) {
            throw taskInvalid("任务创建失败");
        }

        Long inputVersionId = null;
        String outputConfigDigest = null;
        if (spec.taskType() == AiTaskType.TIMELINE_RENDER) {
            TimelineVersion version = appendRenderInputVersion(actorId, project, draft, taskId, spec.requestDigest());
            inputVersionId = version.getTimelineVersionId();
            frozenPayload = freezePayload(spec.taskType(), spec.payload(), Long.toString(taskId),
                Long.toString(project.getProjectId()), Long.toString(draft.getRevision()), Long.toString(inputVersionId),
                draft);
            outputConfigDigest = renderOutputConfigDigest((AiTaskRenderPayloadDTO) frozenPayload);
        }
        String requestPayloadJson = serializeRequest(frozenPayload);
        AiTaskExecution execution = newExecution(actorId, task, inputVersionId, outputConfigDigest);
        if (executionMapper.insert(execution) != 1) {
            throw taskInvalid("任务执行创建失败");
        }
        int transitioned = updateTask(actorId, task, new LambdaUpdateWrapper<AiTask>()
            .eq(AiTask::getTaskId, taskId)
            .eq(AiTask::getOwnerUserId, actorId)
            .eq(AiTask::getTaskStatus, AiTaskStatus.PENDING.value())
            .eq(AiTask::getRowVersion, 0L)
            .set(AiTask::getInputVersionId, inputVersionId)
            .set(AiTask::getRequestPayloadJson, requestPayloadJson)
            .set(AiTask::getTaskStatus, AiTaskStatus.QUEUED.value())
            .set(AiTask::getStage, AiTaskStage.QUEUED.value())
            .set(AiTask::getActiveExecutionId, execution.getTaskExecutionId())
            .set(AiTask::getRowVersion, 1L));
        if (transitioned != 1) {
            throw new LeaseLostException();
        }
        task.setInputVersionId(inputVersionId);
        task.setRequestPayloadJson(requestPayloadJson);
        task.setTaskStatus(AiTaskStatus.QUEUED.value());
        task.setStage(AiTaskStage.QUEUED.value());
        task.setActiveExecutionId(execution.getTaskExecutionId());
        task.setRowVersion(1L);
        if (spec.taskType() == AiTaskType.TIMELINE_RENDER
            && transitionProjectToRendering(actorId, project.getProjectId()) != 1) {
            throw new LeaseLostException();
        }
        return toDto(task, true);
    }

    private AiTaskDTO createWorkflowInTransaction(AiTaskActorDTO actor, CreateWorkflowAiTaskDTO command,
                                                   IFreeAiTaskQuotaPolicyService.FrozenQuota quota) {
        AiTask existing = findByIdempotency(actor, command.idempotencyKey());
        if (existing != null) {
            return replayOrConflict(existing, command.requestDigest());
        }
        long resourceId = parsePositiveId(command.resourceId(), "工作流任务资源不存在");
        long taskId = IdWorker.getId();
        AiTask task = new AiTask();
        task.setTaskId(taskId);
        task.setOwnerUserId(actor.ownerUserId());
        task.setTaskType(command.taskType().value());
        task.setResourceType(command.resourceType().value());
        task.setResourceId(resourceId);
        task.setIdempotencyKey(command.idempotencyKey());
        task.setRequestDigest(command.requestDigest());
        task.setRequestSchemaVersion("workflow-1");
        task.setRequestPayloadJson(serializeRequest(command.payload()));
        task.setTaskStatus(AiTaskStatus.PENDING.value());
        task.setStage(AiTaskStage.WAITING_FOR_DISPATCH.value());
        task.setProgressPercent(0);
        task.setRowVersion(0L);
        task.setCancelRequested(false);
        task.setQuotaPolicyVersion(quota.quotaPolicyVersion());
        task.setEstimatedUsage(quota.estimatedUsage());
        auditCreate(task, actor);
        if (taskMapper.insert(task) != 1) {
            throw taskInvalid("工作流任务创建失败");
        }

        AiTaskExecution execution = newExecution(actor, task, null, null);
        execution.setStage(AiTaskStage.WAITING_FOR_DISPATCH.value());
        if (executionMapper.insert(execution) != 1) {
            throw taskInvalid("工作流任务执行创建失败");
        }
        int transitioned = updateTask(actor.actorId(), task, new LambdaUpdateWrapper<AiTask>()
            .eq(AiTask::getTaskId, taskId)
            .eq(AiTask::getActorType, actor.actorType())
            .eq(AiTask::getActorId, actor.actorId())
            .eq(AiTask::getTaskStatus, AiTaskStatus.PENDING.value())
            .eq(AiTask::getRowVersion, 0L)
            .set(AiTask::getTaskStatus, AiTaskStatus.QUEUED.value())
            .set(AiTask::getStage, AiTaskStage.WAITING_FOR_DISPATCH.value())
            .set(AiTask::getActiveExecutionId, execution.getTaskExecutionId())
            .set(AiTask::getRowVersion, 1L));
        if (transitioned != 1) {
            throw new LeaseLostException();
        }
        task.setTaskStatus(AiTaskStatus.QUEUED.value());
        task.setActiveExecutionId(execution.getTaskExecutionId());
        task.setRowVersion(1L);
        return toDto(task, true);
    }

    private AiTaskLeaseDTO claimCandidate(AiTaskActorDTO actor, long executionId, String workerId,
                                          LocalDateTime current) {
        long actorId = actor.actorId();
        AiTaskExecution execution = findExecution(actor, executionId);
        if (execution == null || !AiTaskExecutionStatus.QUEUED.value().equals(execution.getExecutionStatus())) {
            return null;
        }
        AiTask task = findTask(actor, execution.getTaskId());
        if (task == null || !AiTaskStatus.QUEUED.value().equals(task.getTaskStatus())
            || !Objects.equals(task.getActiveExecutionId(), executionId) || Boolean.TRUE.equals(task.getCancelRequested())) {
            return null;
        }
        String token = newLeaseToken();
        long nextExecutionVersion = number(execution.getRowVersion()) + 1L;
        int executionUpdated = updateExecution(actorId, new LambdaUpdateWrapper<AiTaskExecution>()
            .eq(AiTaskExecution::getTaskExecutionId, executionId)
            .eq(AiTaskExecution::getActorType, actor.actorType())
            .eq(AiTaskExecution::getActorId, actorId)
            .eq(AiTaskExecution::getExecutionStatus, AiTaskExecutionStatus.QUEUED.value())
            .eq(AiTaskExecution::getRowVersion, number(execution.getRowVersion()))
            .le(AiTaskExecution::getNextRunAt, current)
            .set(AiTaskExecution::getExecutionStatus, AiTaskExecutionStatus.RUNNING.value())
            .set(AiTaskExecution::getLeaseOwner, workerId)
            .set(AiTaskExecution::getLeaseToken, token)
            .set(AiTaskExecution::getLeaseExpiresAt, current.plusSeconds(LEASE_SECONDS))
            .set(AiTaskExecution::getNextRunAt, null)
            .set(AiTaskExecution::getCancelRequestedSnapshot, false)
            .set(AiTaskExecution::getRowVersion, nextExecutionVersion));
        if (executionUpdated != 1) {
            return null;
        }
        long nextTaskVersion = number(task.getRowVersion()) + 1L;
        int taskUpdated = updateTask(actorId, task, new LambdaUpdateWrapper<AiTask>()
            .eq(AiTask::getTaskId, task.getTaskId())
            .eq(AiTask::getActorType, actor.actorType())
            .eq(AiTask::getActorId, actorId)
            .eq(AiTask::getTaskStatus, AiTaskStatus.QUEUED.value())
            .eq(AiTask::getActiveExecutionId, executionId)
            .eq(AiTask::getRowVersion, number(task.getRowVersion()))
            .set(AiTask::getTaskStatus, AiTaskStatus.RUNNING.value())
            .set(AiTask::getStage, workflowTask(AiTaskType.fromValue(task.getTaskType()))
                ? AiTaskStage.PREPARING_INPUTS.value() : AiTaskStage.PREPARING_ASSETS.value())
            .set(AiTask::getStartedAt, current)
            .set(AiTask::getRowVersion, nextTaskVersion));
        if (taskUpdated != 1) {
            throw new LeaseLostException();
        }
        return new AiTaskLeaseDTO(Long.toString(task.getTaskId()), Long.toString(executionId), null, token, workerId,
            actor.actorType(), Long.toString(actorId),
            execution.getInputVersionId() == null ? null : Long.toString(execution.getInputVersionId()),
            Math.toIntExact(number(execution.getExecutionNo())), 0, Math.toIntExact(nextExecutionVersion));
    }

    private boolean completeInTransaction(long actorId, AiTaskLeaseDTO lease, AiTaskCompletionDTO completion,
                                          Instant now) {
        LeaseState state = loadLiveLeaseState(actorId, lease, now);
        if (state == null || Boolean.TRUE.equals(state.task().getCancelRequested())) {
            return false;
        }
        AiTaskType taskType = AiTaskType.fromValue(state.task().getTaskType());
        if (completion.isSuccess() && taskType == AiTaskType.TIMELINE_RENDER) {
            return false;
        }
        AiTaskResultPayloadDTO payload = completion.getResultPayload();
        String resultJson = null;
        String resultSchema = null;
        if (completion.isSuccess()) {
            if (payload == null || !matchesResult(taskType, payload)) {
                throw taskInvalid("任务结果类型不匹配");
            }
            resultJson = serializeResult(payload);
            readResultPayload(taskType, resultJson);
            resultSchema = workflowTask(taskType) ? "workflow-1" : TimelineContractLimits.SCHEMA_VERSION;
        }
        String taskStatus = completion.isSuccess() ? AiTaskStatus.SUCCESS.value() : AiTaskStatus.FAILED.value();
        String executionStatus = completion.isSuccess() ? AiTaskExecutionStatus.SUCCESS.value()
            : AiTaskExecutionStatus.FAILED.value();
        return terminalInTransaction(actorId, lease, taskStatus, executionStatus, completion.getErrorCode(),
            safeSummary(completion.getSafeMessage()), resultSchema, resultJson, now, true);
    }

    private boolean terminalInTransaction(long actorId, AiTaskLeaseDTO lease, String taskStatus,
                                          String executionStatus, String errorCode, String errorSummary,
                                          String resultSchema, String resultJson, Instant now,
                                          boolean requireLiveLease) {
        LeaseState state = requireLiveLease ? loadLiveLeaseState(actorId, lease, now)
            : loadLeaseState(actorId, lease);
        if (state == null || (!requireLiveLease && !isLeaseExpired(state.execution(), now))) {
            return false;
        }
        AiTask task = state.task();
        AiTaskExecution execution = state.execution();
        if (AiTaskStatus.SUCCESS.value().equals(taskStatus) && Boolean.TRUE.equals(task.getCancelRequested())) {
            return false;
        }
        long nextExecutionVersion = number(execution.getRowVersion()) + 1L;
        LambdaUpdateWrapper<AiTaskExecution> executionUpdate = new LambdaUpdateWrapper<AiTaskExecution>()
            .eq(AiTaskExecution::getTaskExecutionId, execution.getTaskExecutionId())
            .eq(AiTaskExecution::getActorType, lease.getActorType())
            .eq(AiTaskExecution::getActorId, actorId)
            .eq(AiTaskExecution::getExecutionStatus, AiTaskExecutionStatus.RUNNING.value())
            .eq(AiTaskExecution::getLeaseToken, lease.getLeaseToken())
            .eq(AiTaskExecution::getRowVersion, number(execution.getRowVersion()))
            .set(AiTaskExecution::getExecutionStatus, executionStatus)
            .set(AiTaskExecution::getStage, terminalStage(executionStatus))
            .set(AiTaskExecution::getProgressPercent,
                AiTaskExecutionStatus.SUCCESS.value().equals(executionStatus) ? 100 : number(execution.getProgressPercent()))
            .set(AiTaskExecution::getLeaseOwner, null)
            .set(AiTaskExecution::getLeaseToken, null)
            .set(AiTaskExecution::getLeaseExpiresAt, null)
            .set(AiTaskExecution::getErrorCode, errorCode)
            .set(AiTaskExecution::getErrorSummary, errorSummary)
            .set(AiTaskExecution::getRowVersion, nextExecutionVersion);
        if (requireLiveLease) {
            executionUpdate.gt(AiTaskExecution::getLeaseExpiresAt, local(now));
        } else {
            executionUpdate.le(AiTaskExecution::getLeaseExpiresAt, local(now));
        }
        int executionUpdated = updateExecution(actorId, executionUpdate);
        if (executionUpdated != 1) {
            return false;
        }
        if (lease.getAttemptId() != null && !finishAttempt(actorId, lease, executionStatus, errorSummary, now)) {
            throw new LeaseLostException();
        }
        long nextTaskVersion = number(task.getRowVersion()) + 1L;
        AiTask resultEntity = new AiTask();
        resultEntity.setUpdateBy(actorId);
        resultEntity.setTaskStatus(taskStatus);
        resultEntity.setStage(terminalStage(executionStatus));
        resultEntity.setProgressPercent(AiTaskStatus.SUCCESS.value().equals(taskStatus) ? 100
            : number(task.getProgressPercent()));
        resultEntity.setResultAssetId(null);
        resultEntity.setResultSchemaVersion(resultSchema);
        resultEntity.setResultPayloadJson(resultJson);
        resultEntity.setErrorCode(errorCode);
        resultEntity.setErrorSummary(errorSummary);
        resultEntity.setFinishedAt(local(now));
        resultEntity.setRowVersion(nextTaskVersion);
        int taskUpdated = taskMapper.update(resultEntity, new LambdaUpdateWrapper<AiTask>()
            .eq(AiTask::getTaskId, task.getTaskId())
            .eq(AiTask::getActorType, lease.getActorType())
            .eq(AiTask::getActorId, actorId)
            .eq(AiTask::getTaskStatus, AiTaskStatus.RUNNING.value())
            .eq(AiTask::getActiveExecutionId, execution.getTaskExecutionId())
            .eq(AiTask::getRowVersion, number(task.getRowVersion()))
            .set(AiTask::getTaskStatus, taskStatus)
            .set(AiTask::getStage, terminalStage(executionStatus))
            .set(AiTask::getProgressPercent, AiTaskStatus.SUCCESS.value().equals(taskStatus) ? 100
                : number(task.getProgressPercent()))
            .set(AiTask::getResultAssetId, null)
            .set(AiTask::getResultSchemaVersion, resultSchema)
            .set(AiTask::getResultPayloadJson, resultJson)
            .set(AiTask::getErrorCode, errorCode)
            .set(AiTask::getErrorSummary, errorSummary)
            .set(AiTask::getFinishedAt, local(now))
            .set(AiTask::getRowVersion, nextTaskVersion));
        if (taskUpdated != 1) {
            throw new LeaseLostException();
        }
        if (isRenderTask(task) && !AiTaskStatus.SUCCESS.value().equals(taskStatus)
            && restoreProjectAfterRenderTerminal(actorId, task) != 1) {
            throw new LeaseLostException();
        }
        return true;
    }

    private boolean recoverExecution(AiTaskActorDTO actor, long executionId, Instant now) {
        long actorId = actor.actorId();
        AiTaskExecution execution = findExecution(actor, executionId);
        if (execution == null || !AiTaskExecutionStatus.RUNNING.value().equals(execution.getExecutionStatus())
            || !isLeaseExpired(execution, now)) {
            return false;
        }
        AiTask task = findTask(actor, execution.getTaskId());
        if (task == null || !AiTaskStatus.RUNNING.value().equals(task.getTaskStatus())
            || !Objects.equals(task.getActiveExecutionId(), executionId)) {
            return false;
        }
        if (Boolean.TRUE.equals(task.getCancelRequested())) {
            AiTaskAttempt runningAttempt = attemptMapper.selectOne(new LambdaQueryWrapper<AiTaskAttempt>()
                .eq(AiTaskAttempt::getActorType, actor.actorType())
                .eq(AiTaskAttempt::getActorId, actorId)
                .eq(AiTaskAttempt::getTaskExecutionId, executionId)
                .eq(AiTaskAttempt::getAttemptStatus, AiTaskAttemptStatus.RUNNING.value()));
            AiTaskLeaseDTO lease = new AiTaskLeaseDTO(Long.toString(task.getTaskId()), Long.toString(executionId),
                runningAttempt == null ? null : Long.toString(runningAttempt.getTaskAttemptId()), execution.getLeaseToken(),
                execution.getLeaseOwner(), execution.getActorType(), Long.toString(actorId),
                execution.getInputVersionId() == null ? null : Long.toString(execution.getInputVersionId()),
                Math.toIntExact(number(execution.getExecutionNo())),
                runningAttempt == null ? 0 : Math.toIntExact(number(runningAttempt.getAttemptNo())),
                Math.toIntExact(number(execution.getRowVersion())));
            return terminalInTransaction(actorId, lease, AiTaskStatus.CANCELLED.value(),
                AiTaskExecutionStatus.CANCELLED.value(), "CANCELLED", "任务已取消", null, null, now, false);
        }
        if (workflowProviderExecutionInFlight(task, execution)) {
            return false;
        }
        int updated = updateExecution(actorId, new LambdaUpdateWrapper<AiTaskExecution>()
            .eq(AiTaskExecution::getTaskExecutionId, executionId)
            .eq(AiTaskExecution::getActorType, actor.actorType())
            .eq(AiTaskExecution::getActorId, actorId)
            .eq(AiTaskExecution::getExecutionStatus, AiTaskExecutionStatus.RUNNING.value())
            .eq(AiTaskExecution::getLeaseToken, execution.getLeaseToken())
            .eq(AiTaskExecution::getRowVersion, number(execution.getRowVersion()))
            .le(AiTaskExecution::getLeaseExpiresAt, local(now))
            .set(AiTaskExecution::getExecutionStatus, AiTaskExecutionStatus.QUEUED.value())
            .set(AiTaskExecution::getLeaseOwner, null)
            .set(AiTaskExecution::getLeaseToken, null)
            .set(AiTaskExecution::getLeaseExpiresAt, null)
            .set(AiTaskExecution::getNextRunAt, local(now))
            .set(AiTaskExecution::getRowVersion, number(execution.getRowVersion()) + 1L));
        if (updated != 1) {
            return false;
        }
        long nextTaskVersion = number(task.getRowVersion()) + 1L;
        AiTask requeuedTask = new AiTask();
        requeuedTask.setUpdateBy(actorId);
        requeuedTask.setTaskStatus(AiTaskStatus.QUEUED.value());
        requeuedTask.setStage(workflowTask(AiTaskType.fromValue(task.getTaskType()))
            ? AiTaskStage.WAITING_FOR_DISPATCH.value() : AiTaskStage.QUEUED.value());
        requeuedTask.setRowVersion(nextTaskVersion);
        int rootUpdated = taskMapper.update(requeuedTask, new LambdaUpdateWrapper<AiTask>()
            .eq(AiTask::getTaskId, task.getTaskId())
            .eq(AiTask::getActorType, actor.actorType())
            .eq(AiTask::getActorId, actorId)
            .eq(AiTask::getTaskStatus, AiTaskStatus.RUNNING.value())
            .eq(AiTask::getActiveExecutionId, executionId)
            .eq(AiTask::getCancelRequested, false)
            .eq(AiTask::getRowVersion, number(task.getRowVersion()))
            .set(AiTask::getTaskStatus, AiTaskStatus.QUEUED.value())
            .set(AiTask::getStage, workflowTask(AiTaskType.fromValue(task.getTaskType()))
                ? AiTaskStage.WAITING_FOR_DISPATCH.value() : AiTaskStage.QUEUED.value())
            .set(AiTask::getRowVersion, nextTaskVersion));
        if (rootUpdated != 1) {
            throw new LeaseLostException();
        }
        List<AiTaskAttempt> attempts = attemptMapper.selectList(new LambdaQueryWrapper<AiTaskAttempt>()
            .eq(AiTaskAttempt::getActorType, actor.actorType())
            .eq(AiTaskAttempt::getActorId, actorId)
            .eq(AiTaskAttempt::getTaskExecutionId, executionId)
            .eq(AiTaskAttempt::getAttemptStatus, AiTaskAttemptStatus.RUNNING.value()));
        if (attempts != null) {
            for (AiTaskAttempt attempt : attempts) {
                updateAttempt(actorId, attempt, AiTaskAttemptStatus.ABANDONED.value(), "lease_expired", now);
            }
        }
        return true;
    }

    private void cancelQueued(long actorId, AiTask task, AiTaskExecution execution, Instant now) {
        int executionUpdated = updateExecution(actorId, new LambdaUpdateWrapper<AiTaskExecution>()
            .eq(AiTaskExecution::getTaskExecutionId, execution.getTaskExecutionId())
            .eq(AiTaskExecution::getOwnerUserId, actorId)
            .eq(AiTaskExecution::getExecutionStatus, AiTaskExecutionStatus.QUEUED.value())
            .eq(AiTaskExecution::getRowVersion, number(execution.getRowVersion()))
            .set(AiTaskExecution::getExecutionStatus, AiTaskExecutionStatus.CANCELLED.value())
            .set(AiTaskExecution::getStage, AiTaskStage.CANCELLED.value())
            .set(AiTaskExecution::getErrorCode, "CANCELLED")
            .set(AiTaskExecution::getErrorSummary, "任务已取消")
            .set(AiTaskExecution::getRowVersion, number(execution.getRowVersion()) + 1L));
        if (executionUpdated != 1) {
            throw new LeaseLostException();
        }
        int taskUpdated = updateTask(actorId, task, new LambdaUpdateWrapper<AiTask>()
            .eq(AiTask::getTaskId, task.getTaskId())
            .eq(AiTask::getOwnerUserId, actorId)
            .eq(AiTask::getTaskStatus, AiTaskStatus.QUEUED.value())
            .eq(AiTask::getActiveExecutionId, execution.getTaskExecutionId())
            .eq(AiTask::getRowVersion, number(task.getRowVersion()))
            .set(AiTask::getTaskStatus, AiTaskStatus.CANCELLED.value())
            .set(AiTask::getStage, AiTaskStage.CANCELLED.value())
            .set(AiTask::getCancelRequested, true)
            .set(AiTask::getErrorCode, "CANCELLED")
            .set(AiTask::getErrorSummary, "任务已取消")
            .set(AiTask::getFinishedAt, local(now))
            .set(AiTask::getRowVersion, number(task.getRowVersion()) + 1L));
        if (taskUpdated != 1) {
            throw new LeaseLostException();
        }
    }

    private boolean finishAttempt(long actorId, AiTaskLeaseDTO lease, String executionStatus, String errorSummary,
                                  Instant now) {
        long attemptId = parsePositiveId(lease.getAttemptId(), "任务尝试不存在");
        AiTaskAttempt attempt = attemptMapper.selectOne(new LambdaQueryWrapper<AiTaskAttempt>()
            .eq(AiTaskAttempt::getTaskAttemptId, attemptId)
            .eq(AiTaskAttempt::getActorType, lease.getActorType())
            .eq(AiTaskAttempt::getActorId, actorId)
            .eq(AiTaskAttempt::getTaskExecutionId, parsePositiveId(lease.getExecutionId(), "任务执行不存在"))
            .eq(AiTaskAttempt::getAttemptStatus, AiTaskAttemptStatus.RUNNING.value()));
        if (attempt == null) {
            return false;
        }
        String attemptStatus = switch (executionStatus) {
            case "success" -> AiTaskAttemptStatus.SUCCESS.value();
            case "cancelled" -> AiTaskAttemptStatus.CANCELLED.value();
            default -> AiTaskAttemptStatus.FAILED.value();
        };
        return updateAttempt(actorId, attempt, attemptStatus, errorSummary, now) == 1;
    }

    private int updateAttempt(long actorId, AiTaskAttempt attempt, String status, String errorSummary, Instant now) {
        AiTaskAttempt update = new AiTaskAttempt();
        update.setUpdateBy(actorId);
        update.setAttemptStatus(status);
        update.setFinishedAt(local(now));
        update.setExitCategory(status);
        update.setErrorSummary(errorSummary);
        return attemptMapper.update(update, new LambdaUpdateWrapper<AiTaskAttempt>()
            .eq(AiTaskAttempt::getTaskAttemptId, attempt.getTaskAttemptId())
            .eq(AiTaskAttempt::getActorType, attempt.getActorType())
            .eq(AiTaskAttempt::getActorId, actorId)
            .eq(AiTaskAttempt::getAttemptStatus, AiTaskAttemptStatus.RUNNING.value())
            .eq(AiTaskAttempt::getRowVersion, number(attempt.getRowVersion()))
            .set(AiTaskAttempt::getAttemptStatus, status)
            .set(AiTaskAttempt::getFinishedAt, local(now))
            .set(AiTaskAttempt::getExitCategory, status)
            .set(AiTaskAttempt::getErrorSummary, errorSummary)
            .set(AiTaskAttempt::getRowVersion, number(attempt.getRowVersion()) + 1L));
    }

    private LeaseState loadLeaseState(long actorId, AiTaskLeaseDTO lease) {
        return loadLeaseState(actorId, lease, true);
    }

    private LeaseState loadLiveLeaseState(long actorId, AiTaskLeaseDTO lease, Instant now) {
        LeaseState state = loadLeaseState(actorId, lease);
        return state != null && isLeaseLive(state.execution(), now) ? state : null;
    }

    private LeaseState loadCancellationState(long actorId, AiTaskLeaseDTO lease) {
        LeaseState state = loadLeaseState(actorId, lease, false);
        if (state == null || (!Boolean.TRUE.equals(state.task().getCancelRequested())
            && !Boolean.TRUE.equals(state.execution().getCancelRequestedSnapshot()))) {
            return null;
        }
        return state;
    }

    private LeaseState loadLeaseState(long actorId, AiTaskLeaseDTO lease, boolean requireRowVersion) {
        if (lease == null || !notBlank(lease.getLeaseToken()) || !notBlank(lease.getExecutionId())
            || !notBlank(lease.getTaskId())) {
            return null;
        }
        long taskId = parsePositiveId(lease.getTaskId(), "任务不存在");
        long executionId = parsePositiveId(lease.getExecutionId(), "任务执行不存在");
        AiTaskActorDTO leaseActor;
        try {
            leaseActor = new AiTaskActorDTO(lease.getActorType(), actorId,
                APP_USER.equals(lease.getActorType()) ? actorId : null);
        } catch (IllegalArgumentException exception) {
            return null;
        }
        AiTask task = findTask(leaseActor, taskId);
        AiTaskExecution execution = findExecution(leaseActor, executionId);
        if (task == null || execution == null || !Objects.equals(task.getTaskId(), execution.getTaskId())
            || !Objects.equals(task.getActiveExecutionId(), executionId)
            || !AiTaskStatus.RUNNING.value().equals(task.getTaskStatus())
            || !AiTaskExecutionStatus.RUNNING.value().equals(execution.getExecutionStatus())
            || !Objects.equals(task.getOwnerUserId(), leaseActor.ownerUserId())
            || !Objects.equals(execution.getOwnerUserId(), leaseActor.ownerUserId())
            || !Objects.equals(execution.getLeaseToken(), lease.getLeaseToken())
            || (requireRowVersion && number(execution.getRowVersion()) != lease.getRowVersion())) {
            return null;
        }
        return new LeaseState(task, execution);
    }

    private AiTask newTask(long actorId, long taskId, long projectId, CreateSpec spec, String requestPayloadJson) {
        AiTask task = new AiTask();
        task.setTaskId(taskId);
        task.setOwnerUserId(actorId);
        task.setTaskType(spec.taskType().value());
        task.setResourceType(AiTaskResourceType.CREATION_PROJECT.value());
        task.setResourceId(projectId);
        task.setInputVersionId(null);
        task.setIdempotencyKey(spec.idempotencyKey());
        task.setRequestDigest(spec.requestDigest());
        task.setRequestSchemaVersion(TimelineContractLimits.SCHEMA_VERSION);
        task.setRequestPayloadJson(requestPayloadJson);
        task.setTaskStatus(AiTaskStatus.PENDING.value());
        task.setStage(AiTaskStage.QUEUED.value());
        task.setProgressPercent(0);
        task.setRowVersion(0L);
        task.setCancelRequested(false);
        task.setQuotaPolicyVersion(spec.quota().quotaPolicyVersion());
        task.setEstimatedUsage(spec.quota().estimatedUsage());
        auditCreate(task, actorId);
        return task;
    }

    private AiTaskExecution newExecution(long actorId, AiTask task, Long inputVersionId, String outputConfigDigest) {
        return newExecution(new AiTaskActorDTO(APP_USER, actorId, actorId), task, inputVersionId, outputConfigDigest);
    }

    private AiTaskExecution newExecution(AiTaskActorDTO actor, AiTask task, Long inputVersionId,
                                         String outputConfigDigest) {
        AiTaskExecution execution = new AiTaskExecution();
        execution.setTaskExecutionId(IdWorker.getId());
        execution.setOwnerUserId(actor.ownerUserId());
        execution.setTaskId(task.getTaskId());
        execution.setResourceId(task.getResourceId());
        execution.setExecutionNo(1L);
        execution.setExecutionStatus(AiTaskExecutionStatus.QUEUED.value());
        execution.setStage(AiTaskStage.QUEUED.value());
        execution.setProgressPercent(0);
        execution.setRowVersion(0L);
        execution.setNextRunAt(LocalDateTime.now(ZoneOffset.UTC));
        execution.setCancelRequestedSnapshot(false);
        execution.setInputVersionId(inputVersionId);
        execution.setOutputConfigDigest(outputConfigDigest);
        auditCreate(execution, actor);
        return execution;
    }

    private TimelineVersion appendRenderInputVersion(long actorId, CreationProject project, TimelineDraft draft,
                                                     long taskId, String requestDigest) {
        TimelineVersion version = new TimelineVersion();
        version.setTimelineVersionId(IdWorker.getId());
        version.setOwnerUserId(actorId);
        version.setProjectId(project.getProjectId());
        version.setVersionNo(nextVersionNo(actorId, project.getProjectId()));
        version.setSourceDraftRevision(draft.getRevision());
        version.setVersionReason(RENDER_INPUT);
        version.setIdempotencyKey("render-input-" + taskId);
        version.setRequestDigest(requestDigest);
        version.setSchemaVersion(TimelineContractLimits.SCHEMA_VERSION);
        version.setContentJson(draft.getContentJson());
        version.setContentHash(draft.getContentHash());
        version.setDurationMs(draft.getDurationMs());
        auditCreate(version, actorId);
        if (versionMapper.insert(version) != 1) {
            throw taskInvalid("渲染输入版本创建失败");
        }
        List<TimelineAssetRef> refs = assetRefMapper.selectList(new LambdaQueryWrapper<TimelineAssetRef>()
            .eq(TimelineAssetRef::getOwnerUserId, actorId)
            .eq(TimelineAssetRef::getProjectId, project.getProjectId())
            .eq(TimelineAssetRef::getDocumentType, DRAFT)
            .eq(TimelineAssetRef::getDocumentId, draft.getTimelineDraftId()));
        if (refs != null) {
            for (TimelineAssetRef ref : refs) {
                TimelineAssetRef copy = new TimelineAssetRef();
                copy.setTimelineAssetRefId(IdWorker.getId());
                copy.setOwnerUserId(actorId);
                copy.setProjectId(project.getProjectId());
                copy.setDocumentType(VERSION);
                copy.setDocumentId(version.getTimelineVersionId());
                copy.setElementId(ref.getElementId());
                copy.setAssetId(ref.getAssetId());
                copy.setUsageType(ref.getUsageType());
                copy.setStartMs(ref.getStartMs());
                copy.setEndMs(ref.getEndMs());
                auditCreate(copy, actorId);
                if (assetRefMapper.insert(copy) != 1) {
                    throw taskInvalid("渲染输入素材引用创建失败");
                }
            }
        }
        return version;
    }

    private long nextVersionNo(long actorId, long projectId) {
        List<TimelineVersion> versions = versionMapper.selectList(new LambdaQueryWrapper<TimelineVersion>()
            .eq(TimelineVersion::getOwnerUserId, actorId)
            .eq(TimelineVersion::getProjectId, projectId)
            .orderByDesc(TimelineVersion::getVersionNo));
        return versions == null ? 1L : versions.stream().map(TimelineVersion::getVersionNo)
            .filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(0L) + 1L;
    }

    private CreateSpec validateCreate(long actorId, CreateFreeAiTaskDTO command) {
        if (actorId <= 0 || command == null || command.taskType() == null
            || command.resourceType() != AiTaskResourceType.CREATION_PROJECT
            || command.payload() == null || command.inputVersionId() != null
            || !IDEMPOTENCY_KEY.matcher(nullToEmpty(command.idempotencyKey())).matches()
            || !LOWER_HEX.matcher(nullToEmpty(command.requestDigest())).matches()) {
            throw taskInvalid("任务创建请求无效");
        }
        long resourceId = parsePositiveId(command.resourceId(), "创作项目不存在");
        long projectId = parsePositiveId(command.projectId(), "创作项目不存在");
        long draftRevision = parsePositiveId(command.draftRevision(), "草稿修订无效");
        if (resourceId != projectId) {
            throw taskInvalid("任务资源与项目不一致");
        }
        validatePayloadIdentity(command.taskType(), command.payload(), command.projectId(), command.draftRevision());
        IFreeAiTaskQuotaPolicyService.FrozenQuota quota = quotaPolicyService.freeze(command.taskType(),
            command.quotaPolicyVersion(), command.estimatedUsage());
        return new CreateSpec(command.taskType(), projectId, draftRevision, command.idempotencyKey(),
            command.requestDigest(), command.payload(), quota);
    }

    private void validatePayloadIdentity(AiTaskType type, AiTaskRequestPayloadDTO payload, String projectId,
                                         String draftRevision) {
        switch (type) {
            case TIMELINE_IMAGE_PROMPT_GENERATE -> {
                if (!(payload instanceof AiTaskImagePromptPayloadDTO image) || image.command() == null
                    || !Objects.equals(projectId, image.command().projectId())
                    || !Objects.equals(draftRevision, image.command().draftRevision())) {
                    throw taskInvalid("图像提示词任务输入无效");
                }
            }
            case TIMELINE_FANCY_TEXT_SUGGEST -> {
                if (!(payload instanceof AiTaskFancyTextPayloadDTO fancy) || fancy.command() == null
                    || !Objects.equals(projectId, fancy.command().projectId())
                    || !Objects.equals(draftRevision, fancy.command().draftRevision())) {
                    throw taskInvalid("花字建议任务输入无效");
                }
            }
            case TIMELINE_SUBTITLE_ALIGN -> {
                if (!(payload instanceof AiTaskSubtitleAlignmentPayloadDTO subtitle) || subtitle.command() == null
                    || !Objects.equals(projectId, subtitle.command().projectId())
                    || !Objects.equals(draftRevision, subtitle.command().draftRevision())) {
                    throw taskInvalid("字幕对齐任务输入无效");
                }
            }
            case TIMELINE_RENDER -> {
                if (!(payload instanceof AiTaskRenderPayloadDTO render) || render.command() == null
                    || render.command().outputConfig() == null) {
                    throw taskInvalid("渲染任务输入无效");
                }
            }
            case WORKFLOW_TEMPLATE_GENERATE, WORKFLOW_TEMPLATE_TEST -> throw taskInvalid("workflow task unsupported");
        }
    }

    private AiTaskRequestPayloadDTO freezePayload(AiTaskType type, AiTaskRequestPayloadDTO payload, String taskId,
                                                   String projectId, String draftRevision, String inputVersionId,
                                                   TimelineDraft draft) {
        return switch (type) {
            case TIMELINE_IMAGE_PROMPT_GENERATE -> {
                TimelineImagePromptCommandDTO source = ((AiTaskImagePromptPayloadDTO) payload).command();
                yield new AiTaskImagePromptPayloadDTO(new TimelineImagePromptCommandDTO(taskId, projectId,
                    draftRevision, source.sourceStartOffset(), source.sourceEndOffset(), source.sourceText(),
                    source.contextBefore(), source.contextAfter(), source.canvasAspect(), source.styleCode()));
            }
            case TIMELINE_FANCY_TEXT_SUGGEST -> {
                TimelineFancyTextSuggestionCommandDTO source = ((AiTaskFancyTextPayloadDTO) payload).command();
                yield new AiTaskFancyTextPayloadDTO(new TimelineFancyTextSuggestionCommandDTO(taskId, projectId,
                    draftRevision, source.sourceStartOffset(), source.sourceEndOffset(), source.sourceText(),
                    source.contextBefore(), source.contextAfter(), source.allowedTemplates()));
            }
            case TIMELINE_SUBTITLE_ALIGN -> {
                TimelineSubtitleAlignmentCommandDTO source = ((AiTaskSubtitleAlignmentPayloadDTO) payload).command();
                yield new AiTaskSubtitleAlignmentPayloadDTO(new TimelineSubtitleAlignmentCommandDTO(taskId, projectId,
                    draftRevision, source.primaryAudioAssetId(), source.scriptTextSnapshot(), source.language(),
                    source.trustedCues()));
            }
            case TIMELINE_RENDER -> {
                TimelineRenderCommandDTO source = ((AiTaskRenderPayloadDTO) payload).command();
                yield new AiTaskRenderPayloadDTO(new TimelineRenderCommandDTO(taskId, null, null, inputVersionId,
                    source.fontRegistryVersion(), source.fontRegistrySha256(), readTimeline(draft.getContentJson()),
                    source.outputConfig(), source.assets()));
            }
            case WORKFLOW_TEMPLATE_GENERATE, WORKFLOW_TEMPLATE_TEST -> payload;
        };
    }

    private String renderOutputConfigDigest(AiTaskRenderPayloadDTO payload) {
        try {
            return sha256(jsonMapper.writeValueAsString(payload.command().outputConfig()));
        } catch (Exception exception) {
            throw taskInvalid("渲染输出配置无效");
        }
    }

    private AiTaskRequestPayloadDTO readRequestPayload(AiTask task) {
        AiTaskType type = AiTaskType.fromValue(task.getTaskType());
        String expectedSchema = workflowTask(type) ? "workflow-1" : TimelineContractLimits.SCHEMA_VERSION;
        if (!expectedSchema.equals(task.getRequestSchemaVersion()) || !notBlank(task.getRequestPayloadJson())) {
            throw taskInvalid("任务请求事实损坏");
        }
        try {
            return switch (type) {
                case TIMELINE_IMAGE_PROMPT_GENERATE -> jsonMapper.readerFor(AiTaskImagePromptPayloadDTO.class)
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).readValue(task.getRequestPayloadJson());
                case TIMELINE_FANCY_TEXT_SUGGEST -> jsonMapper.readerFor(AiTaskFancyTextPayloadDTO.class)
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).readValue(task.getRequestPayloadJson());
                case TIMELINE_SUBTITLE_ALIGN -> jsonMapper.readerFor(AiTaskSubtitleAlignmentPayloadDTO.class)
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).readValue(task.getRequestPayloadJson());
                case TIMELINE_RENDER -> jsonMapper.readerFor(AiTaskRenderPayloadDTO.class)
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).readValue(task.getRequestPayloadJson());
                case WORKFLOW_TEMPLATE_GENERATE, WORKFLOW_TEMPLATE_TEST -> jsonMapper.readerFor(WorkflowAiTaskPayloadDTO.class)
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).readValue(task.getRequestPayloadJson());
            };
        } catch (Exception exception) {
            throw taskInvalid("任务请求事实损坏");
        }
    }

    private AiTaskResultPayloadDTO readResultPayload(AiTaskType type, String json) {
        try {
            return switch (type) {
                case TIMELINE_IMAGE_PROMPT_GENERATE -> jsonMapper.readerFor(AiTaskImagePromptResultPayloadDTO.class)
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).readValue(json);
                case TIMELINE_FANCY_TEXT_SUGGEST -> jsonMapper.readerFor(AiTaskFancyTextResultPayloadDTO.class)
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).readValue(json);
                case TIMELINE_SUBTITLE_ALIGN -> jsonMapper.readerFor(AiTaskSubtitleAlignmentResultPayloadDTO.class)
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).readValue(json);
                case WORKFLOW_TEMPLATE_GENERATE, WORKFLOW_TEMPLATE_TEST -> jsonMapper.readerFor(WorkflowAiTaskResultPayloadDTO.class)
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).readValue(json);
                case TIMELINE_RENDER -> throw taskInvalid("渲染任务不接受建议结果");
            };
        } catch (ServiceException exception) {
            throw exception;
        } catch (Exception exception) {
            throw taskInvalid("任务结果事实损坏");
        }
    }

    private String serializeRequest(AiTaskRequestPayloadDTO payload) {
        try {
            String json = jsonMapper.writeValueAsString(payload);
            if (json.getBytes(StandardCharsets.UTF_8).length > TimelineContractLimits.NUMERIC_LIMITS
                .get("maxTaskRequestBytes").intValue()) {
                throw taskInvalid("任务请求过大");
            }
            return json;
        } catch (ServiceException exception) {
            throw exception;
        } catch (Exception exception) {
            throw taskInvalid("任务请求序列化失败");
        }
    }

    private String serializeResult(AiTaskResultPayloadDTO payload) {
        try {
            String json = jsonMapper.writeValueAsString(payload);
            if (json.getBytes(StandardCharsets.UTF_8).length > TimelineContractLimits.NUMERIC_LIMITS
                .get("maxTaskResultBytes").intValue()) {
                throw taskInvalid("任务结果过大");
            }
            return json;
        } catch (ServiceException exception) {
            throw exception;
        } catch (Exception exception) {
            throw taskInvalid("任务结果序列化失败");
        }
    }

    private boolean matchesResult(AiTaskType type, AiTaskResultPayloadDTO payload) {
        return switch (type) {
            case TIMELINE_IMAGE_PROMPT_GENERATE -> payload instanceof AiTaskImagePromptResultPayloadDTO;
            case TIMELINE_FANCY_TEXT_SUGGEST -> payload instanceof AiTaskFancyTextResultPayloadDTO;
            case TIMELINE_SUBTITLE_ALIGN -> payload instanceof AiTaskSubtitleAlignmentResultPayloadDTO;
            case TIMELINE_RENDER -> false;
            case WORKFLOW_TEMPLATE_GENERATE, WORKFLOW_TEMPLATE_TEST -> payload instanceof WorkflowAiTaskResultPayloadDTO;
        };
    }

    private AiTaskDTO replayOrConflict(AiTask task, String requestDigest) {
        if (!Objects.equals(task.getRequestDigest(), requestDigest)) {
            throw idempotencyConflict();
        }
        return toDto(task, true);
    }

    private AiTaskDTO toDto(AiTask task, boolean includeResult) {
        AiTaskRequestPayloadDTO payload = readRequestPayload(task);
        AiTaskType type = AiTaskType.fromValue(task.getTaskType());
        AiTaskResultPayloadDTO result = null;
        if (includeResult && task.getResultPayloadJson() != null) {
            String expectedSchema = workflowTask(type) ? "workflow-1" : TimelineContractLimits.SCHEMA_VERSION;
            if (!expectedSchema.equals(task.getResultSchemaVersion())) {
                throw taskInvalid("任务结果事实损坏");
            }
            result = readResultPayload(type, task.getResultPayloadJson());
        }
        String status = task.getTaskStatus();
        String projectId = AiTaskResourceType.CREATION_PROJECT.value().equals(task.getResourceType())
            ? Long.toString(task.getResourceId()) : null;
        return new AiTaskDTO(Long.toString(task.getTaskId()), task.getTaskType(), status, task.getStage(),
            task.getResourceType(), Long.toString(task.getResourceId()), projectId,
            draftRevision(task, payload), task.getInputVersionId() == null ? null : Long.toString(task.getInputVersionId()),
            task.getResultAssetId() == null ? null : Long.toString(task.getResultAssetId()), task.getErrorCode(),
            safeSummary(task.getErrorSummary()), asInstant(task.getCreateTime()), asInstant(task.getUpdateTime()), result,
            number(task.getProgressPercent()), !isTerminal(status) && !Boolean.TRUE.equals(task.getCancelRequested()),
            AiTaskStatus.FAILED.value().equals(status) || AiTaskStatus.CANCELLED.value().equals(status));
    }

    private AiTaskSummaryDTO toSummary(AiTask task) {
        String status = task.getTaskStatus();
        String projectId = AiTaskResourceType.CREATION_PROJECT.value().equals(task.getResourceType())
            ? Long.toString(task.getResourceId()) : null;
        return new AiTaskSummaryDTO(Long.toString(task.getTaskId()), task.getTaskType(), status, task.getStage(),
            task.getResourceType(), Long.toString(task.getResourceId()), projectId,
            asInstant(task.getCreateTime()), asInstant(task.getUpdateTime()), task.getErrorCode(),
            safeSummary(task.getErrorSummary()), number(task.getProgressPercent()),
            !isTerminal(status) && !Boolean.TRUE.equals(task.getCancelRequested()),
            AiTaskStatus.FAILED.value().equals(status) || AiTaskStatus.CANCELLED.value().equals(status));
    }

    private String draftRevision(AiTask task, AiTaskRequestPayloadDTO payload) {
        return switch (payload) {
            case AiTaskImagePromptPayloadDTO image -> image.command().draftRevision();
            case AiTaskFancyTextPayloadDTO fancy -> fancy.command().draftRevision();
            case AiTaskSubtitleAlignmentPayloadDTO subtitle -> subtitle.command().draftRevision();
            case AiTaskRenderPayloadDTO ignored -> {
                if (task.getInputVersionId() == null) {
                    yield null;
                }
                TimelineVersion version = versionMapper.selectOne(new LambdaQueryWrapper<TimelineVersion>()
                    .eq(TimelineVersion::getTimelineVersionId, task.getInputVersionId())
                    .eq(TimelineVersion::getOwnerUserId, task.getOwnerUserId())
                    .eq(TimelineVersion::getProjectId, task.getResourceId()));
                yield version == null || version.getSourceDraftRevision() == null ? null
                    : Long.toString(version.getSourceDraftRevision());
            }
            case WorkflowAiTaskPayloadDTO ignored -> null;
        };
    }

    private TimelineDocumentDTO readTimeline(String json) {
        try {
            return jsonMapper.readerFor(TimelineDocumentDTO.class)
                .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).readValue(json);
        } catch (Exception exception) {
            throw taskInvalid("草稿内容损坏");
        }
    }

    private CreationProject requireProject(long actorId, long projectId, AiTaskType taskType) {
        CreationProject project = projectMapper.selectOne(new LambdaQueryWrapper<CreationProject>()
            .eq(CreationProject::getProjectId, projectId)
            .eq(CreationProject::getOwnerUserId, actorId)
            .eq(CreationProject::getDelFlag, "0"));
        if (project == null) {
            throw new ServiceException("创作项目不存在", TimelineErrorCodes.CREATION_PROJECT_NOT_FOUND);
        }
        if (ARCHIVED.equals(project.getProjectStatus())) {
            throw new ServiceException("创作项目已归档", TimelineErrorCodes.CREATION_PROJECT_STATE_CONFLICT);
        }
        if (taskType == AiTaskType.TIMELINE_RENDER
            && !EDITING.equals(project.getProjectStatus()) && !READY.equals(project.getProjectStatus())) {
            throw projectStateConflict();
        }
        return project;
    }

    private int transitionProjectToRendering(long actorId, long projectId) {
        CreationProject update = new CreationProject();
        update.setUpdateBy(actorId);
        update.setProjectStatus(RENDERING);
        return projectMapper.update(update, new LambdaUpdateWrapper<CreationProject>()
            .eq(CreationProject::getProjectId, projectId)
            .eq(CreationProject::getOwnerUserId, actorId)
            .eq(CreationProject::getDelFlag, "0")
            .in(CreationProject::getProjectStatus, List.of(EDITING, READY))
            .set(CreationProject::getProjectStatus, RENDERING));
    }

    private int restoreProjectAfterRenderTerminal(long actorId, AiTask task) {
        if (task.getResourceId() == null) {
            return 0;
        }
        CreationProject project = projectMapper.selectOne(new LambdaQueryWrapper<CreationProject>()
            .eq(CreationProject::getProjectId, task.getResourceId())
            .eq(CreationProject::getOwnerUserId, actorId)
            .eq(CreationProject::getDelFlag, "0"));
        if (project == null) {
            return 0;
        }
        String restoredStatus = project.getCurrentOutputAssetId() == null ? EDITING : READY;
        CreationProject update = new CreationProject();
        update.setUpdateBy(actorId);
        update.setProjectStatus(restoredStatus);
        return projectMapper.update(update, new LambdaUpdateWrapper<CreationProject>()
            .eq(CreationProject::getProjectId, project.getProjectId())
            .eq(CreationProject::getOwnerUserId, actorId)
            .eq(CreationProject::getDelFlag, "0")
            .eq(CreationProject::getProjectStatus, RENDERING)
            .set(CreationProject::getProjectStatus, restoredStatus));
    }

    private boolean isRenderTask(AiTask task) {
        return task != null && AiTaskType.TIMELINE_RENDER.value().equals(task.getTaskType());
    }

    private TimelineDraft requireDraft(long actorId, long projectId) {
        TimelineDraft draft = draftMapper.selectOne(new LambdaQueryWrapper<TimelineDraft>()
            .eq(TimelineDraft::getOwnerUserId, actorId)
            .eq(TimelineDraft::getProjectId, projectId)
            .eq(TimelineDraft::getDelFlag, "0"));
        if (draft == null) {
            throw taskInvalid("创作草稿不存在");
        }
        return draft;
    }

    private AiTask requireOwnedTask(long actorId, String taskId) {
        if (actorId <= 0) {
            throw taskNotFound();
        }
        AiTask task = findOwnedTask(actorId, parsePositiveId(taskId, "任务不存在"));
        if (task == null || AiTaskResourceType.WORKFLOW_ORDER.value().equals(task.getResourceType())) {
            throw taskNotFound();
        }
        return task;
    }

    private boolean workflowTask(AiTaskType type) {
        return type == AiTaskType.WORKFLOW_TEMPLATE_GENERATE || type == AiTaskType.WORKFLOW_TEMPLATE_TEST;
    }

    private boolean workflowProviderExecutionInFlight(AiTask task, AiTaskExecution execution) {
        return workflowTask(AiTaskType.fromValue(task.getTaskType()))
            && (providerExecutionStage(task.getStage()) || providerExecutionStage(execution.getStage()));
    }

    private boolean providerExecutionStage(String stage) {
        return AiTaskStage.SUBMITTING_TO_PROVIDER.value().equals(stage)
            || AiTaskStage.CONFIRMING_PROVIDER_ACCEPTANCE.value().equals(stage)
            || AiTaskStage.PROVIDER_PROCESSING.value().equals(stage)
            || AiTaskStage.PROCESSING_RESULTS.value().equals(stage);
    }

    private AiTask requireScopedTask(AiTaskAccessScopeDTO scope, String taskId) {
        if (scope == null) {
            throw taskNotFound();
        }
        AiTask task = taskMapper.selectOne(new LambdaQueryWrapper<AiTask>()
            .eq(AiTask::getTaskId, parsePositiveId(taskId, "任务不存在"))
            .eq(AiTask::getOwnerUserId, scope.ownerUserId())
            .eq(AiTask::getActorType, APP_USER)
            .eq(AiTask::getActorId, scope.ownerUserId()));
        if (task == null) {
            throw taskNotFound();
        }
        if (AiTaskResourceType.WORKFLOW_ORDER.value().equals(task.getResourceType())
            && taskMapper.countOwnedWorkflowOrder(task.getResourceId(), scope.tenantId(), scope.ownerUserId(),
                scope.workspaceId()) != 1) {
            throw taskNotFound();
        }
        return task;
    }

    private AiTaskExecution requireActiveExecution(long actorId, AiTask task) {
        if (task.getActiveExecutionId() == null) {
            throw taskInvalid("任务执行不存在");
        }
        AiTaskExecution execution = findExecution(actorId, task.getActiveExecutionId());
        if (execution == null || !Objects.equals(execution.getTaskId(), task.getTaskId())) {
            throw taskInvalid("任务执行不存在");
        }
        return execution;
    }

    private AiTask findByIdempotency(long actorId, String key) {
        return taskMapper.selectOne(new LambdaQueryWrapper<AiTask>()
            .eq(AiTask::getOwnerUserId, actorId)
            .eq(AiTask::getIdempotencyKey, key));
    }

    private AiTask findOwnedTask(long actorId, Long taskId) {
        if (taskId == null) {
            return null;
        }
        return taskMapper.selectOne(new LambdaQueryWrapper<AiTask>()
            .eq(AiTask::getTaskId, taskId)
            .eq(AiTask::getOwnerUserId, actorId));
    }

    private AiTask findTask(AiTaskActorDTO actor, Long taskId) {
        if (actor == null || taskId == null) {
            return null;
        }
        return taskMapper.selectOne(new LambdaQueryWrapper<AiTask>()
            .eq(AiTask::getTaskId, taskId)
            .eq(AiTask::getActorType, actor.actorType())
            .eq(AiTask::getActorId, actor.actorId()));
    }

    private AiTaskExecution findExecution(long actorId, long executionId) {
        return executionMapper.selectOne(new LambdaQueryWrapper<AiTaskExecution>()
            .eq(AiTaskExecution::getTaskExecutionId, executionId)
            .eq(AiTaskExecution::getOwnerUserId, actorId));
    }

    private AiTaskExecution findExecution(AiTaskActorDTO actor, long executionId) {
        return executionMapper.selectOne(new LambdaQueryWrapper<AiTaskExecution>()
            .eq(AiTaskExecution::getTaskExecutionId, executionId)
            .eq(AiTaskExecution::getActorType, actor.actorType())
            .eq(AiTaskExecution::getActorId, actor.actorId()));
    }

    private int updateTask(long actorId, AiTask current, LambdaUpdateWrapper<AiTask> wrapper) {
        AiTask update = new AiTask();
        update.setUpdateBy(actorId);
        return taskMapper.update(update, wrapper);
    }

    private int updateExecution(long actorId, LambdaUpdateWrapper<AiTaskExecution> wrapper) {
        AiTaskExecution update = new AiTaskExecution();
        update.setUpdateBy(actorId);
        return executionMapper.update(update, wrapper);
    }

    private <T> T inTransaction(long actorId, java.util.function.Supplier<T> action) {
        if (actorId <= 0) {
            throw taskInvalid("任务审计主体无效");
        }
        if (transactionTemplate == null) {
            try (AuditFillContext.Scope ignored = AuditFillContext.open(actorId)) {
                return action.get();
            }
        }
        return transactionTemplate.execute(status -> {
            try (AuditFillContext.Scope ignored = AuditFillContext.open(actorId)) {
                return action.get();
            }
        });
    }

    private long actorId(AiTaskLeaseDTO lease) {
        if (lease == null || !POSITIVE_ID.matcher(nullToEmpty(lease.getActorId())).matches()) {
            throw taskInvalid("任务租约无效");
        }
        return parsePositiveId(lease.getActorId(), "任务租约无效");
    }

    private AiTaskActorDTO actor(AiTaskLeaseDTO lease, Long ownerUserId) {
        if (lease == null) {
            throw taskInvalid("任务租约无效");
        }
        try {
            return new AiTaskActorDTO(lease.getActorType(), actorId(lease), ownerUserId);
        } catch (IllegalArgumentException exception) {
            throw taskInvalid("任务租约无效");
        }
    }

    private AiTaskLeaseDTO copyLease(AiTaskLeaseDTO lease, String attemptId, int attemptNo, int rowVersion) {
        return new AiTaskLeaseDTO(lease.getTaskId(), lease.getExecutionId(), attemptId, lease.getLeaseToken(),
            lease.getWorkerId(), lease.getActorType(), lease.getActorId(), lease.getInputVersionId(),
            lease.getExecutionNo(), attemptNo, rowVersion);
    }

    private String newLeaseToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private boolean isThrottled(LocalDateTime updateTime, Instant now) {
        return updateTime != null && updateTime.plusSeconds(1).isAfter(local(now));
    }

    private boolean isLeaseLive(AiTaskExecution execution, Instant now) {
        return now != null && execution != null && execution.getLeaseExpiresAt() != null
            && execution.getLeaseExpiresAt().isAfter(local(now));
    }

    private boolean isLeaseExpired(AiTaskExecution execution, Instant now) {
        return now != null && execution != null && execution.getLeaseExpiresAt() != null
            && !execution.getLeaseExpiresAt().isAfter(local(now));
    }

    private String terminalStage(String executionStatus) {
        return switch (executionStatus) {
            case "success" -> AiTaskStage.COMPLETED.value();
            case "cancelled" -> AiTaskStage.CANCELLED.value();
            default -> AiTaskStage.FAILED.value();
        };
    }

    private boolean isTerminal(String status) {
        return AiTaskStatus.SUCCESS.value().equals(status) || AiTaskStatus.FAILED.value().equals(status)
            || AiTaskStatus.CANCELLED.value().equals(status);
    }

    private long parsePositiveId(String value, String message) {
        if (!POSITIVE_ID.matcher(nullToEmpty(value)).matches()) {
            throw taskInvalid(message);
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw taskInvalid(message);
        }
    }

    private long number(Long value) {
        return value == null ? 0L : value;
    }

    private int number(Integer value) {
        return value == null ? 0 : value;
    }

    private LocalDateTime local(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private String asInstant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC).toString();
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String safeSummary(String value) {
        if (value == null) {
            return null;
        }
        int codePoints = value.codePointCount(0, value.length());
        int end = value.offsetByCodePoints(0, Math.min(codePoints, SAFE_MESSAGE_MAX_CODE_POINTS));
        return value.substring(0, end);
    }

    private void auditCreate(AiTask task, long actorId) {
        auditCreate(task, new AiTaskActorDTO(APP_USER, actorId, actorId));
    }

    private AiTask findByIdempotency(AiTaskActorDTO actor, String key) {
        return taskMapper.selectOne(new LambdaQueryWrapper<AiTask>()
            .eq(AiTask::getActorType, actor.actorType())
            .eq(AiTask::getActorId, actor.actorId())
            .eq(AiTask::getIdempotencyKey, key));
    }

    private void auditCreate(AiTask task, AiTaskActorDTO actor) {
        task.setActorType(actor.actorType());
        task.setActorId(actor.actorId());
        task.setCreateBy(actor.actorId());
        task.setUpdateBy(actor.actorId());
    }

    private void auditCreate(AiTaskExecution execution, long actorId) {
        auditCreate(execution, new AiTaskActorDTO(APP_USER, actorId, actorId));
    }

    private void auditCreate(AiTaskExecution execution, AiTaskActorDTO actor) {
        execution.setActorType(actor.actorType());
        execution.setActorId(actor.actorId());
        execution.setCreateBy(actor.actorId());
        execution.setUpdateBy(actor.actorId());
    }

    private void auditCreate(AiTaskAttempt attempt, long actorId) {
        auditCreate(attempt, new AiTaskActorDTO(APP_USER, actorId, actorId));
    }

    private void auditCreate(AiTaskAttempt attempt, AiTaskActorDTO actor) {
        attempt.setActorType(actor.actorType());
        attempt.setActorId(actor.actorId());
        attempt.setCreateBy(actor.actorId());
        attempt.setUpdateBy(actor.actorId());
    }

    private void auditCreate(TimelineVersion version, long actorId) {
        version.setActorType(APP_USER);
        version.setActorId(actorId);
        version.setCreateBy(actorId);
        version.setUpdateBy(actorId);
    }

    private void auditCreate(TimelineAssetRef ref, long actorId) {
        ref.setActorType(APP_USER);
        ref.setActorId(actorId);
        ref.setCreateBy(actorId);
        ref.setUpdateBy(actorId);
    }

    private ServiceException taskNotFound() {
        return new ServiceException("任务不存在", TimelineErrorCodes.CREATION_PROJECT_NOT_FOUND);
    }

    private ServiceException taskInvalid(String message) {
        return new ServiceException(message, TimelineErrorCodes.TIMELINE_DOCUMENT_INVALID);
    }

    private ServiceException projectStateConflict() {
        return new ServiceException("当前创作项目状态不允许该操作",
            TimelineErrorCodes.CREATION_PROJECT_STATE_CONFLICT);
    }

    private ServiceException revisionConflict() {
        return new ServiceException("时间轴修订冲突", TimelineErrorCodes.TIMELINE_REVISION_CONFLICT);
    }

    private ServiceException idempotencyConflict() {
        return new ServiceException("幂等键已用于不同的任务请求", TimelineErrorCodes.TIMELINE_IDEMPOTENCY_CONFLICT);
    }

    private record CreateSpec(AiTaskType taskType, long projectId, long draftRevision, String idempotencyKey,
                              String requestDigest, AiTaskRequestPayloadDTO payload,
                              IFreeAiTaskQuotaPolicyService.FrozenQuota quota) {
    }

    private record LeaseState(AiTask task, AiTaskExecution execution) {
    }

    private static final class LeaseLostException extends RuntimeException {
    }
}
