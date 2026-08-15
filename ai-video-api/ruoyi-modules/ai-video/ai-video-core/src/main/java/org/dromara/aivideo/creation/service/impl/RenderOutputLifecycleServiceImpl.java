package org.dromara.aivideo.creation.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.dromara.aivideo.creation.domain.CreationAsset;
import org.dromara.aivideo.creation.domain.CreationProject;
import org.dromara.aivideo.creation.dto.PendingRenderOutputDTO;
import org.dromara.aivideo.creation.dto.RegisterPendingRenderOutputDTO;
import org.dromara.aivideo.creation.dto.RenderOutputFailureDTO;
import org.dromara.aivideo.creation.mapper.CreationAssetMapper;
import org.dromara.aivideo.creation.mapper.CreationProjectMapper;
import org.dromara.aivideo.creation.service.ICreationAssetService;
import org.dromara.aivideo.creation.service.IRenderOutputLifecycleService;
import org.dromara.aivideo.task.domain.AiTask;
import org.dromara.aivideo.task.domain.AiTaskAttempt;
import org.dromara.aivideo.task.domain.AiTaskExecution;
import org.dromara.aivideo.task.dto.AiTaskLeaseDTO;
import org.dromara.aivideo.task.enums.AiTaskAttemptStatus;
import org.dromara.aivideo.task.enums.AiTaskExecutionStatus;
import org.dromara.aivideo.task.enums.AiTaskStage;
import org.dromara.aivideo.task.enums.AiTaskStatus;
import org.dromara.aivideo.task.enums.AiTaskType;
import org.dromara.aivideo.task.mapper.AiTaskAttemptMapper;
import org.dromara.aivideo.task.mapper.AiTaskExecutionMapper;
import org.dromara.aivideo.task.mapper.AiTaskMapper;
import org.dromara.aivideo.timeline.constant.TimelineErrorCodes;
import org.dromara.aivideo.timeline.dto.TimelineRenderResultDTO;
import org.dromara.aivideo.timeline.service.TimelineRenderOutputHandle;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.mybatis.audit.AuditFillContext;
import org.dromara.common.oss.client.OssClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Keeps object upload outside database transactions, but commits its database projection only through one final CAS.
 */
@Service
public class RenderOutputLifecycleServiceImpl implements IRenderOutputLifecycleService {

    private static final String APP_USER = "app_user";
    private static final String PENDING = "pending";
    private static final String READY = "ready";
    private static final String RENDER_OUTPUT = "timeline_render_output";
    private static final Pattern POSITIVE_ID = Pattern.compile("[1-9][0-9]{0,18}");
    private static final Pattern LOWER_HEX = Pattern.compile("[0-9a-f]{64}");

    private final ICreationAssetService assetService;
    private final CreationAssetMapper assetMapper;
    private final CreationProjectMapper projectMapper;
    private final AiTaskMapper taskMapper;
    private final AiTaskExecutionMapper executionMapper;
    private final AiTaskAttemptMapper attemptMapper;
    private final TransactionTemplate transactionTemplate;
    private final ObjectProvider<OssClient> ossClientProvider;

    RenderOutputLifecycleServiceImpl(ICreationAssetService assetService, CreationAssetMapper assetMapper,
                                     CreationProjectMapper projectMapper, AiTaskMapper taskMapper,
                                     AiTaskExecutionMapper executionMapper, AiTaskAttemptMapper attemptMapper) {
        this(assetService, assetMapper, projectMapper, taskMapper, executionMapper, attemptMapper,
            (TransactionTemplate) null, null);
    }

    RenderOutputLifecycleServiceImpl(ICreationAssetService assetService, CreationAssetMapper assetMapper,
                                     CreationProjectMapper projectMapper, AiTaskMapper taskMapper,
                                     AiTaskExecutionMapper executionMapper, AiTaskAttemptMapper attemptMapper,
                                     ObjectProvider<OssClient> ossClientProvider) {
        this(assetService, assetMapper, projectMapper, taskMapper, executionMapper, attemptMapper,
            (TransactionTemplate) null, ossClientProvider);
    }

    @Autowired
    public RenderOutputLifecycleServiceImpl(ICreationAssetService assetService, CreationAssetMapper assetMapper,
                                             CreationProjectMapper projectMapper, AiTaskMapper taskMapper,
                                             AiTaskExecutionMapper executionMapper, AiTaskAttemptMapper attemptMapper,
                                             PlatformTransactionManager transactionManager,
                                             @Qualifier("aiVideoOssClient") ObjectProvider<OssClient> ossClientProvider) {
        this(assetService, assetMapper, projectMapper, taskMapper, executionMapper, attemptMapper,
            new TransactionTemplate(transactionManager), ossClientProvider);
    }

    private RenderOutputLifecycleServiceImpl(ICreationAssetService assetService, CreationAssetMapper assetMapper,
                                              CreationProjectMapper projectMapper, AiTaskMapper taskMapper,
                                              AiTaskExecutionMapper executionMapper, AiTaskAttemptMapper attemptMapper,
                                              TransactionTemplate transactionTemplate,
                                              ObjectProvider<OssClient> ossClientProvider) {
        this.assetService = Objects.requireNonNull(assetService, "assetService");
        this.assetMapper = Objects.requireNonNull(assetMapper, "assetMapper");
        this.projectMapper = Objects.requireNonNull(projectMapper, "projectMapper");
        this.taskMapper = Objects.requireNonNull(taskMapper, "taskMapper");
        this.executionMapper = Objects.requireNonNull(executionMapper, "executionMapper");
        this.attemptMapper = Objects.requireNonNull(attemptMapper, "attemptMapper");
        this.transactionTemplate = transactionTemplate;
        this.ossClientProvider = ossClientProvider;
    }

    @Override
    public PendingRenderOutputDTO registerPendingOutput(AiTaskLeaseDTO lease, String outputConfigDigest) {
        long actorId = actorId(lease);
        if (!LOWER_HEX.matcher(nullToEmpty(outputConfigDigest)).matches()) {
            throw renderUnavailable("输出配置摘要无效");
        }
        return inTransaction(actorId, () -> {
            LeaseState state = loadLiveLeaseState(actorId, lease, Instant.now());
            if (state == null || !AiTaskType.TIMELINE_RENDER.value().equals(state.task().getTaskType())
                || !Objects.equals(outputConfigDigest, state.execution().getOutputConfigDigest())
                || state.execution().getInputVersionId() == null) {
                throw renderUnavailable("渲染租约已失效");
            }
            return assetService.registerPendingRenderOutput(actorId, new RegisterPendingRenderOutputDTO(
                Long.toString(state.task().getTaskId()), Long.toString(state.execution().getInputVersionId()),
                outputConfigDigest, "render-output-" + state.task().getTaskId()));
        });
    }

    @Override
    public boolean storeAndComplete(AiTaskLeaseDTO lease, PendingRenderOutputDTO pending,
                                    TimelineRenderOutputHandle output, Instant now) {
        if (pending == null || output == null || now == null) {
            throw renderUnavailable("渲染成品无效");
        }
        long actorId = actorId(lease);
        try (output) {
            LeaseState beforeUpload = loadLiveLeaseState(actorId, lease, now);
            if (beforeUpload == null || !AiTaskType.TIMELINE_RENDER.value().equals(beforeUpload.task().getTaskType())) {
                return false;
            }
            CreationAsset pendingAsset = requirePendingAsset(actorId, pending, beforeUpload);
            TimelineRenderResultDTO metadata = requireMetadata(output.metadata());
            String actualDigest = uploadAndDigest(pendingAsset.getStorageKey(), output.stream(), metadata.fileSize(),
                metadata.sha256());
            if (!metadata.sha256().equals(actualDigest)) {
                throw renderUnavailable("渲染成品摘要不匹配");
            }
            Instant finalizedAt = Instant.now();
            return inTransaction(actorId, () -> finalizeReady(actorId, lease, pending, metadata, finalizedAt));
        } catch (IOException exception) {
            throw renderUnavailable("渲染成品关闭失败");
        }
    }

    @Override
    public int compensatePendingOutputs(Instant olderThan, int limit) {
        if (olderThan == null || limit < 1 || limit > 100) {
            throw renderUnavailable("成品补偿参数无效");
        }
        int compensated = 0;
        for (PendingRenderOutputDTO pending : assetService.findCompensatablePending(olderThan, limit)) {
            long actorId = ownerForPending(pending);
            if (actorId <= 0 || hasDurableReference(actorId, pending.assetId())) {
                continue;
            }
            assetService.markPendingRenderFailed(actorId, new RenderOutputFailureDTO(pending.assetId(), pending.taskId(),
                "STALE_PENDING_OUTPUT", "渲染成品等待超时"));
            compensated++;
        }
        return compensated;
    }

    private boolean finalizeReady(long actorId, AiTaskLeaseDTO lease, PendingRenderOutputDTO pending,
                                  TimelineRenderResultDTO metadata, Instant now) {
        LeaseState state = loadLiveLeaseState(actorId, lease, now);
        if (state == null || Boolean.TRUE.equals(state.task().getCancelRequested())) {
            return false;
        }
        CreationAsset asset = requirePendingAsset(actorId, pending, state);
        long nextExecutionVersion = number(state.execution().getRowVersion()) + 1L;
        int assetUpdated = updateAsset(actorId, asset, metadata);
        if (assetUpdated != 1) {
            throw new LeaseLostException();
        }
        int executionUpdated = updateExecution(actorId, state.execution(), lease, asset.getAssetId(), nextExecutionVersion, now);
        if (executionUpdated != 1) {
            throw new LeaseLostException();
        }
        if (lease.getAttemptId() != null && !finishAttempt(actorId, lease, now)) {
            throw new LeaseLostException();
        }
        long nextTaskVersion = number(state.task().getRowVersion()) + 1L;
        int taskUpdated = updateTask(actorId, state.task(), state.execution(), asset.getAssetId(), nextTaskVersion, now);
        if (taskUpdated != 1) {
            throw new LeaseLostException();
        }
        int projectUpdated = updateProject(actorId, state.task(), asset.getAssetId());
        if (projectUpdated != 1) {
            throw new LeaseLostException();
        }
        return true;
    }

    private CreationAsset requirePendingAsset(long actorId, PendingRenderOutputDTO pending, LeaseState state) {
        long assetId = parsePositiveId(pending.assetId(), "成品素材不存在");
        CreationAsset asset = assetMapper.selectOne(new LambdaQueryWrapper<CreationAsset>()
            .eq(CreationAsset::getAssetId, assetId)
            .eq(CreationAsset::getOwnerUserId, actorId)
            .eq(CreationAsset::getUsageOrigin, RENDER_OUTPUT)
            .eq(CreationAsset::getSourceRefId, state.task().getTaskId())
            .eq(CreationAsset::getAssetStatus, PENDING)
            .eq(CreationAsset::getDelFlag, "0"));
        if (asset == null || !Objects.equals(pending.taskId(), Long.toString(state.task().getTaskId()))
            || !Objects.equals(pending.inputVersionId(), Long.toString(state.execution().getInputVersionId()))
            || !Objects.equals(pending.outputConfigDigest(), state.execution().getOutputConfigDigest())) {
            throw renderUnavailable("待处理成品不存在");
        }
        return asset;
    }

    private int updateAsset(long actorId, CreationAsset asset, TimelineRenderResultDTO metadata) {
        CreationAsset update = new CreationAsset();
        update.setUpdateBy(actorId);
        update.setAssetStatus(READY);
        update.setMimeType("video/mp4");
        update.setSha256(metadata.sha256());
        update.setSizeBytes(metadata.fileSize());
        update.setDurationMs(metadata.durationMs());
        update.setWidth(metadata.width());
        update.setHeight(metadata.height());
        update.setHasVideoStream(true);
        update.setHasAudioStream(true);
        return assetMapper.update(update, new LambdaUpdateWrapper<CreationAsset>()
            .eq(CreationAsset::getAssetId, asset.getAssetId())
            .eq(CreationAsset::getOwnerUserId, actorId)
            .eq(CreationAsset::getAssetStatus, PENDING)
            .eq(CreationAsset::getUsageOrigin, RENDER_OUTPUT)
            .eq(CreationAsset::getDelFlag, "0")
            .set(CreationAsset::getAssetStatus, READY)
            .set(CreationAsset::getMimeType, "video/mp4")
            .set(CreationAsset::getSha256, metadata.sha256())
            .set(CreationAsset::getSizeBytes, metadata.fileSize())
            .set(CreationAsset::getDurationMs, metadata.durationMs())
            .set(CreationAsset::getWidth, metadata.width())
            .set(CreationAsset::getHeight, metadata.height())
            .set(CreationAsset::getHasVideoStream, true)
            .set(CreationAsset::getHasAudioStream, true));
    }

    private int updateExecution(long actorId, AiTaskExecution execution, AiTaskLeaseDTO lease, long assetId,
                                long nextVersion, Instant now) {
        AiTaskExecution update = new AiTaskExecution();
        update.setUpdateBy(actorId);
        update.setExecutionStatus(AiTaskExecutionStatus.SUCCESS.value());
        update.setStage(AiTaskStage.COMPLETED.value());
        update.setProgressPercent(100);
        update.setResultAssetId(assetId);
        update.setLeaseOwner(null);
        update.setLeaseToken(null);
        update.setLeaseExpiresAt(null);
        return executionMapper.update(update, new LambdaUpdateWrapper<AiTaskExecution>()
            .eq(AiTaskExecution::getTaskExecutionId, execution.getTaskExecutionId())
            .eq(AiTaskExecution::getOwnerUserId, actorId)
            .eq(AiTaskExecution::getExecutionStatus, AiTaskExecutionStatus.RUNNING.value())
            .eq(AiTaskExecution::getLeaseToken, lease.getLeaseToken())
            .eq(AiTaskExecution::getRowVersion, number(execution.getRowVersion()))
            .gt(AiTaskExecution::getLeaseExpiresAt, local(now))
            .set(AiTaskExecution::getExecutionStatus, AiTaskExecutionStatus.SUCCESS.value())
            .set(AiTaskExecution::getStage, AiTaskStage.COMPLETED.value())
            .set(AiTaskExecution::getProgressPercent, 100)
            .set(AiTaskExecution::getResultAssetId, assetId)
            .set(AiTaskExecution::getLeaseOwner, null)
            .set(AiTaskExecution::getLeaseToken, null)
            .set(AiTaskExecution::getLeaseExpiresAt, null)
            .set(AiTaskExecution::getRowVersion, nextVersion));
    }

    private boolean finishAttempt(long actorId, AiTaskLeaseDTO lease, Instant now) {
        long attemptId = parsePositiveId(lease.getAttemptId(), "任务尝试不存在");
        AiTaskAttempt attempt = attemptMapper.selectOne(new LambdaQueryWrapper<AiTaskAttempt>()
            .eq(AiTaskAttempt::getTaskAttemptId, attemptId)
            .eq(AiTaskAttempt::getOwnerUserId, actorId)
            .eq(AiTaskAttempt::getTaskExecutionId, parsePositiveId(lease.getExecutionId(), "任务执行不存在"))
            .eq(AiTaskAttempt::getAttemptStatus, AiTaskAttemptStatus.RUNNING.value()));
        if (attempt == null) {
            return false;
        }
        AiTaskAttempt update = new AiTaskAttempt();
        update.setUpdateBy(actorId);
        update.setAttemptStatus(AiTaskAttemptStatus.SUCCESS.value());
        update.setFinishedAt(local(now));
        update.setExitCategory("success");
        return attemptMapper.update(update, new LambdaUpdateWrapper<AiTaskAttempt>()
            .eq(AiTaskAttempt::getTaskAttemptId, attemptId)
            .eq(AiTaskAttempt::getOwnerUserId, actorId)
            .eq(AiTaskAttempt::getAttemptStatus, AiTaskAttemptStatus.RUNNING.value())
            .eq(AiTaskAttempt::getRowVersion, number(attempt.getRowVersion()))
            .set(AiTaskAttempt::getAttemptStatus, AiTaskAttemptStatus.SUCCESS.value())
            .set(AiTaskAttempt::getFinishedAt, local(now))
            .set(AiTaskAttempt::getExitCategory, "success")
            .set(AiTaskAttempt::getRowVersion, number(attempt.getRowVersion()) + 1L)) == 1;
    }

    private int updateTask(long actorId, AiTask task, AiTaskExecution execution, long assetId, long nextVersion,
                           Instant now) {
        AiTask update = new AiTask();
        update.setUpdateBy(actorId);
        update.setTaskStatus(AiTaskStatus.SUCCESS.value());
        update.setStage(AiTaskStage.COMPLETED.value());
        update.setProgressPercent(100);
        update.setResultAssetId(assetId);
        update.setResultSchemaVersion(null);
        update.setResultPayloadJson(null);
        update.setErrorCode(null);
        update.setErrorSummary(null);
        update.setFinishedAt(local(now));
        return taskMapper.update(update, new LambdaUpdateWrapper<AiTask>()
            .eq(AiTask::getTaskId, task.getTaskId())
            .eq(AiTask::getOwnerUserId, actorId)
            .eq(AiTask::getTaskStatus, AiTaskStatus.RUNNING.value())
            .eq(AiTask::getActiveExecutionId, execution.getTaskExecutionId())
            .eq(AiTask::getRowVersion, number(task.getRowVersion()))
            .eq(AiTask::getCancelRequested, false)
            .set(AiTask::getTaskStatus, AiTaskStatus.SUCCESS.value())
            .set(AiTask::getStage, AiTaskStage.COMPLETED.value())
            .set(AiTask::getProgressPercent, 100)
            .set(AiTask::getResultAssetId, assetId)
            .set(AiTask::getResultSchemaVersion, null)
            .set(AiTask::getResultPayloadJson, null)
            .set(AiTask::getErrorCode, null)
            .set(AiTask::getErrorSummary, null)
            .set(AiTask::getFinishedAt, local(now))
            .set(AiTask::getRowVersion, nextVersion));
    }

    private int updateProject(long actorId, AiTask task, long assetId) {
        if (task.getResourceId() == null) {
            return 0;
        }
        CreationProject update = new CreationProject();
        update.setUpdateBy(actorId);
        update.setCurrentOutputAssetId(assetId);
        update.setProjectStatus("ready");
        return projectMapper.update(update, new LambdaUpdateWrapper<CreationProject>()
            .eq(CreationProject::getProjectId, task.getResourceId())
            .eq(CreationProject::getOwnerUserId, actorId)
            .eq(CreationProject::getDelFlag, "0")
            .in(CreationProject::getProjectStatus, List.of("editing", "ready", "rendering"))
            .set(CreationProject::getCurrentOutputAssetId, assetId)
            .set(CreationProject::getProjectStatus, "ready"));
    }

    private LeaseState loadLeaseState(long actorId, AiTaskLeaseDTO lease) {
        if (lease == null || !notBlank(lease.getLeaseToken())) {
            return null;
        }
        long taskId = parsePositiveId(lease.getTaskId(), "任务不存在");
        long executionId = parsePositiveId(lease.getExecutionId(), "任务执行不存在");
        AiTask task = taskMapper.selectOne(new LambdaQueryWrapper<AiTask>()
            .eq(AiTask::getTaskId, taskId)
            .eq(AiTask::getOwnerUserId, actorId));
        AiTaskExecution execution = executionMapper.selectOne(new LambdaQueryWrapper<AiTaskExecution>()
            .eq(AiTaskExecution::getTaskExecutionId, executionId)
            .eq(AiTaskExecution::getOwnerUserId, actorId));
        if (task == null || execution == null || !AiTaskType.TIMELINE_RENDER.value().equals(task.getTaskType())
            || !Objects.equals(task.getTaskId(), execution.getTaskId())
            || !Objects.equals(task.getActiveExecutionId(), executionId)
            || !AiTaskStatus.RUNNING.value().equals(task.getTaskStatus())
            || !AiTaskExecutionStatus.RUNNING.value().equals(execution.getExecutionStatus())
            || !APP_USER.equals(task.getActorType()) || !Objects.equals(task.getActorId(), actorId)
            || !Objects.equals(execution.getLeaseToken(), lease.getLeaseToken())
            || number(execution.getRowVersion()) != lease.getRowVersion()) {
            return null;
        }
        return new LeaseState(task, execution);
    }

    private LeaseState loadLiveLeaseState(long actorId, AiTaskLeaseDTO lease, Instant now) {
        LeaseState state = loadLeaseState(actorId, lease);
        if (state == null || now == null || state.execution().getLeaseExpiresAt() == null
            || !state.execution().getLeaseExpiresAt().isAfter(local(now))) {
            return null;
        }
        return state;
    }

    private TimelineRenderResultDTO requireMetadata(TimelineRenderResultDTO metadata) {
        if (metadata == null || !"video/mp4".equals(normalizedMediaType(metadata.contentType()))
            || metadata.fileSize() <= 0 || metadata.durationMs() <= 0 || metadata.width() <= 0
            || metadata.height() <= 0 || metadata.frameRate() <= 0
            || !LOWER_HEX.matcher(nullToEmpty(metadata.sha256())).matches()) {
            throw renderUnavailable("渲染成品媒体元数据无效");
        }
        return metadata;
    }

    private String uploadAndDigest(String storageKey, InputStream input, long expectedSize, String expectedSha256) {
        if (!notBlank(storageKey) || input == null) {
            throw renderUnavailable("渲染成品输出流无效");
        }
        try {
            return ImmutableRenderObjectStore.uploadOrReuse(requireOssClient(), storageKey, input, expectedSize,
                expectedSha256);
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof ServiceException serviceException) {
                throw serviceException;
            }
            throw renderUnavailable("渲染成品上传失败");
        }
    }

    private OssClient requireOssClient() {
        try {
            OssClient client = ossClientProvider == null ? null : ossClientProvider.getIfAvailable();
            if (client != null) {
                return client;
            }
        } catch (RuntimeException ignored) {
            // Keep configuration and provider details inside the process boundary.
        }
        throw renderUnavailable("VideoOps 对象存储未启用");
    }

    private boolean hasDurableReference(long actorId, String assetId) {
        long parsed = parsePositiveId(assetId, "成品素材不存在");
        return taskMapper.selectCount(new LambdaQueryWrapper<AiTask>()
            .eq(AiTask::getOwnerUserId, actorId)
            .eq(AiTask::getResultAssetId, parsed)) > 0
            || executionMapper.selectCount(new LambdaQueryWrapper<AiTaskExecution>()
            .eq(AiTaskExecution::getOwnerUserId, actorId)
            .eq(AiTaskExecution::getResultAssetId, parsed)) > 0
            || projectMapper.selectCount(new LambdaQueryWrapper<CreationProject>()
            .eq(CreationProject::getOwnerUserId, actorId)
            .eq(CreationProject::getCurrentOutputAssetId, parsed)
            .eq(CreationProject::getDelFlag, "0")) > 0;
    }

    private long ownerForPending(PendingRenderOutputDTO pending) {
        if (pending == null) {
            return 0L;
        }
        long taskId;
        try {
            taskId = parsePositiveId(pending.taskId(), "任务不存在");
        } catch (ServiceException exception) {
            return 0L;
        }
        AiTask task = taskMapper.selectOne(new LambdaQueryWrapper<AiTask>()
            .eq(AiTask::getTaskId, taskId));
        return task == null || task.getOwnerUserId() == null ? 0L : task.getOwnerUserId();
    }

    private long actorId(AiTaskLeaseDTO lease) {
        if (lease == null || !POSITIVE_ID.matcher(nullToEmpty(lease.getActorId())).matches()) {
            throw renderUnavailable("渲染租约无效");
        }
        return parsePositiveId(lease.getActorId(), "渲染租约无效");
    }

    private <T> T inTransaction(long actorId, java.util.function.Supplier<T> action) {
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

    private long parsePositiveId(String value, String message) {
        if (!POSITIVE_ID.matcher(nullToEmpty(value)).matches()) {
            throw renderUnavailable(message);
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw renderUnavailable(message);
        }
    }

    private long number(Long value) {
        return value == null ? 0L : value;
    }

    private LocalDateTime local(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private String normalizedMediaType(String value) {
        if (value == null) {
            return "";
        }
        int separator = value.indexOf(';');
        return (separator < 0 ? value : value.substring(0, separator)).trim().toLowerCase(java.util.Locale.ROOT);
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private ServiceException renderUnavailable(String message) {
        return new ServiceException(message, TimelineErrorCodes.TIMELINE_RENDER_UNAVAILABLE);
    }

    private record LeaseState(AiTask task, AiTaskExecution execution) {
    }

    private static final class LeaseLostException extends RuntimeException {
    }
}
