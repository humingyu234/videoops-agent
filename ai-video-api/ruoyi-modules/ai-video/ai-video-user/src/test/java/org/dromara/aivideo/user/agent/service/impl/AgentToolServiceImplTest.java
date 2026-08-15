package org.dromara.aivideo.user.agent.service.impl;

import org.dromara.aivideo.agent.dto.AgentToolDTOs;
import org.dromara.aivideo.creation.dto.CreationAssetDTO;
import org.dromara.aivideo.creation.dto.CreationOutputDTO;
import org.dromara.aivideo.creation.enums.CreationAssetStatus;
import org.dromara.aivideo.creation.enums.CreationAssetType;
import org.dromara.aivideo.creation.enums.CreationAssetUsageOrigin;
import org.dromara.aivideo.creation.service.ICreationAssetService;
import org.dromara.aivideo.creation.service.ICreationProjectService;
import org.dromara.aivideo.digitalhuman.domain.DigitalHumanJobStage;
import org.dromara.aivideo.digitalhuman.domain.DigitalHumanJobStatus;
import org.dromara.aivideo.digitalhuman.domain.DigitalHumanJobType;
import org.dromara.aivideo.digitalhuman.dto.CreateDigitalHumanVideoByResourceDTO;
import org.dromara.aivideo.digitalhuman.dto.CreateVoiceGenerationByResourceDTO;
import org.dromara.aivideo.digitalhuman.dto.DigitalHumanJobDTO;
import org.dromara.aivideo.digitalhuman.dto.DigitalHumanOwnerDTO;
import org.dromara.aivideo.digitalhuman.service.IDigitalHumanGenerationService;
import org.dromara.aivideo.digitalhuman.service.IDigitalHumanResourceGenerationService;
import org.dromara.aivideo.identity.dto.AppPrincipalSnapshotDTO;
import org.dromara.aivideo.identity.dto.AppWorkspaceSessionSnapshotDTO;
import org.dromara.aivideo.identity.security.ConditionalOnAppSecurityEnabled;
import org.dromara.aivideo.task.dto.AiTaskAccessScopeDTO;
import org.dromara.aivideo.task.dto.AiTaskDTO;
import org.dromara.aivideo.task.service.IAiTaskService;
import org.dromara.aivideo.user.timeline.domain.bo.CreateTimelineRenderTaskBo;
import org.dromara.aivideo.user.timeline.service.TimelineTaskApplicationService;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.Arrays;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("dev")
class AgentToolServiceImplTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Set<String> ALL_PERMISSIONS = Set.of(
        "aivideo:studio:generate", "aivideo:studio:query", "aivideo:voice:query",
        "aivideo:portrait:query", "aivideo:creation:edit", "aivideo:creation:generate",
        "aivideo:task:query", "aivideo:creation-asset:query");

    @Mock
    private IDigitalHumanResourceGenerationService resourceGenerationService;
    @Mock
    private IDigitalHumanGenerationService generationService;
    @Mock
    private ICreationProjectService projectService;
    @Mock
    private ICreationAssetService assetService;
    @Mock
    private TimelineTaskApplicationService timelineTaskService;
    @Mock
    private IAiTaskService taskService;

    @Test
    void onlyAssemblesWhenAppSecurityIsEnabled() {
        assertThat(AgentToolServiceImpl.class).hasAnnotation(ConditionalOnAppSecurityEnabled.class);
    }

    @Test
    void dispatchesVoiceSubmitConfirmAndUnifiedStatusWithPrincipalOwnership() {
        AppPrincipalSnapshotDTO principal = principal(ALL_PERMISSIONS);
        when(resourceGenerationService.createVoiceJob(any())).thenReturn(voiceJob(false));
        when(generationService.confirmVoiceJob(501L, new DigitalHumanOwnerDTO(2001L, 1001L)))
            .thenReturn(voiceJob(true));
        when(generationService.getJob(601L, new DigitalHumanOwnerDTO(2001L, 1001L)))
            .thenReturn(videoJob());

        AgentToolDTOs.GenerationJobResult submitted = (AgentToolDTOs.GenerationJobResult) service().execute(principal,
            call("submit_voice_generation", json("idempotencyKey", "voice-1", "scriptText", "固定文案",
                "referenceVoiceId", "301")));
        AgentToolDTOs.GenerationJobResult confirmed = (AgentToolDTOs.GenerationJobResult) service().execute(principal,
            call("confirm_voice_generation", json("jobId", "501")));
        AgentToolDTOs.GenerationJobResult status = (AgentToolDTOs.GenerationJobResult) service().execute(principal,
            call("get_generation_status", json("jobId", "601")));

        assertThat(submitted.jobId()).isEqualTo("501");
        assertThat(confirmed.voiceConfirmed()).isTrue();
        assertThat(status.jobType()).isEqualTo("video_generate");
        verify(resourceGenerationService).createVoiceJob(new CreateVoiceGenerationByResourceDTO(
            principal, "voice-1", "固定文案", "301"));
    }

    @Test
    void dispatchesVideoProjectAndRenderWithServerFixedOutputConfiguration() {
        AppPrincipalSnapshotDTO principal = principal(ALL_PERMISSIONS);
        when(resourceGenerationService.createVideoJob(any())).thenReturn(videoJob());
        when(projectService.create(eq(1001L), any())).thenReturn(project());
        when(timelineTaskService.createRender(eq(1001L), eq("701"), any())).thenReturn(renderTask("queued", null));

        service().execute(principal, call("submit_digital_human_video", json(
            "idempotencyKey", "video-1", "voiceJobId", "501", "portraitId", "401")));
        AgentToolDTOs.ProjectResult project = (AgentToolDTOs.ProjectResult) service().execute(principal,
            call("prepare_timeline_project", json("idempotencyKey", "project-1", "videoJobId", "601",
                "projectTitle", "T3 黄金链")));
        AgentToolDTOs.RenderTaskResult render = (AgentToolDTOs.RenderTaskResult) service().execute(principal,
            call("render_timeline", json("idempotencyKey", "render-1", "projectId", "701",
                "expectedRevision", "3")));

        verify(resourceGenerationService).createVideoJob(new CreateDigitalHumanVideoByResourceDTO(
            principal, "video-1", 501L, "401"));
        verify(projectService).create(1001L, new ICreationProjectService.CreateProjectCommand(
            "digital_human_job", "601", "T3 黄金链", "project-1"));
        ArgumentCaptor<CreateTimelineRenderTaskBo> body = ArgumentCaptor.forClass(CreateTimelineRenderTaskBo.class);
        verify(timelineTaskService).createRender(eq(1001L), eq("701"), body.capture());
        assertThat(body.getValue().getOutputConfig().getResolutionPreset()).isEqualTo("match_canvas");
        assertThat(body.getValue().getOutputConfig().getFrameRate()).isEqualTo(30);
        assertThat(body.getValue().getOutputConfig().getQualityPreset()).isEqualTo("high");
        assertThat(project.currentDraftRevision()).isEqualTo("3");
        assertThat(render.taskId()).isEqualTo("801");
    }

    @Test
    void returnsOwnedReadyTaskOutputAfterProjectLatestHasAdvanced() {
        AppPrincipalSnapshotDTO principal = principal(ALL_PERMISSIONS);
        AiTaskAccessScopeDTO scope = new AiTaskAccessScopeDTO(2001L, 1001L, "workspace-key");
        when(taskService.getOwned(scope, "801")).thenReturn(renderTask("success", "901"));
        lenient().when(projectService.getLatestOutputOwned(1001L, "701"))
            .thenReturn(new CreationOutputDTO("701", "902", "802", Instant.EPOCH));
        when(assetService.getOwnedTimelineRenderOutput(1001L, "801", "901")).thenReturn(outputAsset());

        AgentToolDTOs.RenderStatusResult status = (AgentToolDTOs.RenderStatusResult) service().execute(principal,
            call("get_timeline_render_status", json("taskId", "801")));
        AgentToolDTOs.OutputInspectionResult inspection = (AgentToolDTOs.OutputInspectionResult) service().execute(
            principal, call("inspect_timeline_output", json("taskId", "801")));

        assertThat(status.resultAssetId()).isEqualTo("901");
        assertThat(inspection).satisfies(result -> {
            assertThat(result.assetId()).isEqualTo("901");
            assertThat(result.status()).isEqualTo("ready");
            assertThat(result.assetType()).isEqualTo("video");
            assertThat(result.usageOrigin()).isEqualTo("timeline_render_output");
            assertThat(result.hasVideoStream()).isTrue();
            assertThat(result.hasAudioStream()).isTrue();
            assertThat(result.downloadPath()).isEqualTo("/api/studio/creation-assets/901/content");
        });
        verify(projectService, never()).getLatestOutputOwned(1001L, "701");
    }

    @Test
    void rejectsUnknownMissingWrongAndExtraArgumentsBeforeAnyDelegate() {
        AppPrincipalSnapshotDTO principal = principal(ALL_PERMISSIONS);
        ObjectNode extra = json("taskId", "801");
        extra.put("ownerId", "1001");

        assertError(() -> service().execute(principal, call("unknown", json())), 46701);
        assertError(() -> service().execute(principal,
            call("get_generation_status", json())), 46702);
        assertError(() -> service().execute(principal,
            call("get_generation_status", json("jobId", 501))), 46702);
        assertError(() -> service().execute(principal, call("submit_voice_generation", json(
            "idempotencyKey", "voice-overflow", "scriptText", "固定文案",
            "referenceVoiceId", "9999999999999999999"))), 46702);
        assertError(() -> service().execute(principal,
            call("get_timeline_render_status", extra)), 46702);

        verifyNoInteractions(resourceGenerationService, generationService, projectService, assetService,
            timelineTaskService, taskService);
    }

    @Test
    void rejectsMissingPermissionAndNonCanonicalWorkspaceBeforeAnyDelegate() {
        AppPrincipalSnapshotDTO missing = principal(Set.of("aivideo:task:query"));
        AppPrincipalSnapshotDTO wrongOwner = new AppPrincipalSnapshotDTO(1001L, "creator", "desktop",
            1L, 1L, 1L, 1L, new AppWorkspaceSessionSnapshotDTO("workspace-key", "personal", 2001L,
            "app_user", 9999L, "app_user", 1001L, "personal_creator", ALL_PERMISSIONS, 1L, null));

        assertError(() -> service().execute(missing, call("render_timeline", json(
            "idempotencyKey", "render-1", "projectId", "701", "expectedRevision", "3"))), 46703);
        assertError(() -> service().execute(wrongOwner,
            call("get_generation_status", json("jobId", "501"))), 46703);

        verifyNoInteractions(resourceGenerationService, generationService, projectService, assetService,
            timelineTaskService, taskService);
    }

    @Test
    void preservesOwnerScopedTaskNotFoundWithoutInspectingAssets() {
        AppPrincipalSnapshotDTO principal = principal(ALL_PERMISSIONS);
        AiTaskAccessScopeDTO scope = new AiTaskAccessScopeDTO(2001L, 1001L, "workspace-key");
        when(taskService.getOwned(scope, "801")).thenThrow(new ServiceException("任务不存在", 404));

        assertError(() -> service().execute(principal,
            call("get_timeline_render_status", json("taskId", "801"))), 404);

        verifyNoInteractions(projectService, assetService);
    }

    @Test
    void rejectsMismatchedOrUnsafeOutputFacts() {
        AppPrincipalSnapshotDTO principal = principal(ALL_PERMISSIONS);
        AiTaskAccessScopeDTO scope = new AiTaskAccessScopeDTO(2001L, 1001L, "workspace-key");
        when(taskService.getOwned(scope, "801")).thenReturn(renderTask("success", "901"));
        CreationAssetDTO unsafe = new CreationAssetDTO("901", "final.mp4", "video/mp4", "a".repeat(64),
            CreationAssetType.VIDEO, CreationAssetUsageOrigin.TIMELINE_RENDER_OUTPUT, CreationAssetStatus.READY,
            5_244_591L, 25_800L, 1080, 1920, false, true, Instant.EPOCH);
        when(assetService.getOwnedTimelineRenderOutput(1001L, "801", "901")).thenReturn(unsafe);

        assertError(() -> service().execute(principal,
            call("get_timeline_render_status", json("taskId", "801"))), 46704);

        verifyNoInteractions(projectService);
    }

    @Test
    void returnsPersistedStableFailureFactsWithoutInspectingOutputs() {
        AppPrincipalSnapshotDTO principal = principal(ALL_PERMISSIONS);
        DigitalHumanOwnerDTO owner = new DigitalHumanOwnerDTO(2001L, 1001L);
        AiTaskAccessScopeDTO scope = new AiTaskAccessScopeDTO(2001L, 1001L, "workspace-key");
        when(generationService.getJob(501L, owner)).thenReturn(failedVoiceJob());
        when(generationService.getJob(601L, owner)).thenReturn(failedVideoJob());
        when(taskService.getOwned(scope, "801")).thenReturn(failedRenderTask());

        AgentToolDTOs.GenerationJobResult voice = (AgentToolDTOs.GenerationJobResult) service().execute(principal,
            call("get_generation_status", json("jobId", "501")));
        AgentToolDTOs.GenerationJobResult video = (AgentToolDTOs.GenerationJobResult) service().execute(principal,
            call("get_generation_status", json("jobId", "601")));
        AgentToolDTOs.RenderStatusResult render = (AgentToolDTOs.RenderStatusResult) service().execute(principal,
            call("get_timeline_render_status", json("taskId", "801")));

        assertThat(voice.errorCode()).isEqualTo("VOICE_PROVIDER_REJECTED");
        assertThat(voice.safeMessage()).isEqualTo("声音生成失败，请稍后重试");
        assertThat(video.errorCode()).isEqualTo("VIDEO_PROVIDER_FAILED");
        assertThat(video.safeMessage()).isEqualTo("数字人视频生成失败，请稍后重试");
        assertThat(render.errorCode()).isEqualTo("TIMELINE_RENDER_FAILED");
        assertThat(render.safeMessage()).isEqualTo("视频渲染失败，请稍后重试");
        verifyNoInteractions(projectService, assetService);
    }

    @Test
    void rejectsFailedResultsWithoutCompleteStableFailureFacts() {
        AppPrincipalSnapshotDTO principal = principal(ALL_PERMISSIONS);
        DigitalHumanOwnerDTO owner = new DigitalHumanOwnerDTO(2001L, 1001L);
        AiTaskAccessScopeDTO scope = new AiTaskAccessScopeDTO(2001L, 1001L, "workspace-key");
        when(generationService.getJob(501L, owner)).thenReturn(new DigitalHumanJobDTO(
            501L, null, DigitalHumanJobType.VOICE_GENERATE, DigitalHumanJobStatus.FAILED,
            DigitalHumanJobStage.FAILED, 0, false, false, null, "声音生成失败，请稍后重试"));
        when(taskService.getOwned(scope, "801")).thenReturn(failedRenderTask(null, "视频渲染失败，请稍后重试"));

        assertError(() -> service().execute(principal,
            call("get_generation_status", json("jobId", "501"))), 46704);
        assertError(() -> service().execute(principal,
            call("get_timeline_render_status", json("taskId", "801"))), 46704);

        verifyNoInteractions(projectService, assetService);
    }

    @Test
    void publicToolResultsNeverExposeStorageLeaseTokenOrSignedUrlFields() {
        Set<String> names = Arrays.stream(AgentToolDTOs.class.getDeclaredClasses())
            .filter(AgentToolDTOs.Result.class::isAssignableFrom)
            .filter(Class::isRecord)
            .flatMap(type -> Arrays.stream(type.getRecordComponents()))
            .map(component -> component.getName().toLowerCase())
            .collect(java.util.stream.Collectors.toSet());

        assertThat(names).noneMatch(name -> name.contains("storage") || name.contains("lease")
            || name.contains("token") || name.contains("secret") || name.contains("signedurl"));
    }

    private AgentToolServiceImpl service() {
        return new AgentToolServiceImpl(resourceGenerationService, generationService, projectService, assetService,
            timelineTaskService, taskService);
    }

    private AgentToolDTOs.Call call(String name, ObjectNode arguments) {
        return new AgentToolDTOs.Call(name, arguments);
    }

    private ObjectNode json(Object... values) {
        ObjectNode node = MAPPER.createObjectNode();
        for (int index = 0; index < values.length; index += 2) {
            String key = (String) values[index];
            Object value = values[index + 1];
            if (value instanceof String text) {
                node.put(key, text);
            } else if (value instanceof Integer number) {
                node.put(key, number);
            } else {
                throw new AssertionError("unsupported test JSON value");
            }
        }
        return node;
    }

    private AppPrincipalSnapshotDTO principal(Set<String> permissions) {
        return new AppPrincipalSnapshotDTO(1001L, "creator", "desktop", 1L, 1L, 1L, 1L,
            new AppWorkspaceSessionSnapshotDTO("workspace-key", "personal", 2001L, "app_user", 1001L,
                "app_user", 1001L, "personal_creator", permissions, 1L, null));
    }

    private DigitalHumanJobDTO voiceJob(boolean confirmed) {
        return new DigitalHumanJobDTO(501L, null, DigitalHumanJobType.VOICE_GENERATE,
            DigitalHumanJobStatus.SUCCEEDED, DigitalHumanJobStage.COMPLETED, 100, confirmed, true, null);
    }

    private DigitalHumanJobDTO videoJob() {
        return new DigitalHumanJobDTO(601L, 501L, DigitalHumanJobType.VIDEO_GENERATE,
            DigitalHumanJobStatus.SUCCEEDED, DigitalHumanJobStage.COMPLETED, 100, true, true, null);
    }

    private DigitalHumanJobDTO failedVoiceJob() {
        return new DigitalHumanJobDTO(501L, null, DigitalHumanJobType.VOICE_GENERATE,
            DigitalHumanJobStatus.FAILED, DigitalHumanJobStage.FAILED, 0, false, false,
            "VOICE_PROVIDER_REJECTED", "声音生成失败，请稍后重试");
    }

    private DigitalHumanJobDTO failedVideoJob() {
        return new DigitalHumanJobDTO(601L, 501L, DigitalHumanJobType.VIDEO_GENERATE,
            DigitalHumanJobStatus.FAILED, DigitalHumanJobStage.FAILED, 0, true, false,
            "VIDEO_PROVIDER_FAILED", "数字人视频生成失败，请稍后重试");
    }

    private ICreationProjectService.CreationProjectDTO project() {
        return new ICreationProjectService.CreationProjectDTO("701", "T3 黄金链", "digital_human_job", "601",
            "711", "712", "editing", 1080, 1920, 30, 25_800L, 3L, "timeline-1", null,
            Instant.EPOCH, Instant.EPOCH);
    }

    private AiTaskDTO renderTask(String status, String resultAssetId) {
        return new AiTaskDTO("801", "timeline_render", status, status, "creation_project", "701", "701", "3",
            "811", resultAssetId, null, null, "2026-08-16T00:00:00Z", "2026-08-16T00:00:00Z", null,
            "success".equals(status) ? 100 : 0, !"success".equals(status), false);
    }

    private AiTaskDTO failedRenderTask() {
        return failedRenderTask("TIMELINE_RENDER_FAILED", "视频渲染失败，请稍后重试");
    }

    private AiTaskDTO failedRenderTask(String errorCode, String safeMessage) {
        return new AiTaskDTO("801", "timeline_render", "failed", "failed", "creation_project", "701", "701",
            "3", "811", null, errorCode, safeMessage,
            "2026-08-16T00:00:00Z", "2026-08-16T00:00:00Z", null, 80, false, true);
    }

    private CreationAssetDTO outputAsset() {
        return new CreationAssetDTO("901", "final.mp4", "video/mp4", "a".repeat(64), CreationAssetType.VIDEO,
            CreationAssetUsageOrigin.TIMELINE_RENDER_OUTPUT, CreationAssetStatus.READY, 5_244_591L, 25_800L,
            1080, 1920, true, true, Instant.EPOCH);
    }

    private void assertError(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, int code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(ServiceException.class,
            exception -> assertThat(exception.getCode()).isEqualTo(code));
    }
}
