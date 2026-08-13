package org.dromara.aivideo.task.service.impl;

import org.dromara.aivideo.creation.dto.PendingRenderOutputDTO;
import org.dromara.aivideo.creation.service.CreationMediaHandle;
import org.dromara.aivideo.creation.service.ICreationAssetService;
import org.dromara.aivideo.creation.service.IRenderOutputLifecycleService;
import org.dromara.aivideo.task.dto.AiTaskCompletionDTO;
import org.dromara.aivideo.task.dto.AiTaskAccessScopeDTO;
import org.dromara.aivideo.task.dto.AiTaskActorDTO;
import org.dromara.aivideo.task.dto.AiTaskDTO;
import org.dromara.aivideo.task.dto.AiTaskDispatchResultDTO;
import org.dromara.aivideo.task.dto.AiTaskFancyTextPayloadDTO;
import org.dromara.aivideo.task.dto.AiTaskFancyTextResultPayloadDTO;
import org.dromara.aivideo.task.dto.AiTaskImagePromptPayloadDTO;
import org.dromara.aivideo.task.dto.AiTaskImagePromptResultPayloadDTO;
import org.dromara.aivideo.task.dto.AiTaskLeaseDTO;
import org.dromara.aivideo.task.dto.AiTaskProgressDTO;
import org.dromara.aivideo.task.dto.AiTaskQueryDTO;
import org.dromara.aivideo.task.dto.AiTaskRenderPayloadDTO;
import org.dromara.aivideo.task.dto.AiTaskResultPayloadDTO;
import org.dromara.aivideo.task.dto.AiTaskSubtitleAlignmentPayloadDTO;
import org.dromara.aivideo.task.dto.AiTaskSubtitleAlignmentResultPayloadDTO;
import org.dromara.aivideo.task.dto.AiTaskSummaryDTO;
import org.dromara.aivideo.task.dto.CreateFreeAiTaskDTO;
import org.dromara.aivideo.task.dto.CreateWorkflowAiTaskDTO;
import org.dromara.aivideo.task.dto.WorkflowAiTaskPayloadDTO;
import org.dromara.aivideo.task.dto.RetryAiTaskDTO;
import org.dromara.aivideo.task.enums.AiTaskResourceType;
import org.dromara.aivideo.task.enums.AiTaskType;
import org.dromara.aivideo.task.service.IAiTaskService;
import org.dromara.aivideo.task.service.IAiTaskTransactionService;
import org.dromara.aivideo.timeline.dto.TimelineProgressDTO;
import org.dromara.aivideo.timeline.dto.TimelineRenderCommandDTO;
import org.dromara.aivideo.timeline.dto.TimelineAssetReferenceDTO;
import org.dromara.aivideo.timeline.exception.TimelineExecutionException;
import org.dromara.aivideo.timeline.service.ITimelineAiSuggestionService;
import org.dromara.aivideo.timeline.service.ITimelineMediaRenderService;
import org.dromara.aivideo.timeline.service.TimelineRenderOutputHandle;
import org.dromara.aivideo.timeline.service.TimelineTaskProgressListener;
import org.dromara.aivideo.workflow.service.IWorkflowTaskExecutionService;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/** Public task facade: short transactions live below; C0 AI/media calls live outside of them. */
@Service
public class AiTaskServiceImpl implements IAiTaskService {

    private static final Logger LOG = LoggerFactory.getLogger(AiTaskServiceImpl.class);
    private static final String WORKER_FAILURE = "WORKER_FAILED";

    private final IAiTaskTransactionService transactionService;
    private final ITimelineAiSuggestionService suggestionService;
    private final ITimelineMediaRenderService mediaRenderService;
    private final ICreationAssetService assetService;
    private final IRenderOutputLifecycleService renderOutputLifecycleService;
    private final IWorkflowTaskExecutionService workflowTaskExecutionService;

    AiTaskServiceImpl(IAiTaskTransactionService transactionService,
                      ITimelineAiSuggestionService suggestionService,
                      ITimelineMediaRenderService mediaRenderService,
                      ICreationAssetService assetService,
                      IRenderOutputLifecycleService renderOutputLifecycleService) {
        this(transactionService, suggestionService, mediaRenderService, assetService, renderOutputLifecycleService, null);
    }

    AiTaskServiceImpl(IAiTaskTransactionService transactionService,
                      ITimelineAiSuggestionService suggestionService,
                      ITimelineMediaRenderService mediaRenderService,
                      ICreationAssetService assetService,
                      IRenderOutputLifecycleService renderOutputLifecycleService,
                      IWorkflowTaskExecutionService workflowTaskExecutionService) {
        this.transactionService = transactionService;
        this.suggestionService = suggestionService;
        this.mediaRenderService = mediaRenderService;
        this.assetService = assetService;
        this.renderOutputLifecycleService = renderOutputLifecycleService;
        this.workflowTaskExecutionService = workflowTaskExecutionService;
    }

    @Autowired
    public AiTaskServiceImpl(IAiTaskTransactionService transactionService,
                             ObjectProvider<ITimelineAiSuggestionService> suggestionServiceProvider,
                             ObjectProvider<ITimelineMediaRenderService> mediaRenderServiceProvider,
                             ObjectProvider<IWorkflowTaskExecutionService> workflowTaskExecutionServiceProvider,
                             ICreationAssetService assetService,
                             IRenderOutputLifecycleService renderOutputLifecycleService) {
        this(transactionService, suggestionServiceProvider.getIfAvailable(), mediaRenderServiceProvider.getIfAvailable(),
            assetService, renderOutputLifecycleService, workflowTaskExecutionServiceProvider.getIfAvailable());
    }

    @Override
    public AiTaskDTO createFreeTask(long actorId, CreateFreeAiTaskDTO command) {
        return transactionService.createFreeTask(actorId, command);
    }

    @Override
    public AiTaskDTO createWorkflowTask(AiTaskActorDTO actor, CreateWorkflowAiTaskDTO command) {
        return transactionService.createWorkflowTask(actor, command);
    }

    @Override
    public AiTaskDTO getOwned(long actorId, String taskId) {
        return transactionService.getOwned(actorId, taskId);
    }

    @Override
    public AiTaskDTO getOwned(AiTaskAccessScopeDTO scope, String taskId) {
        return transactionService.getOwned(scope, taskId);
    }

    @Override
    public PageResult<AiTaskSummaryDTO> pageOwned(long actorId, AiTaskQueryDTO query, PageQuery pageQuery) {
        return transactionService.pageOwned(actorId, query, pageQuery);
    }

    @Override
    public PageResult<AiTaskSummaryDTO> pageOwned(AiTaskAccessScopeDTO scope, AiTaskQueryDTO query,
                                                  PageQuery pageQuery) {
        return transactionService.pageOwned(scope, query, pageQuery);
    }

    @Override
    public AiTaskDTO requestCancellation(long actorId, String taskId, String cancellationKey) {
        return transactionService.requestCancellation(actorId, taskId, cancellationKey);
    }

    @Override
    public AiTaskDTO requestCancellation(AiTaskAccessScopeDTO scope, String taskId, String cancellationKey) {
        return transactionService.requestCancellation(scope, taskId, cancellationKey);
    }

    @Override
    public AiTaskDTO retryOwned(long actorId, RetryAiTaskDTO command) {
        return transactionService.retryOwned(actorId, command);
    }

    @Override
    public AiTaskDispatchResultDTO dispatchNext(String workerId, int perUserConcurrencyLimit,
                                                int systemConcurrencyLimit) {
        if (perUserConcurrencyLimit < 1 || perUserConcurrencyLimit > 100
            || systemConcurrencyLimit < 1 || systemConcurrencyLimit > 100
            || perUserConcurrencyLimit > systemConcurrencyLimit) {
            throw new ServiceException("任务调度并发上限无效");
        }
        AiTaskLeaseDTO claimed = transactionService.claimNext(workerId, perUserConcurrencyLimit,
            systemConcurrencyLimit);
        if (claimed == null) {
            return new AiTaskDispatchResultDTO("none", null, null);
        }
        return dispatchClaimedTask(claimed, false);
    }

    @Override
    public AiTaskLeaseDTO claimNextWorkflow(String workerId, int concurrencyLimit) {
        if (concurrencyLimit < 1 || concurrencyLimit > 100) {
            throw new ServiceException("workflow concurrency limit is invalid");
        }
        return transactionService.claimNextWorkflow(workerId, concurrencyLimit);
    }

    @Override
    public AiTaskDispatchResultDTO dispatchClaimedWorkflow(AiTaskLeaseDTO lease) {
        if (lease == null) {
            return new AiTaskDispatchResultDTO("none", null, null);
        }
        return dispatchClaimedTask(lease, true);
    }

    @Override
    public boolean releaseClaimedWorkflow(AiTaskLeaseDTO lease) {
        return transactionService.releaseClaimedWorkflow(lease);
    }

    private AiTaskDispatchResultDTO dispatchClaimedTask(AiTaskLeaseDTO claimed, boolean workflowOnly) {
        IAiTaskTransactionService.DispatchContext context = transactionService.loadDispatchContext(claimed);
        if (context == null) {
            return outcome("lease_lost", claimed);
        }
        if (transactionService.cancellationRequested(claimed)) {
            return cancelOrLeaseLost(claimed);
        }
        AtomicReference<AiTaskLeaseDTO> activeLease = new AtomicReference<>(claimed);
        List<CreationMediaHandle> openedInputs = new ArrayList<>();
        try {
            if (context.taskType() == AiTaskType.WORKFLOW_TEMPLATE_GENERATE
                || context.taskType() == AiTaskType.WORKFLOW_TEMPLATE_TEST) {
                if (!(context.payload() instanceof WorkflowAiTaskPayloadDTO payload)) {
                    throw new ServiceException("工作流任务事实损坏");
                }
                return requireWorkflowTaskExecutionService().dispatch(activeLease.get(), payload);
            }
            if (workflowOnly) {
                throw new ServiceException("non-workflow task claimed by RunningHub dispatcher");
            }
            AiTaskLeaseDTO started = transactionService.beginAttempt(activeLease.get(), Instant.now());
            if (started == null) {
                return outcome("lease_lost", activeLease.get());
            }
            activeLease.set(started);
            PendingRenderOutputDTO pending = null;
            if (context.taskType() == AiTaskType.TIMELINE_RENDER) {
                pending = requireRenderLifecycle().registerPendingOutput(activeLease.get(), context.outputConfigDigest());
                openedInputs.addAll(openRenderInputs(context, activeLease.get()));
            }
            TimelineTaskProgressListener progress = progressListener(activeLease);
            if (context.taskType() == AiTaskType.TIMELINE_RENDER) {
                return dispatchRender(context, activeLease, progress, openedInputs, pending);
            }
            AiTaskResultPayloadDTO result = executeSuggestion(context, activeLease, progress);
            AiTaskLeaseDTO current = activeLease.get();
            if (transactionService.cancellationRequested(current)) {
                return cancelOrLeaseLost(current);
            }
            boolean completed = transactionService.complete(current, new AiTaskCompletionDTO(current.getExecutionId(),
                current.getLeaseToken(), null, null, null, result, current.getRowVersion(), true, false), Instant.now());
            return outcome(completed ? "completed" : "lease_lost", current);
        } catch (LeaseLostException ignored) {
            return outcome("lease_lost", activeLease.get());
        } catch (RuntimeException exception) {
            AiTaskLeaseDTO current = activeLease.get();
            String failureCode = exception instanceof TimelineExecutionException timelineFailure
                ? timelineFailure.code().name() : WORKER_FAILURE;
            String safeMessage = exception instanceof TimelineExecutionException timelineFailure
                ? timelineFailure.getMessage() : "timeline task execution failed";
            LOG.warn("Timeline task dispatch failed: taskId={}, executionId={}, failureType={}, failureCode={}, safeMessage={}",
                current.getTaskId(), current.getExecutionId(), rootCauseType(exception), failureCode, safeMessage);
            if (transactionService.cancellationRequested(current)) {
                LOG.warn("Timeline task failure observed a lost or cancelled lease: taskId={}, executionId={}, rowVersion={}",
                    current.getTaskId(), current.getExecutionId(), current.getRowVersion());
                return cancelOrLeaseLost(current);
            }
            boolean completed = transactionService.complete(current, new AiTaskCompletionDTO(current.getExecutionId(),
                current.getLeaseToken(), null, WORKER_FAILURE, "任务执行失败", null, current.getRowVersion(), false,
                false), Instant.now());
            if (!completed) {
                LOG.warn("Timeline task failure could not be persisted: taskId={}, executionId={}, rowVersion={}",
                    current.getTaskId(), current.getExecutionId(), current.getRowVersion());
            }
            return outcome(completed ? "failed" : "lease_lost", current);
        } finally {
            closeInputs(openedInputs);
        }
    }

    @Override
    public int recoverExpired(Instant now, int limit) {
        IWorkflowTaskExecutionService workflow = workflowTaskExecutionService;
        int workflowRecovered = workflow == null ? 0 : workflow.recoverExpired(now, limit);
        return workflowRecovered + transactionService.recoverExpired(now, Math.max(1, limit - workflowRecovered));
    }

    @Override
    public int compensatePendingOutputs(Instant now, int limit) {
        return requireRenderLifecycle().compensatePendingOutputs(now, limit);
    }

    private AiTaskDispatchResultDTO dispatchRender(IAiTaskTransactionService.DispatchContext context,
                                                   AtomicReference<AiTaskLeaseDTO> activeLease,
                                                   TimelineTaskProgressListener progress,
                                                   List<CreationMediaHandle> inputs,
                                                   PendingRenderOutputDTO pending) {
        if (!(context.payload() instanceof AiTaskRenderPayloadDTO payload)) {
            throw new ServiceException("渲染任务事实损坏");
        }
        ITimelineMediaRenderService renderer = requireMediaRenderService();
        AiTaskLeaseDTO current = activeLease.get();
        TimelineRenderCommandDTO source = payload.command();
        TimelineRenderCommandDTO command = new TimelineRenderCommandDTO(current.getTaskId(), current.getExecutionId(),
            current.getAttemptId(), current.getInputVersionId(), source.fontRegistryVersion(), source.fontRegistrySha256(),
            source.timeline(), source.outputConfig(), source.assets());
        TimelineRenderOutputHandle output = renderer.render(command, List.copyOf(inputs), progress,
            () -> transactionService.cancellationRequested(activeLease.get()));
        if (transactionService.cancellationRequested(activeLease.get())) {
            closeOutput(output);
            return cancelOrLeaseLost(activeLease.get());
        }
        boolean completed = requireRenderLifecycle().storeAndComplete(activeLease.get(), pending, output, Instant.now());
        return outcome(completed ? "completed" : "lease_lost", activeLease.get());
    }

    private AiTaskResultPayloadDTO executeSuggestion(IAiTaskTransactionService.DispatchContext context,
                                                      AtomicReference<AiTaskLeaseDTO> activeLease,
                                                      TimelineTaskProgressListener progress) {
        ITimelineAiSuggestionService suggestions = requireSuggestionService();
        return switch (context.taskType()) {
            case TIMELINE_IMAGE_PROMPT_GENERATE -> {
                if (!(context.payload() instanceof AiTaskImagePromptPayloadDTO payload)) {
                    throw new ServiceException("图像提示词任务事实损坏");
                }
                yield new AiTaskImagePromptResultPayloadDTO(suggestions.generateImagePrompt(payload.command(), progress,
                    () -> transactionService.cancellationRequested(activeLease.get())));
            }
            case TIMELINE_FANCY_TEXT_SUGGEST -> {
                if (!(context.payload() instanceof AiTaskFancyTextPayloadDTO payload)) {
                    throw new ServiceException("花字建议任务事实损坏");
                }
                yield new AiTaskFancyTextResultPayloadDTO(suggestions.suggestFancyText(payload.command(), progress,
                    () -> transactionService.cancellationRequested(activeLease.get())));
            }
            case TIMELINE_SUBTITLE_ALIGN -> executeSubtitle(context, activeLease, progress, suggestions);
            case TIMELINE_RENDER -> throw new IllegalStateException("render is handled separately");
            case WORKFLOW_TEMPLATE_GENERATE, WORKFLOW_TEMPLATE_TEST ->
                throw new ServiceException("workflow task dispatcher is unavailable");
        };
    }

    private IWorkflowTaskExecutionService requireWorkflowTaskExecutionService() {
        if (workflowTaskExecutionService == null) {
            throw new ServiceException("工作流任务执行器不可用");
        }
        return workflowTaskExecutionService;
    }

    private AiTaskResultPayloadDTO executeSubtitle(IAiTaskTransactionService.DispatchContext context,
                                                   AtomicReference<AiTaskLeaseDTO> activeLease,
                                                   TimelineTaskProgressListener progress,
                                                   ITimelineAiSuggestionService suggestions) {
        if (!(context.payload() instanceof AiTaskSubtitleAlignmentPayloadDTO payload)) {
            throw new ServiceException("字幕对齐任务事实损坏");
        }
        if (payload.command().trustedCues() != null && !payload.command().trustedCues().isEmpty()) {
            return new AiTaskSubtitleAlignmentResultPayloadDTO(suggestions.alignFromTrustedCues(payload.command(), progress,
                () -> transactionService.cancellationRequested(activeLease.get())));
        }
        try (CreationMediaHandle audio = requireAssetService().openOwnedMedia(context.ownerUserId(),
            payload.command().primaryAudioAssetId(), org.dromara.aivideo.timeline.enums.TimelineAssetUsageType.PRIMARY_AUDIO)) {
            return new AiTaskSubtitleAlignmentResultPayloadDTO(suggestions.alignFromAudio(payload.command(), audio, progress,
                () -> transactionService.cancellationRequested(activeLease.get())));
        } catch (IOException exception) {
            throw new ServiceException("字幕音频读取失败");
        }
    }

    private List<CreationMediaHandle> openRenderInputs(IAiTaskTransactionService.DispatchContext context,
                                                        AiTaskLeaseDTO lease) {
        if (!(context.payload() instanceof AiTaskRenderPayloadDTO payload)) {
            throw new ServiceException("渲染任务事实损坏");
        }
        List<CreationMediaHandle> handles = new ArrayList<>();
        try {
            List<TimelineAssetReferenceDTO> assets = payload.command().assets() == null ? List.of()
                : payload.command().assets();
            for (int index = 0; index < assets.size(); index++) {
                TimelineAssetReferenceDTO asset = assets.get(index);
                long startedAt = System.nanoTime();
                LOG.info("Timeline render input opening: taskId={}, executionId={}, assetId={}, usageType={}, ordinal={}/{}",
                    lease.getTaskId(), lease.getExecutionId(), asset.assetId(), asset.usageType(), index + 1, assets.size());
                handles.add(requireAssetService().openOwnedMedia(context.ownerUserId(), asset.assetId(), asset.usageType()));
                LOG.info("Timeline render input opened: taskId={}, executionId={}, assetId={}, elapsedMs={}",
                    lease.getTaskId(), lease.getExecutionId(), asset.assetId(),
                    java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt));
            }
            return handles;
        } catch (RuntimeException exception) {
            closeInputs(handles);
            throw exception;
        }
    }

    private static String rootCauseType(Throwable failure) {
        Throwable root = failure;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getClass().getName();
    }

    private TimelineTaskProgressListener progressListener(AtomicReference<AiTaskLeaseDTO> activeLease) {
        return (TimelineProgressDTO update) -> {
            AiTaskLeaseDTO current = activeLease.get();
            Instant now = Instant.now();
            AiTaskLeaseDTO renewed = transactionService.renew(current, now);
            if (renewed == null) {
                throw new LeaseLostException();
            }
            activeLease.set(renewed);
            AiTaskLeaseDTO next = transactionService.reportProgress(renewed, new AiTaskProgressDTO(
                renewed.getExecutionId(), renewed.getLeaseToken(), renewed.getRowVersion(), update.percent(), update.stage(),
                update.safeMessage()), now);
            if (next == null) {
                throw new LeaseLostException();
            }
            activeLease.set(next);
        };
    }

    private AiTaskDispatchResultDTO cancelOrLeaseLost(AiTaskLeaseDTO lease) {
        boolean cancelled = transactionService.cancel(lease, "任务已取消", Instant.now());
        return outcome(cancelled ? "cancelled" : "lease_lost", lease);
    }

    private AiTaskDispatchResultDTO outcome(String outcome, AiTaskLeaseDTO lease) {
        return new AiTaskDispatchResultDTO(outcome, lease.getTaskId(), lease.getExecutionId());
    }

    private ICreationAssetService requireAssetService() {
        if (assetService == null) {
            throw new ServiceException("创作素材服务不可用");
        }
        return assetService;
    }

    private IRenderOutputLifecycleService requireRenderLifecycle() {
        if (renderOutputLifecycleService == null) {
            throw new ServiceException("渲染成品服务不可用");
        }
        return renderOutputLifecycleService;
    }

    private ITimelineAiSuggestionService requireSuggestionService() {
        if (suggestionService == null) {
            throw new ServiceException("AI 建议服务未配置");
        }
        return suggestionService;
    }

    private ITimelineMediaRenderService requireMediaRenderService() {
        if (mediaRenderService == null) {
            throw new ServiceException("时间轴渲染服务未配置");
        }
        return mediaRenderService;
    }

    private void closeInputs(List<CreationMediaHandle> handles) {
        for (CreationMediaHandle handle : handles) {
            try {
                handle.close();
            } catch (IOException ignored) {
                // A terminal task state remains authoritative; cleanup is best effort.
            }
        }
    }

    private void closeOutput(TimelineRenderOutputHandle output) {
        if (output == null) {
            return;
        }
        try {
            output.close();
        } catch (IOException ignored) {
            // Cancellation is still durable even if the provider stream's close reports an I/O error.
        }
    }

    private static final class LeaseLostException extends RuntimeException {
    }
}
