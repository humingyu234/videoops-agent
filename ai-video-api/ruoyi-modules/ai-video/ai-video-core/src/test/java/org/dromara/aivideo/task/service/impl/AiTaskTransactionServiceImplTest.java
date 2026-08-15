package org.dromara.aivideo.task.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.dromara.aivideo.creation.domain.CreationProject;
import org.dromara.aivideo.creation.mapper.CreationProjectMapper;
import org.dromara.aivideo.task.domain.AiTask;
import org.dromara.aivideo.task.domain.AiTaskAttempt;
import org.dromara.aivideo.task.domain.AiTaskExecution;
import org.dromara.aivideo.task.dto.AiTaskCompletionDTO;
import org.dromara.aivideo.task.dto.AiTaskAccessScopeDTO;
import org.dromara.aivideo.task.dto.AiTaskDTO;
import org.dromara.aivideo.task.dto.AiTaskImagePromptPayloadDTO;
import org.dromara.aivideo.task.dto.AiTaskImagePromptResultPayloadDTO;
import org.dromara.aivideo.task.dto.AiTaskLeaseDTO;
import org.dromara.aivideo.task.dto.AiTaskProgressDTO;
import org.dromara.aivideo.task.dto.AiTaskRenderPayloadDTO;
import org.dromara.aivideo.task.dto.CreateFreeAiTaskDTO;
import org.dromara.aivideo.task.enums.AiTaskExecutionStatus;
import org.dromara.aivideo.task.enums.AiTaskResourceType;
import org.dromara.aivideo.task.enums.AiTaskStage;
import org.dromara.aivideo.task.enums.AiTaskStatus;
import org.dromara.aivideo.task.enums.AiTaskType;
import org.dromara.aivideo.task.mapper.AiTaskAttemptMapper;
import org.dromara.aivideo.task.mapper.AiTaskExecutionMapper;
import org.dromara.aivideo.task.mapper.AiTaskMapper;
import org.dromara.aivideo.timeline.domain.TimelineAssetRef;
import org.dromara.aivideo.timeline.domain.TimelineDraft;
import org.dromara.aivideo.timeline.domain.TimelineVersion;
import org.dromara.aivideo.timeline.dto.TimelineImagePromptCommandDTO;
import org.dromara.aivideo.timeline.dto.TimelineImagePromptResultDTO;
import org.dromara.aivideo.timeline.dto.TimelineOutputConfigDTO;
import org.dromara.aivideo.timeline.dto.TimelineRenderCommandDTO;
import org.dromara.aivideo.timeline.enums.TimelineAssetUsageType;
import org.dromara.aivideo.timeline.enums.TimelineOutputQuality;
import org.dromara.aivideo.timeline.mapper.TimelineAssetRefMapper;
import org.dromara.aivideo.timeline.mapper.TimelineDraftMapper;
import org.dromara.aivideo.timeline.mapper.TimelineVersionMapper;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("dev")
class AiTaskTransactionServiceImplTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Mock
    private AiTaskMapper taskMapper;
    @Mock
    private AiTaskExecutionMapper executionMapper;
    @Mock
    private AiTaskAttemptMapper attemptMapper;
    @Mock
    private CreationProjectMapper projectMapper;
    @Mock
    private TimelineDraftMapper draftMapper;
    @Mock
    private TimelineVersionMapper versionMapper;
    @Mock
    private TimelineAssetRefMapper assetRefMapper;

    @BeforeAll
    static void initializeLambdaMetadata() {
        initialize(AiTask.class);
        initialize(AiTaskExecution.class);
        initialize(org.dromara.aivideo.task.domain.AiTaskAttempt.class);
        initialize(CreationProject.class);
        initialize(TimelineDraft.class);
        initialize(TimelineVersion.class);
        initialize(TimelineAssetRef.class);
    }

    @Test
    void scopedTimelineTaskReadDoesNotDependOnWorkflowOrderStorage() {
        AiTask task = runningTask(AiTaskStatus.SUCCESS.value(), 1L);
        when(taskMapper.selectOne(any(Wrapper.class))).thenReturn(task);

        AiTaskDTO result = service().getOwned(new AiTaskAccessScopeDTO(2001L, 7L, "personal-7"), "701");

        assertThat(result.taskId()).isEqualTo("701");
        ArgumentCaptor<Wrapper> query = ArgumentCaptor.forClass(Wrapper.class);
        verify(taskMapper).selectOne(query.capture());
        assertThat(query.getValue().getSqlSegment())
            .doesNotContainIgnoringCase("av_workflow_order")
            .doesNotContainIgnoringCase("exists");
        verify(taskMapper, never()).countOwnedWorkflowOrder(any(), any(), any(), any());
    }

    @Test
    void scopedWorkflowTaskStillRequiresTheWorkspaceOwnedOrder() {
        AiTask task = runningTask(AiTaskStatus.SUCCESS.value(), 1L);
        task.setResourceType(AiTaskResourceType.WORKFLOW_ORDER.value());
        when(taskMapper.selectOne(any(Wrapper.class))).thenReturn(task);
        when(taskMapper.countOwnedWorkflowOrder(900L, 2001L, 7L, "personal-7")).thenReturn(0);

        assertThatThrownBy(() -> service().getOwned(
            new AiTaskAccessScopeDTO(2001L, 7L, "personal-7"), "701"))
            .isInstanceOf(ServiceException.class);
        verify(taskMapper).countOwnedWorkflowOrder(900L, 2001L, 7L, "personal-7");
    }

    @Test
    void scopedWorkflowTaskAllowsTheWorkspaceOwnedOrder() {
        AiTask task = runningTask(AiTaskStatus.SUCCESS.value(), 1L);
        task.setResourceType(AiTaskResourceType.WORKFLOW_ORDER.value());
        when(taskMapper.selectOne(any(Wrapper.class))).thenReturn(task);
        when(taskMapper.countOwnedWorkflowOrder(900L, 2001L, 7L, "personal-7")).thenReturn(1);

        AiTaskDTO result = service().getOwned(
            new AiTaskAccessScopeDTO(2001L, 7L, "personal-7"), "701");

        assertThat(result.taskId()).isEqualTo("701");
        verify(taskMapper).countOwnedWorkflowOrder(900L, 2001L, 7L, "personal-7");
    }

    @Test
    void suggestionCreationFreezesOnlyTheDraftInputAndCreatesNoAttempt() {
        stubProjectAndDraft();
        when(taskMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(taskMapper.insert(any(AiTask.class))).thenReturn(1);
        when(taskMapper.update(any(AiTask.class), any(LambdaUpdateWrapper.class))).thenReturn(1);
        when(executionMapper.insert(any(AiTaskExecution.class))).thenReturn(1);

        AiTaskDTO result = service().createFreeTask(7L, imagePromptCommand("suggestion-key", "a".repeat(64)));

        assertThat(result.status()).isEqualTo(AiTaskStatus.QUEUED.value());
        assertThat(result.inputVersionId()).isNull();
        ArgumentCaptor<AiTask> task = ArgumentCaptor.forClass(AiTask.class);
        verify(taskMapper).insert(task.capture());
        assertThat(task.getValue().getTaskType()).isEqualTo(AiTaskType.TIMELINE_IMAGE_PROMPT_GENERATE.value());
        assertThat(task.getValue().getInputVersionId()).isNull();
        assertThat(task.getValue().getQuotaPolicyVersion()).isEqualTo("timeline-free-1");
        assertThat(task.getValue().getEstimatedUsage()).isZero();
        ArgumentCaptor<AiTaskExecution> execution = ArgumentCaptor.forClass(AiTaskExecution.class);
        verify(executionMapper).insert(execution.capture());
        assertThat(execution.getValue().getExecutionNo()).isEqualTo(1L);
        assertThat(execution.getValue().getInputVersionId()).isNull();
        verify(attemptMapper, never()).insert(any(org.dromara.aivideo.task.domain.AiTaskAttempt.class));
        verify(versionMapper, never()).insert(any(TimelineVersion.class));
    }

    @Test
    void renderCreationCreatesImmutableInputVersionAndReferenceProjectionInTheSameWrite() {
        stubProjectAndDraft();
        TimelineAssetRef draftReference = new TimelineAssetRef();
        draftReference.setTimelineAssetRefId(77L);
        draftReference.setOwnerUserId(7L);
        draftReference.setProjectId(900L);
        draftReference.setDocumentType("draft");
        draftReference.setDocumentId(500L);
        draftReference.setElementId("main-video");
        draftReference.setAssetId(44L);
        draftReference.setUsageType(TimelineAssetUsageType.BASE_VIDEO.value());
        draftReference.setStartMs(0L);
        draftReference.setEndMs(1_000L);
        when(taskMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(taskMapper.insert(any(AiTask.class))).thenReturn(1);
        when(taskMapper.update(any(AiTask.class), any(LambdaUpdateWrapper.class))).thenReturn(1);
        when(executionMapper.insert(any(AiTaskExecution.class))).thenReturn(1);
        when(versionMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        when(versionMapper.insert(any(TimelineVersion.class))).thenReturn(1);
        when(assetRefMapper.selectList(any(Wrapper.class))).thenReturn(List.of(draftReference));
        when(assetRefMapper.insert(any(TimelineAssetRef.class))).thenReturn(1);
        when(projectMapper.update(any(CreationProject.class), any(LambdaUpdateWrapper.class))).thenReturn(1);

        AiTaskDTO result = service().createFreeTask(7L, renderCommand("render-key", "b".repeat(64)));

        assertThat(result.inputVersionId()).isNotBlank();
        ArgumentCaptor<TimelineVersion> version = ArgumentCaptor.forClass(TimelineVersion.class);
        verify(versionMapper).insert(version.capture());
        assertThat(version.getValue().getVersionReason()).isEqualTo("render_input");
        assertThat(version.getValue().getSourceDraftRevision()).isEqualTo(3L);
        ArgumentCaptor<TimelineAssetRef> copiedReference = ArgumentCaptor.forClass(TimelineAssetRef.class);
        verify(assetRefMapper).insert(copiedReference.capture());
        assertThat(copiedReference.getValue().getDocumentType()).isEqualTo("version");
        assertThat(copiedReference.getValue().getDocumentId()).isEqualTo(version.getValue().getTimelineVersionId());
        verify(attemptMapper, never()).insert(any(org.dromara.aivideo.task.domain.AiTaskAttempt.class));
    }

    @Test
    void renderingProjectRejectsAnotherRenderBeforeWritingAnyTaskFacts() {
        CreationProject rendering = project();
        rendering.setProjectStatus("rendering");
        when(projectMapper.selectOne(any(Wrapper.class))).thenReturn(rendering);
        when(taskMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        assertThatThrownBy(() -> service().createFreeTask(7L, renderCommand("second-render", "d".repeat(64))))
            .isInstanceOfSatisfying(ServiceException.class,
                exception -> assertThat(exception.getCode()).isEqualTo(46612));

        verify(taskMapper, never()).insert(any(AiTask.class));
        verify(executionMapper, never()).insert(any(AiTaskExecution.class));
    }

    @Test
    void replaysTheSameIdempotencyDigestAndRejectsChangedDigestWithoutNewFacts() throws Exception {
        AiTask winner = existingSuggestionTask("idempotency-key", "c".repeat(64));
        when(taskMapper.selectOne(any(Wrapper.class))).thenReturn(winner);

        AiTaskDTO replay = service().createFreeTask(7L, imagePromptCommand("idempotency-key", "c".repeat(64)));

        assertThat(replay.taskId()).isEqualTo("701");
        assertThatThrownBy(() -> service().createFreeTask(7L,
            imagePromptCommand("idempotency-key", "d".repeat(64))))
            .isInstanceOfSatisfying(ServiceException.class, exception -> assertThat(exception.getCode()).isEqualTo(46609));
        verify(taskMapper, never()).insert(any(AiTask.class));
        verify(executionMapper, never()).insert(any(AiTaskExecution.class));
        verify(versionMapper, never()).insert(any(TimelineVersion.class));
    }

    @Test
    void timelineRenderPreflightReplaysOnlyTheExactOwnerProjectRevisionAndDigestWithoutWrites() throws Exception {
        AiTask winner = existingRenderTask("render-replay", "b".repeat(64));
        TimelineVersion version = new TimelineVersion();
        version.setTimelineVersionId(601L);
        version.setOwnerUserId(7L);
        version.setProjectId(900L);
        version.setSourceDraftRevision(3L);
        when(taskMapper.selectOne(any(Wrapper.class))).thenReturn(winner);
        when(versionMapper.selectOne(any(Wrapper.class))).thenReturn(version);

        Optional<AiTaskDTO> replay = service().replayTimelineRender(7L, "900", "3", "render-replay",
            "b".repeat(64));

        assertThat(replay).hasValueSatisfying(task -> {
            assertThat(task.taskId()).isEqualTo("701");
            assertThat(task.projectId()).isEqualTo("900");
            assertThat(task.draftRevision()).isEqualTo("3");
        });
        assertThatThrownBy(() -> service().replayTimelineRender(7L, "901", "3", "render-replay",
            "b".repeat(64)))
            .isInstanceOfSatisfying(ServiceException.class,
                exception -> assertThat(exception.getCode()).isEqualTo(46609));
        assertThatThrownBy(() -> service().replayTimelineRender(7L, "900", "4", "render-replay",
            "b".repeat(64)))
            .isInstanceOfSatisfying(ServiceException.class,
                exception -> assertThat(exception.getCode()).isEqualTo(46609));
        assertThatThrownBy(() -> service().replayTimelineRender(7L, "900", "3", "render-replay",
            "d".repeat(64)))
            .isInstanceOfSatisfying(ServiceException.class,
                exception -> assertThat(exception.getCode()).isEqualTo(46609));
        verify(taskMapper, never()).insert(any(AiTask.class));
        verify(executionMapper, never()).insert(any(AiTaskExecution.class));
        verify(versionMapper, never()).insert(any(TimelineVersion.class));
        verify(projectMapper, never()).selectOne(any(Wrapper.class));
        verify(draftMapper, never()).selectOne(any(Wrapper.class));
    }

    @Test
    void claimUsesCasLeaseAndDefersAttemptInsertionUntilTheRealExternalCall() {
        AiTask task = runningTask(AiTaskStatus.QUEUED.value(), 0L);
        AiTaskExecution execution = queuedExecution();
        LocalDateTime databaseNow = LocalDateTime.of(2026, 8, 8, 0, 0);
        when(taskMapper.lockDispatchCapacityGuard()).thenReturn(701L);
        when(taskMapper.selectDatabaseNow()).thenReturn(databaseNow);
        when(executionMapper.countLiveRunningNonWorkflow(databaseNow)).thenReturn(0L);
        when(executionMapper.countLiveRunningByActor("app_user", 7L, databaseNow)).thenReturn(0L);
        when(executionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(execution));
        when(executionMapper.selectOne(any(Wrapper.class))).thenReturn(execution);
        when(taskMapper.selectOne(any(Wrapper.class))).thenReturn(task);
        when(executionMapper.update(any(AiTaskExecution.class), any(LambdaUpdateWrapper.class))).thenReturn(1);
        when(taskMapper.update(any(AiTask.class), any(LambdaUpdateWrapper.class))).thenReturn(1);

        AiTaskLeaseDTO lease = service().claimNext("timeline-worker-a", 2, 4);

        assertThat(lease).isNotNull();
        assertThat(lease.getLeaseToken()).isNotBlank().isNotEqualTo("timeline-worker-a");
        assertThat(lease.getAttemptId()).isNull();
        verify(attemptMapper, never()).insert(any(org.dromara.aivideo.task.domain.AiTaskAttempt.class));
    }

    @Test
    void claimRejectsInvalidConcurrencyLimitsBeforeReadingDatabase() {
        assertThatThrownBy(() -> service().claimNext("timeline-worker-a", 0, 4))
            .isInstanceOf(ServiceException.class);
        assertThatThrownBy(() -> service().claimNext("timeline-worker-a", 5, 4))
            .isInstanceOf(ServiceException.class);
        assertThatThrownBy(() -> service().claimNext("timeline-worker-a", 1, 101))
            .isInstanceOf(ServiceException.class);

        verify(taskMapper, never()).lockDispatchCapacityGuard();
    }

    @Test
    void claimReturnsNoneWhenTheClusterSystemLimitIsAlreadyFull() {
        LocalDateTime databaseNow = LocalDateTime.of(2026, 8, 8, 0, 0);
        when(executionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(queuedExecution()));
        when(taskMapper.lockDispatchCapacityGuard()).thenReturn(701L);
        when(taskMapper.selectDatabaseNow()).thenReturn(databaseNow);
        when(executionMapper.countLiveRunningNonWorkflow(databaseNow)).thenReturn(4L);

        AiTaskLeaseDTO lease = service().claimNext("timeline-worker-a", 2, 4);

        assertThat(lease).isNull();
        verify(executionMapper).countLiveRunningNonWorkflow(databaseNow);
        verify(executionMapper, never()).update(any(AiTaskExecution.class), any(LambdaUpdateWrapper.class));
    }

    @Test
    void workflowClaimUsesDedicatedProviderCapacityInsteadOfTimelineCapacity() {
        AiTask task = runningTask(AiTaskStatus.QUEUED.value(), 0L);
        task.setTaskType(AiTaskType.WORKFLOW_TEMPLATE_GENERATE.value());
        task.setResourceType(AiTaskResourceType.WORKFLOW_ORDER.value());
        task.setStage(AiTaskStage.WAITING_FOR_DISPATCH.value());
        AiTaskExecution execution = queuedExecution();
        LocalDateTime databaseNow = LocalDateTime.of(2026, 8, 8, 0, 0);
        when(taskMapper.lockDispatchCapacityGuard()).thenReturn(701L);
        when(taskMapper.selectDatabaseNow()).thenReturn(databaseNow);
        when(executionMapper.countLiveRunningWorkflow(databaseNow)).thenReturn(99L);
        when(executionMapper.selectQueuedWorkflowForUpdate(databaseNow, 16)).thenReturn(List.of(execution));
        when(executionMapper.selectOne(any(Wrapper.class))).thenReturn(execution);
        when(taskMapper.selectOne(any(Wrapper.class))).thenReturn(task);
        when(executionMapper.update(any(AiTaskExecution.class), any(LambdaUpdateWrapper.class))).thenReturn(1);
        when(taskMapper.update(any(AiTask.class), any(LambdaUpdateWrapper.class))).thenReturn(1);

        AiTaskLeaseDTO lease = service().claimNextWorkflow("runninghub-worker", 100);

        assertThat(lease).isNotNull();
        verify(executionMapper).countLiveRunningWorkflow(databaseNow);
        verify(executionMapper, never()).countLiveRunningNonWorkflow(databaseNow);
    }

    @Test
    void claimSkipsAQueuedOwnerWhoseLiveLeaseLimitIsAlreadyFull() {
        AiTask task = runningTask(AiTaskStatus.QUEUED.value(), 0L);
        AiTaskExecution execution = queuedExecution();
        LocalDateTime databaseNow = LocalDateTime.of(2026, 8, 8, 0, 0);
        when(taskMapper.lockDispatchCapacityGuard()).thenReturn(701L);
        when(taskMapper.selectDatabaseNow()).thenReturn(databaseNow);
        when(executionMapper.countLiveRunningNonWorkflow(databaseNow)).thenReturn(2L);
        when(executionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(execution));
        when(taskMapper.selectOne(any(Wrapper.class))).thenReturn(task);
        when(executionMapper.countLiveRunningByActor("app_user", 7L, databaseNow)).thenReturn(2L);

        AiTaskLeaseDTO lease = service().claimNext("timeline-worker-a", 2, 4);

        assertThat(lease).isNull();
        verify(executionMapper, never()).update(any(AiTaskExecution.class), any(LambdaUpdateWrapper.class));
        verify(taskMapper, never()).update(any(AiTask.class), any(LambdaUpdateWrapper.class));
    }

    @Test
    void lateTerminalCallbackStopsWhenTheExecutionCasWasLost() {
        AiTask task = runningTask(AiTaskStatus.RUNNING.value(), 2L);
        AiTaskExecution execution = runningExecution("lease-token", 4L);
        when(taskMapper.selectOne(any(Wrapper.class))).thenReturn(task);
        when(executionMapper.selectOne(any(Wrapper.class))).thenReturn(execution);
        when(executionMapper.update(any(AiTaskExecution.class), any(LambdaUpdateWrapper.class))).thenReturn(0);
        AiTaskLeaseDTO lease = lease("701", "801", null, "lease-token", 4);
        AiTaskCompletionDTO completion = new AiTaskCompletionDTO("801", "lease-token", null, "LEASE_LOST",
            "safe failure", null, 4, false, false);

        boolean completed = service().complete(lease, completion, Instant.parse("2026-08-08T00:00:00Z"));

        assertThat(completed).isFalse();
        verify(taskMapper, never()).update(any(AiTask.class), any(LambdaUpdateWrapper.class));
    }

    @Test
    void expiredLeaseCannotRenewWriteProgressOrComplete() {
        AiTask task = runningTask(AiTaskStatus.RUNNING.value(), 2L);
        AiTaskExecution execution = runningExecution("lease-token", 4L);
        execution.setLeaseExpiresAt(LocalDateTime.of(2026, 8, 7, 23, 59));
        when(taskMapper.selectOne(any(Wrapper.class))).thenReturn(task);
        when(executionMapper.selectOne(any(Wrapper.class))).thenReturn(execution);
        AiTaskLeaseDTO lease = lease("701", "801", null, "lease-token", 4);
        Instant now = Instant.parse("2026-08-08T00:00:00Z");

        assertThat(service().renew(lease, now)).isNull();
        assertThat(service().reportProgress(lease, new AiTaskProgressDTO("801", "lease-token", 4, 10,
            AiTaskStage.PREPARING_ASSETS, "preparing"), now)).isNull();
        assertThat(service().complete(lease, new AiTaskCompletionDTO("801", "lease-token", null,
            "WORKER_FAILURE", "safe failure", null, 4, false, false), now)).isFalse();

        verify(executionMapper, never()).update(any(AiTaskExecution.class), any(LambdaUpdateWrapper.class));
        verify(taskMapper, never()).update(any(AiTask.class), any(LambdaUpdateWrapper.class));
    }

    @Test
    void recoveryRequeuesTheRootTogetherWithTheExpiredExecution() {
        AiTask task = runningTask(AiTaskStatus.RUNNING.value(), 2L);
        AiTaskExecution execution = runningExecution("lease-token", 4L);
        AiTaskAttempt attempt = new AiTaskAttempt();
        attempt.setTaskAttemptId(901L);
        attempt.setOwnerUserId(7L);
        attempt.setTaskId(701L);
        attempt.setTaskExecutionId(801L);
        attempt.setAttemptStatus("running");
        attempt.setRowVersion(1L);
        execution.setLeaseExpiresAt(LocalDateTime.of(2026, 8, 7, 23, 59));
        when(executionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(execution));
        when(executionMapper.selectOne(any(Wrapper.class))).thenReturn(execution);
        when(taskMapper.selectOne(any(Wrapper.class))).thenReturn(task);
        when(attemptMapper.selectList(any(Wrapper.class))).thenReturn(List.of(attempt));
        when(executionMapper.update(any(AiTaskExecution.class), any(LambdaUpdateWrapper.class))).thenReturn(1);
        when(taskMapper.update(any(AiTask.class), any(LambdaUpdateWrapper.class))).thenReturn(1);
        when(attemptMapper.update(any(AiTaskAttempt.class), any(LambdaUpdateWrapper.class))).thenReturn(1);

        int recovered = service().recoverExpired(Instant.parse("2026-08-08T00:00:00Z"), 1);

        assertThat(recovered).isEqualTo(1);
        ArgumentCaptor<AiTask> rootUpdate = ArgumentCaptor.forClass(AiTask.class);
        verify(taskMapper).update(rootUpdate.capture(), any(LambdaUpdateWrapper.class));
        assertThat(rootUpdate.getValue().getTaskStatus()).isEqualTo(AiTaskStatus.QUEUED.value());
        assertThat(rootUpdate.getValue().getStage()).isEqualTo(AiTaskStage.QUEUED.value());
        ArgumentCaptor<AiTaskAttempt> attemptUpdate = ArgumentCaptor.forClass(AiTaskAttempt.class);
        verify(attemptMapper).update(attemptUpdate.capture(), any(LambdaUpdateWrapper.class));
        assertThat(attemptUpdate.getValue().getAttemptStatus()).isEqualTo("abandoned");
        assertThat(attemptUpdate.getValue().getRowVersion()).isNull();
    }

    @Test
    void recoveryUsesFrozenSysActorWhenOwnerIsNull() {
        AiTask task = runningTask(AiTaskStatus.RUNNING.value(), 2L);
        task.setOwnerUserId(null);
        task.setTaskType(AiTaskType.WORKFLOW_TEMPLATE_TEST.value());
        task.setResourceType(AiTaskResourceType.WORKFLOW_TEMPLATE.value());
        task.setActorType("sys_user");
        task.setActorId(9L);
        AiTaskExecution execution = runningExecution("lease-token", 4L);
        execution.setOwnerUserId(null);
        execution.setActorType("sys_user");
        execution.setActorId(9L);
        execution.setLeaseExpiresAt(LocalDateTime.of(2026, 8, 7, 23, 59));
        when(executionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(execution));
        when(executionMapper.selectOne(any(Wrapper.class))).thenReturn(execution);
        when(taskMapper.selectOne(any(Wrapper.class))).thenReturn(task);
        when(attemptMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        when(executionMapper.update(any(AiTaskExecution.class), any(LambdaUpdateWrapper.class))).thenReturn(1);
        when(taskMapper.update(any(AiTask.class), any(LambdaUpdateWrapper.class))).thenReturn(1);

        int recovered = service().recoverExpired(Instant.parse("2026-08-08T00:00:00Z"), 1);

        assertThat(recovered).isEqualTo(1);
    }

    @Test
    void recoveredAttemptMayReplayEarlierProgressWithoutRegressingPersistedProgress() {
        AiTask task = runningTask(AiTaskStatus.RUNNING.value(), 2L);
        task.setProgressPercent(90);
        AiTaskExecution execution = runningExecution("lease-token", 4L);
        execution.setProgressPercent(90);
        when(taskMapper.selectOne(any(Wrapper.class))).thenReturn(task);
        when(executionMapper.selectOne(any(Wrapper.class))).thenReturn(execution);
        AiTaskLeaseDTO lease = lease("701", "801", "901", "lease-token", 4);

        AiTaskLeaseDTO unchanged = service().reportProgress(lease, new AiTaskProgressDTO(
            "801", "lease-token", 4, 5, AiTaskStage.PREPARING_ASSETS, "preparing"),
            Instant.parse("2026-08-08T00:00:00Z"));

        assertThat(unchanged).isSameAs(lease);
        verify(executionMapper, never()).update(any(AiTaskExecution.class), any(LambdaUpdateWrapper.class));
        verify(taskMapper, never()).update(any(AiTask.class), any(LambdaUpdateWrapper.class));
    }

    @Test
    void cancellationUsesTheCurrentSnapshotAfterTheRequestBumpsTheLeaseVersion() {
        AiTask task = runningTask(AiTaskStatus.RUNNING.value(), 3L);
        task.setCancelRequested(true);
        AiTaskExecution execution = runningExecution("lease-token", 5L);
        execution.setCancelRequestedSnapshot(true);
        when(taskMapper.selectOne(any(Wrapper.class))).thenReturn(task);
        when(executionMapper.selectOne(any(Wrapper.class))).thenReturn(execution);
        when(executionMapper.update(any(AiTaskExecution.class), any(LambdaUpdateWrapper.class))).thenReturn(1);
        when(taskMapper.update(any(AiTask.class), any(LambdaUpdateWrapper.class))).thenReturn(1);

        boolean cancelled = service().cancel(lease("701", "801", null, "lease-token", 4), "任务已取消",
            Instant.parse("2026-08-08T00:00:00Z"));

        assertThat(cancelled).isTrue();
        verify(executionMapper).update(any(AiTaskExecution.class), any(LambdaUpdateWrapper.class));
        verify(taskMapper).update(any(AiTask.class), any(LambdaUpdateWrapper.class));
    }

    @Test
    void successfulSuggestionCompletionPersistsTheTypedPayloadInsteadOfAnUnboundedOpaqueValue() {
        AiTask task = runningTask(AiTaskStatus.RUNNING.value(), 2L);
        AiTaskExecution execution = runningExecution("lease-token", 4L);
        when(taskMapper.selectOne(any(Wrapper.class))).thenReturn(task);
        when(executionMapper.selectOne(any(Wrapper.class))).thenReturn(execution);
        when(executionMapper.update(any(AiTaskExecution.class), any(LambdaUpdateWrapper.class))).thenReturn(1);
        when(taskMapper.update(any(AiTask.class), any(LambdaUpdateWrapper.class))).thenReturn(1);
        AiTaskLeaseDTO lease = lease("701", "801", null, "lease-token", 4);
        AiTaskImagePromptResultPayloadDTO payload = new AiTaskImagePromptResultPayloadDTO(
            new TimelineImagePromptResultDTO("701", List.of()));
        AiTaskCompletionDTO completion = new AiTaskCompletionDTO("801", "lease-token", null, null, null,
            payload, 4, true, false);

        boolean completed = service().complete(lease, completion, Instant.parse("2026-08-08T00:00:00Z"));

        assertThat(completed).isTrue();
        ArgumentCaptor<AiTask> updated = ArgumentCaptor.forClass(AiTask.class);
        verify(taskMapper).update(updated.capture(), any(LambdaUpdateWrapper.class));
        assertThat(updated.getValue().getResultSchemaVersion()).isEqualTo("timeline-1");
        assertThat(updated.getValue().getResultPayloadJson()).contains("suggestions");
    }

    private AiTaskTransactionServiceImpl service() {
        return new AiTaskTransactionServiceImpl(taskMapper, executionMapper, attemptMapper, projectMapper,
            draftMapper, versionMapper, assetRefMapper, jsonMapper, new FreeAiTaskQuotaPolicyServiceImpl());
    }

    private void stubProjectAndDraft() {
        when(projectMapper.selectOne(any(Wrapper.class))).thenReturn(project());
        when(draftMapper.selectOne(any(Wrapper.class))).thenReturn(draft());
    }

    private CreateFreeAiTaskDTO imagePromptCommand(String idempotencyKey, String requestDigest) {
        return new CreateFreeAiTaskDTO(AiTaskType.TIMELINE_IMAGE_PROMPT_GENERATE,
            AiTaskResourceType.CREATION_PROJECT, "900", "900", "3", null, idempotencyKey, requestDigest,
            "timeline-free-1", 0L, new AiTaskImagePromptPayloadDTO(new TimelineImagePromptCommandDTO(
                "untrusted-task", "900", "3", 0, 2, "AI", "", "", "9:16", "cinematic")));
    }

    private CreateFreeAiTaskDTO renderCommand(String idempotencyKey, String requestDigest) {
        TimelineRenderCommandDTO command = new TimelineRenderCommandDTO("untrusted-task", null, null, null,
            "timeline-fonts-1", "e".repeat(64), null,
            new TimelineOutputConfigDTO("match_canvas", 30, TimelineOutputQuality.STANDARD), List.of());
        return new CreateFreeAiTaskDTO(AiTaskType.TIMELINE_RENDER, AiTaskResourceType.CREATION_PROJECT,
            "900", "900", "3", null, idempotencyKey, requestDigest, "timeline-free-1", 0L,
            new AiTaskRenderPayloadDTO(command));
    }

    private CreationProject project() {
        CreationProject project = new CreationProject();
        project.setProjectId(900L);
        project.setOwnerUserId(7L);
        project.setProjectStatus("editing");
        project.setDelFlag("0");
        return project;
    }

    private TimelineDraft draft() {
        TimelineDraft draft = new TimelineDraft();
        draft.setTimelineDraftId(500L);
        draft.setOwnerUserId(7L);
        draft.setProjectId(900L);
        draft.setRevision(3L);
        draft.setSchemaVersion("timeline-1");
        draft.setContentJson("{\"schemaVersion\":\"timeline-1\",\"tracks\":[]}");
        draft.setContentHash("f".repeat(64));
        draft.setDurationMs(1_000L);
        draft.setDelFlag("0");
        return draft;
    }

    private AiTask existingSuggestionTask(String idempotencyKey, String requestDigest) throws Exception {
        AiTask task = runningTask(AiTaskStatus.QUEUED.value(), 0L);
        task.setIdempotencyKey(idempotencyKey);
        task.setRequestDigest(requestDigest);
        task.setRequestPayloadJson(jsonMapper.writeValueAsString(imagePromptCommand(idempotencyKey, requestDigest).payload()));
        return task;
    }

    private AiTask existingRenderTask(String idempotencyKey, String requestDigest) throws Exception {
        AiTask task = runningTask(AiTaskStatus.SUCCESS.value(), 2L);
        task.setTaskType(AiTaskType.TIMELINE_RENDER.value());
        task.setIdempotencyKey(idempotencyKey);
        task.setRequestDigest(requestDigest);
        task.setInputVersionId(601L);
        task.setRequestPayloadJson(jsonMapper.writeValueAsString(renderCommand(idempotencyKey, requestDigest).payload()));
        return task;
    }

    private AiTask runningTask(String status, long rowVersion) {
        AiTask task = new AiTask();
        task.setTaskId(701L);
        task.setOwnerUserId(7L);
        task.setTaskType(AiTaskType.TIMELINE_IMAGE_PROMPT_GENERATE.value());
        task.setResourceType(AiTaskResourceType.CREATION_PROJECT.value());
        task.setResourceId(900L);
        task.setTaskStatus(status);
        task.setStage("queued");
        task.setProgressPercent(0);
        task.setRowVersion(rowVersion);
        task.setCancelRequested(false);
        task.setActiveExecutionId(801L);
        task.setQuotaPolicyVersion("timeline-free-1");
        task.setEstimatedUsage(0L);
        task.setActorType("app_user");
        task.setActorId(7L);
        task.setRequestSchemaVersion("timeline-1");
        task.setRequestPayloadJson("{\"command\":{\"taskId\":\"701\",\"projectId\":\"900\",\"draftRevision\":\"3\",\"sourceStartOffset\":0,\"sourceEndOffset\":2,\"sourceText\":\"AI\",\"contextBefore\":\"\",\"contextAfter\":\"\",\"canvasAspect\":\"9:16\",\"styleCode\":\"cinematic\"}}");
        return task;
    }

    private AiTaskExecution queuedExecution() {
        AiTaskExecution execution = runningExecution(null, 3L);
        execution.setExecutionStatus(AiTaskExecutionStatus.QUEUED.value());
        execution.setNextRunAt(LocalDateTime.of(2026, 8, 7, 0, 0));
        return execution;
    }

    private AiTaskExecution runningExecution(String leaseToken, long rowVersion) {
        AiTaskExecution execution = new AiTaskExecution();
        execution.setTaskExecutionId(801L);
        execution.setOwnerUserId(7L);
        execution.setTaskId(701L);
        execution.setResourceId(900L);
        execution.setExecutionNo(1L);
        execution.setExecutionStatus(AiTaskExecutionStatus.RUNNING.value());
        execution.setStage("queued");
        execution.setProgressPercent(0);
        execution.setRowVersion(rowVersion);
        execution.setLeaseOwner(leaseToken == null ? null : "timeline-worker-a");
        execution.setLeaseToken(leaseToken);
        execution.setLeaseExpiresAt(leaseToken == null ? null : LocalDateTime.of(2026, 8, 8, 1, 0));
        execution.setCancelRequestedSnapshot(false);
        execution.setActorType("app_user");
        execution.setActorId(7L);
        return execution;
    }

    private AiTaskLeaseDTO lease(String taskId, String executionId, String attemptId, String token, int rowVersion) {
        return new AiTaskLeaseDTO(taskId, executionId, attemptId, token, "timeline-worker-a", "7", null,
            1, attemptId == null ? 0 : 1, rowVersion);
    }

    private static void initialize(Class<?> type) {
        if (TableInfoHelper.getTableInfo(type) == null) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), type);
        }
    }
}
