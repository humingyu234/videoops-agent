package org.dromara.aivideo.task.service.impl;

import org.dromara.aivideo.creation.service.ICreationAssetService;
import org.dromara.aivideo.creation.service.IRenderOutputLifecycleService;
import org.dromara.aivideo.task.dto.AiTaskDTO;
import org.dromara.aivideo.task.dto.AiTaskActorDTO;
import org.dromara.aivideo.task.dto.AiTaskDispatchResultDTO;
import org.dromara.aivideo.task.dto.AiTaskImagePromptPayloadDTO;
import org.dromara.aivideo.task.dto.AiTaskLeaseDTO;
import org.dromara.aivideo.task.dto.AiTaskRenderPayloadDTO;
import org.dromara.aivideo.task.dto.CreateFreeAiTaskDTO;
import org.dromara.aivideo.task.dto.WorkflowAiTaskPayloadDTO;
import org.dromara.aivideo.task.enums.AiTaskResourceType;
import org.dromara.aivideo.task.enums.AiTaskStage;
import org.dromara.aivideo.task.enums.AiTaskType;
import org.dromara.aivideo.task.service.IAiTaskTransactionService;
import org.dromara.aivideo.timeline.dto.TimelineAssetReferenceDTO;
import org.dromara.aivideo.timeline.dto.TimelineImagePromptCommandDTO;
import org.dromara.aivideo.timeline.dto.TimelineImagePromptResultDTO;
import org.dromara.aivideo.timeline.dto.TimelineOutputConfigDTO;
import org.dromara.aivideo.timeline.dto.TimelineProgressDTO;
import org.dromara.aivideo.timeline.dto.TimelineRenderCommandDTO;
import org.dromara.aivideo.timeline.enums.TimelineAssetUsageType;
import org.dromara.aivideo.timeline.enums.TimelineOutputQuality;
import org.dromara.aivideo.timeline.service.ITimelineAiSuggestionService;
import org.dromara.aivideo.timeline.service.TimelineTaskProgressListener;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("dev")
class AiTaskServiceImplTest {

    @Mock
    private IAiTaskTransactionService transactionService;
    @Mock
    private ICreationAssetService assetService;
    @Mock
    private IRenderOutputLifecycleService renderOutputLifecycleService;
    @Mock
    private ITimelineAiSuggestionService suggestionService;

    @Test
    void createsThroughTheTransactionBoundaryWithoutStartingAnExternalWorkerCall() {
        CreateFreeAiTaskDTO command = imagePromptCommand("task-key", "a".repeat(64));
        AiTaskDTO expected = taskDto("701", "queued");
        when(transactionService.createFreeTask(7L, command)).thenReturn(expected);

        AiTaskDTO actual = service().createFreeTask(7L, command);

        assertThat(actual).isSameAs(expected);
        verify(transactionService).createFreeTask(7L, command);
    }

    @Test
    void returnsNoneWhenNoQueuedExecutionCanBeClaimed() {
        when(transactionService.claimNext("timeline-worker-a", 2, 4))
            .thenReturn(null);

        AiTaskDispatchResultDTO result = service().dispatchNext("timeline-worker-a", 2, 4);

        assertThat(result).isEqualTo(new AiTaskDispatchResultDTO("none", null, null));
        verify(transactionService).claimNext("timeline-worker-a", 2, 4);
    }

    @Test
    void rejectsInvalidConcurrencyLimitsBeforeClaiming() {
        assertThatThrownBy(() -> service().dispatchNext("timeline-worker-a", 0, 4))
            .isInstanceOf(ServiceException.class);
        assertThatThrownBy(() -> service().dispatchNext("timeline-worker-a", 5, 4))
            .isInstanceOf(ServiceException.class);

        verify(transactionService, never()).claimNext(org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void stopsAsLeaseLostWhenTheClaimedExecutionNoLongerHasAReadableDispatchContext() {
        AiTaskLeaseDTO lease = new AiTaskLeaseDTO("701", "801", null, "lease-token", "timeline-worker-a", "7",
            null, 1, 0, 1);
        when(transactionService.claimNext("timeline-worker-a", 2, 4))
            .thenReturn(lease);
        when(transactionService.loadDispatchContext(lease)).thenReturn(null);

        AiTaskDispatchResultDTO result = service().dispatchNext("timeline-worker-a", 2, 4);

        assertThat(result).isEqualTo(new AiTaskDispatchResultDTO("lease_lost", "701", "801"));
        verify(transactionService, never()).cancel(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any());
    }

    @Test
    void reportsLeaseLostWhenCancellationCannotDurablyTransition() {
        AiTaskLeaseDTO lease = lease(null, 1);
        when(transactionService.claimNext("timeline-worker-a", 2, 4))
            .thenReturn(lease);
        when(transactionService.loadDispatchContext(lease)).thenReturn(imageDispatchContext());
        when(transactionService.cancellationRequested(lease)).thenReturn(true);
        when(transactionService.cancel(org.mockito.ArgumentMatchers.eq(lease), org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.any())).thenReturn(false);

        AiTaskDispatchResultDTO result = service().dispatchNext("timeline-worker-a", 2, 4);

        assertThat(result).isEqualTo(new AiTaskDispatchResultDTO("lease_lost", "701", "801"));
    }

    @Test
    void renewsTheLeaseBeforePersistingProgressFromAnExternalSuggestion() {
        AiTaskLeaseDTO claimed = lease(null, 1);
        AiTaskLeaseDTO started = lease("901", 1);
        AiTaskLeaseDTO renewed = lease("901", 2);
        AiTaskLeaseDTO progressed = lease("901", 3);
        when(transactionService.claimNext("timeline-worker-a", 2, 4))
            .thenReturn(claimed);
        when(transactionService.loadDispatchContext(claimed)).thenReturn(imageDispatchContext());
        when(transactionService.beginAttempt(org.mockito.ArgumentMatchers.eq(claimed), org.mockito.ArgumentMatchers.any()))
            .thenReturn(started);
        when(transactionService.renew(org.mockito.ArgumentMatchers.eq(started), org.mockito.ArgumentMatchers.any()))
            .thenReturn(renewed);
        when(transactionService.reportProgress(org.mockito.ArgumentMatchers.any(AiTaskLeaseDTO.class), org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any())).thenReturn(progressed);
        when(transactionService.complete(org.mockito.ArgumentMatchers.any(AiTaskLeaseDTO.class), org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any())).thenReturn(true);
        org.mockito.Mockito.doAnswer(invocation -> {
            TimelineTaskProgressListener progress = invocation.getArgument(1);
            progress.onProgress(new TimelineProgressDTO(AiTaskStage.PREPARING_ASSETS, 10, "preparing"));
            return new TimelineImagePromptResultDTO("701", List.of());
        }).when(suggestionService).generateImagePrompt(org.mockito.ArgumentMatchers.any(TimelineImagePromptCommandDTO.class),
            org.mockito.ArgumentMatchers.any(TimelineTaskProgressListener.class), org.mockito.ArgumentMatchers.any());

        AiTaskDispatchResultDTO result = service(suggestionService, null, null, null)
            .dispatchNext("timeline-worker-a", 2, 4);

        assertThat(result).isEqualTo(new AiTaskDispatchResultDTO("completed", "701", "801"));
        org.mockito.InOrder order = org.mockito.Mockito.inOrder(transactionService);
        order.verify(transactionService).renew(org.mockito.ArgumentMatchers.eq(started),
            org.mockito.ArgumentMatchers.any(Instant.class));
        order.verify(transactionService).reportProgress(org.mockito.ArgumentMatchers.eq(renewed),
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(Instant.class));
    }

    @Test
    void createsTheAttemptBeforeOpeningRenderInputStreams() {
        AiTaskLeaseDTO claimed = lease(null, 1);
        AiTaskLeaseDTO started = lease("901", 1);
        when(transactionService.claimNext("timeline-worker-a", 2, 4))
            .thenReturn(claimed);
        when(transactionService.loadDispatchContext(claimed)).thenReturn(renderDispatchContext());
        when(transactionService.beginAttempt(org.mockito.ArgumentMatchers.eq(claimed), org.mockito.ArgumentMatchers.any()))
            .thenReturn(started);
        when(transactionService.complete(org.mockito.ArgumentMatchers.any(AiTaskLeaseDTO.class), org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any())).thenReturn(true);
        when(assetService.openOwnedMedia(7L, "44", TimelineAssetUsageType.BASE_VIDEO))
            .thenThrow(new ServiceException("素材读取失败"));

        service(null, null, assetService, renderOutputLifecycleService)
            .dispatchNext("timeline-worker-a", 2, 4);

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(transactionService, assetService);
        order.verify(transactionService).beginAttempt(org.mockito.ArgumentMatchers.eq(claimed),
            org.mockito.ArgumentMatchers.any(Instant.class));
        order.verify(assetService).openOwnedMedia(7L, "44", TimelineAssetUsageType.BASE_VIDEO);
    }

    @Test
    void workflowTaskFailsClosedWithoutDispatcherAndNeverFallsIntoTimelineSuggestions() throws Exception {
        AiTaskLeaseDTO claimed = new AiTaskLeaseDTO("701", "801", null, "lease-token",
            "workflow-worker", "sys_user", "9", null, 1, 0, 1);
        WorkflowAiTaskPayloadDTO payload = new WorkflowAiTaskPayloadDTO(null, "201",
            "sha256:" + "a".repeat(64), Map.of("prompt", JsonMapper.builder().build().readTree("\"hi\"")));
        when(transactionService.claimNext("workflow-worker", 1, 2)).thenReturn(claimed);
        when(transactionService.loadDispatchContext(claimed)).thenReturn(new IAiTaskTransactionService.DispatchContext(
            new AiTaskActorDTO("sys_user", 9L, null), AiTaskType.WORKFLOW_TEMPLATE_TEST, payload, null));
        when(transactionService.complete(org.mockito.ArgumentMatchers.eq(claimed), org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any())).thenReturn(true);

        assertThat(service().dispatchNext("workflow-worker", 1, 2).outcome()).isEqualTo("failed");
        verify(transactionService, never()).beginAttempt(org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any());
        verify(suggestionService, never()).generateImagePrompt(org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    private AiTaskServiceImpl service() {
        return service(null, null, null, null);
    }

    private AiTaskServiceImpl service(ITimelineAiSuggestionService suggestions,
                                      org.dromara.aivideo.timeline.service.ITimelineMediaRenderService mediaRender,
                                      ICreationAssetService assets,
                                      IRenderOutputLifecycleService outputLifecycle) {
        return new AiTaskServiceImpl(transactionService, suggestions, mediaRender, assets, outputLifecycle);
    }

    private IAiTaskTransactionService.DispatchContext imageDispatchContext() {
        return new IAiTaskTransactionService.DispatchContext(7L, AiTaskType.TIMELINE_IMAGE_PROMPT_GENERATE,
            new AiTaskImagePromptPayloadDTO(new TimelineImagePromptCommandDTO("701", "900", "3", 0, 2,
                "AI", "", "", "9:16", "cinematic")), null);
    }

    private IAiTaskTransactionService.DispatchContext renderDispatchContext() {
        TimelineRenderCommandDTO command = new TimelineRenderCommandDTO("701", "801", "901", "601",
            "timeline-fonts-1", "a".repeat(64), null,
            new TimelineOutputConfigDTO("match_canvas", 30, TimelineOutputQuality.STANDARD),
            List.of(new TimelineAssetReferenceDTO("44", TimelineAssetUsageType.BASE_VIDEO, List.of("main-video"),
                "b".repeat(64), 100L)));
        return new IAiTaskTransactionService.DispatchContext(7L, AiTaskType.TIMELINE_RENDER,
            new AiTaskRenderPayloadDTO(command), "c".repeat(64));
    }

    private AiTaskLeaseDTO lease(String attemptId, int rowVersion) {
        return new AiTaskLeaseDTO("701", "801", attemptId, "lease-token", "timeline-worker-a", "7", "601",
            1, attemptId == null ? 0 : 1, rowVersion);
    }

    private CreateFreeAiTaskDTO imagePromptCommand(String idempotencyKey, String requestDigest) {
        return new CreateFreeAiTaskDTO(AiTaskType.TIMELINE_IMAGE_PROMPT_GENERATE,
            AiTaskResourceType.CREATION_PROJECT, "900", "900", "3", null, idempotencyKey, requestDigest,
            "timeline-free-1", 0L, new AiTaskImagePromptPayloadDTO(new TimelineImagePromptCommandDTO(
                "untrusted-task-id", "900", "3", 0, 2, "AI", "", "", "9:16", "cinematic")));
    }

    private AiTaskDTO taskDto(String taskId, String status) {
        return new AiTaskDTO(taskId, AiTaskType.TIMELINE_IMAGE_PROMPT_GENERATE.value(), status, "queued",
            AiTaskResourceType.CREATION_PROJECT.value(), "900", "900", "3", null, null, null, null,
            null, null, null, 0, true, false);
    }
}
