package org.dromara.aivideo.user.agent.service.impl;

import org.dromara.aivideo.agent.dto.AgentRunOrchestrationDTOs;
import org.dromara.aivideo.agent.dto.AgentRunTraceDTO;
import org.dromara.aivideo.agent.dto.AgentToolDTOs;
import org.dromara.aivideo.agent.service.IAgentRunOrchestrationService;
import org.dromara.aivideo.agent.service.IAgentRunService;
import org.dromara.aivideo.agent.service.IAgentRunTraceService;
import org.dromara.aivideo.agent.service.IAgentToolService;
import org.dromara.aivideo.creation.service.ICreationProjectService;
import org.dromara.aivideo.digitalhuman.domain.DigitalHumanJobStage;
import org.dromara.aivideo.digitalhuman.domain.DigitalHumanJobStatus;
import org.dromara.aivideo.digitalhuman.domain.DigitalHumanJobType;
import org.dromara.aivideo.digitalhuman.dto.DigitalHumanJobDTO;
import org.dromara.aivideo.digitalhuman.service.IDigitalHumanGenerationService;
import org.dromara.aivideo.identity.dto.AppPrincipalSnapshotDTO;
import org.dromara.aivideo.identity.dto.AppWorkspaceSessionSnapshotDTO;
import org.dromara.aivideo.identity.security.AppAuditRequestContext;
import org.dromara.aivideo.identity.security.AppAuditRequestContextHolder;
import org.dromara.aivideo.user.agent.domain.bo.AgentApprovalDecisionBo;
import org.dromara.aivideo.user.agent.domain.bo.AgentRunRevisionBo;
import org.dromara.aivideo.user.agent.domain.bo.CreateAgentRunBo;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@Tag("dev")
class AgentRunApplicationServiceImplTest {

    private static final String INPUT_HASH = "a".repeat(64);

    private static final Set<String> PERMISSIONS = Set.of(
        "aivideo:studio:generate", "aivideo:studio:query", "aivideo:voice:query",
        "aivideo:portrait:query", "aivideo:creation:edit", "aivideo:creation:generate",
        "aivideo:task:query", "aivideo:creation-asset:query");
    private static final Set<String> VIDEO_JOB_PERMISSIONS = Set.of(
        "aivideo:studio:generate", "aivideo:studio:query", "aivideo:creation:edit",
        "aivideo:creation:generate", "aivideo:task:query", "aivideo:creation-asset:query");

    @Test
    void createsOneFrozenContractWithServerOwnedPolicyAndStableKeys() {
        Fixture fixture = fixture();
        CreateAgentRunBo body = createBody();

        var detail = fixture.service.create(principal(PERMISSIONS), body);

        ArgumentCaptor<IAgentRunService.AppendDeliveryBriefCommand> brief =
            ArgumentCaptor.forClass(IAgentRunService.AppendDeliveryBriefCommand.class);
        ArgumentCaptor<IAgentRunService.AppendAcceptanceProfileCommand> profile =
            ArgumentCaptor.forClass(IAgentRunService.AppendAcceptanceProfileCommand.class);
        ArgumentCaptor<IAgentRunService.CreateAgentRunCommand> run =
            ArgumentCaptor.forClass(IAgentRunService.CreateAgentRunCommand.class);
        verify(fixture.runs).appendDeliveryBrief(any(), brief.capture());
        verify(fixture.runs).appendAcceptanceProfile(any(), profile.capture());
        verify(fixture.runs).createRun(any(), run.capture());

        assertThat(brief.getValue().idempotencyKey()).isEqualTo("client-1.brief");
        assertThat(brief.getValue().briefJson()).contains(
            "\"startAt\":\"new\"", "\"scriptText\":\"固定文案\"",
            "\"referenceVoiceId\":\"11\"", "\"portraitId\":\"12\"")
            .doesNotContain("owner", "worker", "lease");
        assertThat(profile.getValue().deliveryBriefVersionId()).isEqualTo(101L);
        assertThat(profile.getValue().idempotencyKey()).isEqualTo("client-1.profile");
        assertThat(profile.getValue().profileJson()).contains(
            "\"maxRunSeconds\":3600", "\"maxProviderSubmissions\":2",
            "\"maxRenderRetries\":1", "\"pollIntervalSeconds\":5");
        assertThat(run.getValue()).isEqualTo(new IAgentRunService.CreateAgentRunCommand(101L, 102L,
            "client-1.run"));
        assertThat(detail.run().runId()).isEqualTo("103");
        assertThat(detail.trace().completeness()).isEqualTo("durable_facts");
        assertThat(detail.action()).isNull();
    }

    @Test
    void createsReuseContractsOnlyFromStoredSucceededOwnedJobsAndFreezesTheirInputHash() {
        Fixture voiceFixture = fixture();
        when(voiceFixture.generation.getStoredJob(eq(501L), any())).thenReturn(
            job(501L, null, DigitalHumanJobType.VOICE_GENERATE, true, INPUT_HASH));
        CreateAgentRunBo voice = new CreateAgentRunBo();
        voice.setStartAt("voice_job");
        voice.setVoiceJobId("501");
        voice.setPortraitId("12");
        voice.setProjectTitle("复用声音");
        voice.setIdempotencyKey("reuse-voice");

        voiceFixture.service.create(principal(PERMISSIONS), voice);

        ArgumentCaptor<IAgentRunService.AppendDeliveryBriefCommand> voiceBrief =
            ArgumentCaptor.forClass(IAgentRunService.AppendDeliveryBriefCommand.class);
        ArgumentCaptor<IAgentRunService.AppendAcceptanceProfileCommand> voiceProfile =
            ArgumentCaptor.forClass(IAgentRunService.AppendAcceptanceProfileCommand.class);
        verify(voiceFixture.runs).appendDeliveryBrief(any(), voiceBrief.capture());
        verify(voiceFixture.runs).appendAcceptanceProfile(any(), voiceProfile.capture());
        assertThat(voiceBrief.getValue().briefJson()).contains(
            "\"startAt\":\"voice_job\"", "\"voiceJobId\":\"501\"",
            "\"inputHash\":\"" + INPUT_HASH + "\"", "\"portraitId\":\"12\"")
            .doesNotContain("scriptText", "referenceVoiceId", "owner");
        assertThat(voiceProfile.getValue().profileJson()).contains("\"maxProviderSubmissions\":1");

        Fixture videoFixture = fixture();
        when(videoFixture.generation.getStoredJob(eq(601L), any())).thenReturn(
            job(601L, 501L, DigitalHumanJobType.VIDEO_GENERATE, false, INPUT_HASH));
        CreateAgentRunBo video = new CreateAgentRunBo();
        video.setStartAt("video_job");
        video.setVideoJobId("601");
        video.setProjectTitle("复用视频");
        video.setIdempotencyKey("reuse-video");

        videoFixture.service.create(principal(PERMISSIONS), video);

        ArgumentCaptor<IAgentRunService.AppendDeliveryBriefCommand> videoBrief =
            ArgumentCaptor.forClass(IAgentRunService.AppendDeliveryBriefCommand.class);
        ArgumentCaptor<IAgentRunService.AppendAcceptanceProfileCommand> videoProfile =
            ArgumentCaptor.forClass(IAgentRunService.AppendAcceptanceProfileCommand.class);
        verify(videoFixture.runs).appendDeliveryBrief(any(), videoBrief.capture());
        verify(videoFixture.runs).appendAcceptanceProfile(any(), videoProfile.capture());
        assertThat(videoBrief.getValue().briefJson()).contains(
            "\"startAt\":\"video_job\"", "\"videoJobId\":\"601\"",
            "\"inputHash\":\"" + INPUT_HASH + "\"")
            .doesNotContain("portraitId", "voiceJobId", "owner");
        assertThat(videoProfile.getValue().profileJson()).contains("\"maxProviderSubmissions\":0");
    }

    @Test
    void rejectsWrongTypeNonSucceededOrDigestlessReuseBeforeContractWrites() {
        Fixture fixture = fixture();
        CreateAgentRunBo body = new CreateAgentRunBo();
        body.setStartAt("video_job");
        body.setVideoJobId("601");
        body.setProjectTitle("复用视频");
        body.setIdempotencyKey("reuse-video");
        when(fixture.generation.getStoredJob(eq(601L), any())).thenReturn(
            job(601L, null, DigitalHumanJobType.VOICE_GENERATE, true, INPUT_HASH),
            new DigitalHumanJobDTO(601L, 501L, DigitalHumanJobType.VIDEO_GENERATE,
                DigitalHumanJobStatus.RUNNING, DigitalHumanJobStage.VIDEO_RENDERING, 50,
                true, false, null, null, INPUT_HASH),
            job(601L, 501L, DigitalHumanJobType.VIDEO_GENERATE, false, null));

        for (int attempt = 0; attempt < 3; attempt++) {
            assertThatThrownBy(() -> fixture.service.create(principal(PERMISSIONS), body))
                .isInstanceOf(ServiceException.class)
                .hasMessage("可复用任务不存在或状态无效")
                .extracting(error -> ((ServiceException) error).getCode()).isEqualTo(46704);
        }

        verify(fixture.runs, never()).appendDeliveryBrief(any(), any());
        verifyNoInteractions(fixture.orchestration, fixture.trace);
    }

    @Test
    void acceptsProjectAndRenderShapesButRejectsConflictingKnownFieldsBeforeWrites() {
        Fixture projectFixture = fixture();
        when(projectFixture.projects.getOwned(7L, "701")).thenReturn(project());
        CreateAgentRunBo project = new CreateAgentRunBo();
        project.setStartAt("project");
        project.setProjectId("701");
        project.setExpectedRevision("3");
        project.setIdempotencyKey("reuse-project");
        projectFixture.service.create(principal(PERMISSIONS), project);
        ArgumentCaptor<IAgentRunService.AppendDeliveryBriefCommand> projectBrief =
            ArgumentCaptor.forClass(IAgentRunService.AppendDeliveryBriefCommand.class);
        verify(projectFixture.runs).appendDeliveryBrief(any(), projectBrief.capture());
        assertThat(projectBrief.getValue().briefJson()).isEqualTo(
            "{\"startAt\":\"project\",\"projectId\":\"701\",\"expectedRevision\":\"3\"}");
        verify(projectFixture.projects).getOwned(7L, "701");
        verifyNoInteractions(projectFixture.generation, projectFixture.tools);

        Fixture renderFixture = fixture();
        when(renderFixture.tools.execute(any(), any())).thenReturn(renderStatus("success"));
        CreateAgentRunBo render = new CreateAgentRunBo();
        render.setStartAt("render_task");
        render.setTaskId("801");
        render.setIdempotencyKey("reuse-render");
        renderFixture.service.create(principal(PERMISSIONS), render);
        ArgumentCaptor<IAgentRunService.AppendDeliveryBriefCommand> renderBrief =
            ArgumentCaptor.forClass(IAgentRunService.AppendDeliveryBriefCommand.class);
        ArgumentCaptor<AgentToolDTOs.Call> renderCall = ArgumentCaptor.forClass(AgentToolDTOs.Call.class);
        verify(renderFixture.runs).appendDeliveryBrief(any(), renderBrief.capture());
        verify(renderFixture.tools).execute(any(), renderCall.capture());
        assertThat(renderBrief.getValue().briefJson())
            .isEqualTo("{\"startAt\":\"render_task\",\"taskId\":\"801\"}");
        assertThat(renderCall.getValue().toolName()).isEqualTo("get_timeline_render_status");
        assertThat(renderCall.getValue().arguments().get("taskId").textValue()).isEqualTo("801");
        verifyNoInteractions(renderFixture.generation, renderFixture.projects);

        Fixture invalidFixture = fixture();
        CreateAgentRunBo invalid = new CreateAgentRunBo();
        invalid.setStartAt("render_task");
        invalid.setTaskId("801");
        invalid.setProjectTitle("不允许的已知额外字段");
        invalid.setIdempotencyKey("reuse-render");
        assertThatThrownBy(() -> invalidFixture.service.create(principal(PERMISSIONS), invalid))
            .isInstanceOf(ServiceException.class)
            .extracting(error -> ((ServiceException) error).getCode()).isEqualTo(46702);
        verifyNoInteractions(invalidFixture.runs, invalidFixture.orchestration,
            invalidFixture.trace, invalidFixture.generation, invalidFixture.projects, invalidFixture.tools);
    }

    @Test
    void rejectsInvalidProjectOrRenderFactsBeforeAnyContractWrite() {
        Fixture projectFixture = fixture();
        when(projectFixture.projects.getOwned(7L, "701")).thenReturn(new ICreationProjectService.CreationProjectDTO(
            "701", "T7 复用", "digital_human_job", "601", "711", "712", "ready",
            1080, 1920, 30, 25_800L, 4L, "timeline-1", "901", Instant.EPOCH, Instant.EPOCH));
        CreateAgentRunBo project = new CreateAgentRunBo();
        project.setStartAt("project");
        project.setProjectId("701");
        project.setExpectedRevision("3");
        project.setIdempotencyKey("reuse-project");

        assertReusableFactRejected(() -> projectFixture.service.create(principal(PERMISSIONS), project));
        verify(projectFixture.runs, never()).appendDeliveryBrief(any(), any());
        verifyNoInteractions(projectFixture.orchestration, projectFixture.trace,
            projectFixture.generation, projectFixture.tools);

        Fixture renderFixture = fixture();
        when(renderFixture.tools.execute(any(), any())).thenReturn(renderStatus("running"));
        CreateAgentRunBo render = new CreateAgentRunBo();
        render.setStartAt("render_task");
        render.setTaskId("801");
        render.setIdempotencyKey("reuse-render");

        assertReusableFactRejected(() -> renderFixture.service.create(principal(PERMISSIONS), render));
        verify(renderFixture.runs, never()).appendDeliveryBrief(any(), any());
        verifyNoInteractions(renderFixture.orchestration, renderFixture.trace,
            renderFixture.generation, renderFixture.projects);
    }

    @Test
    void rejectsNonCanonicalOrUnderprivilegedPrincipalBeforeAnyWrite() {
        Fixture fixture = fixture();
        assertThatThrownBy(() -> fixture.service.create(principal(Set.of("aivideo:studio:generate")), createBody()))
            .isInstanceOf(ServiceException.class)
            .extracting(error -> ((ServiceException) error).getCode()).isEqualTo(46703);
        assertThatThrownBy(() -> fixture.service.create(principal(PERMISSIONS, 8L), createBody()))
            .isInstanceOf(ServiceException.class)
            .extracting(error -> ((ServiceException) error).getCode()).isEqualTo(46703);
        verifyNoInteractions(fixture.runs, fixture.orchestration, fixture.trace);
    }

    @Test
    void rejectsEveryReadOrMutationPermissionFailureBeforeAnyPersistenceInteraction() {
        Fixture fixture = fixture();
        AppPrincipalSnapshotDTO generateOnly = principal(Set.of("aivideo:studio:generate"));
        AppPrincipalSnapshotDTO queryOnly = principal(Set.of("aivideo:studio:query"));

        assertForbidden(() -> fixture.service.detail(generateOnly, "103"));
        assertForbidden(() -> fixture.service.advance(generateOnly, "103", revision()));
        assertForbidden(() -> fixture.service.cancel(queryOnly, "103", revision()));
        assertForbidden(() -> fixture.service.decideApproval(queryOnly, "103", "201", decision()));
        assertForbidden(() -> fixture.service.advance(principal(PERMISSIONS, 8L), "103", revision()));

        verifyNoInteractions(fixture.runs, fixture.orchestration, fixture.trace);
    }

    @Test
    void advancesWithServerRequestIdentityAndReturnsImmediateConflictFact() {
        Fixture fixture = fixture();
        AgentRunRevisionBo body = new AgentRunRevisionBo();
        body.setRowVersion(0L);
        body.setContractRevision(1L);
        when(fixture.orchestration.advance(any(), any())).thenReturn(new AgentRunOrchestrationDTOs.AdvanceResult(
            103L, "queued", "state_conflict", null, null, null, List.of(),
            "AGENT_RUN_STATE_CONFLICT", "AgentRun 状态已变化，请重新读取"));

        try (var ignored = AppAuditRequestContextHolder.bindTrusted(
            new AppAuditRequestContext("0123456789abcdef0123456789abcdef", "127.0.0.1"))) {
            var detail = fixture.service.advance(principal(PERMISSIONS), "103", body);
            assertThat(detail.action().errorCode()).isEqualTo("AGENT_RUN_STATE_CONFLICT");
        }

        ArgumentCaptor<AgentRunOrchestrationDTOs.AdvanceCommand> command =
            ArgumentCaptor.forClass(AgentRunOrchestrationDTOs.AdvanceCommand.class);
        var order = inOrder(fixture.runs, fixture.orchestration);
        order.verify(fixture.runs).getOwnedExecutionSnapshot(any(), eq(103L));
        order.verify(fixture.orchestration).advance(any(), any());
        verify(fixture.orchestration).advance(any(), command.capture());
        assertThat(command.getValue().agentRunId()).isEqualTo(103L);
        assertThat(command.getValue().workerId())
            .isEqualTo("agent-http:0123456789abcdef0123456789abcdef");
        verify(fixture.runs, never()).claim(any(), any());
    }

    @Test
    void advancesAReuseRunWithItsFrozenStartPermissionsInsteadOfNewOnlyPermissions() {
        Fixture fixture = fixture();
        when(fixture.runs.getOwnedExecutionSnapshot(any(), eq(103L))).thenReturn(snapshot(
            "{\"startAt\":\"video_job\",\"videoJobId\":\"601\",\"inputHash\":\"" + INPUT_HASH
                + "\",\"projectTitle\":\"复用视频\"}"));
        when(fixture.orchestration.advance(any(), any())).thenReturn(new AgentRunOrchestrationDTOs.AdvanceResult(
            103L, "queued", "advanced", null, null, null, List.of(), null, null));

        try (var ignored = AppAuditRequestContextHolder.bindTrusted(
            new AppAuditRequestContext("0123456789abcdef0123456789abcdef", "127.0.0.1"))) {
            fixture.service.advance(principal(VIDEO_JOB_PERMISSIONS), "103", revision());
        }

        verify(fixture.orchestration).advance(any(), any());
    }

    @Test
    void mapsCrossOwnerBeforeEveryMutationAndDetailToStableNotFound() {
        Fixture fixture = fixture();
        when(fixture.runs.getOwnedRun(any(), eq(103L))).thenThrow(new ServiceException("AgentRun 不存在"));
        when(fixture.runs.getOwnedExecutionSnapshot(any(), eq(103L)))
            .thenThrow(new ServiceException("AgentRun 不存在"));

        assertNotFound(() -> fixture.service.detail(principal(PERMISSIONS), "103"));
        assertNotFound(() -> fixture.service.advance(principal(PERMISSIONS), "103", revision()));
        assertNotFound(() -> fixture.service.cancel(principal(PERMISSIONS), "103", revision()));
        assertNotFound(() -> fixture.service.decideApproval(
            principal(PERMISSIONS), "103", "201", decision()));

        verify(fixture.runs, times(3)).getOwnedRun(any(), eq(103L));
        verify(fixture.runs).getOwnedExecutionSnapshot(any(), eq(103L));
        verifyNoInteractions(fixture.orchestration, fixture.trace);
    }

    @Test
    void mapsUncodedIdempotencyAndStaleApprovalConflictsToStableBoundaryCode() {
        Fixture createFixture = fixture();
        when(createFixture.runs.appendDeliveryBrief(any(), any()))
            .thenThrow(new ServiceException("交付目标幂等键冲突"));

        assertConflict(() -> createFixture.service.create(principal(PERMISSIONS), createBody()));
        verify(createFixture.runs, never()).appendAcceptanceProfile(any(), any());
        verify(createFixture.runs, never()).createRun(any(), any());
        verifyNoInteractions(createFixture.orchestration, createFixture.trace);

        Fixture approvalFixture = fixture();
        when(approvalFixture.orchestration.decideApproval(any(), any()))
            .thenThrow(new ServiceException("AgentRun 批准事实已变化"));

        assertConflict(() -> approvalFixture.service.decideApproval(
            principal(PERMISSIONS), "103", "201", decision()));
        verifyNoInteractions(approvalFixture.trace);
    }

    @Test
    void preservesExistingCodedServiceExceptionAtTheHttpBoundary() {
        Fixture fixture = fixture();
        ServiceException coded = new ServiceException("Agent 审批权限不足", 46703);
        when(fixture.orchestration.cancel(any(), any())).thenThrow(coded);

        assertThatThrownBy(() -> fixture.service.cancel(principal(PERMISSIONS), "103", revision()))
            .isSameAs(coded);
        verifyNoInteractions(fixture.trace);
    }

    private Fixture fixture() {
        IAgentRunService runs = mock(IAgentRunService.class);
        IAgentRunOrchestrationService orchestration = mock(IAgentRunOrchestrationService.class);
        IAgentRunTraceService trace = mock(IAgentRunTraceService.class);
        IDigitalHumanGenerationService generation = mock(IDigitalHumanGenerationService.class);
        ICreationProjectService projects = mock(ICreationProjectService.class);
        IAgentToolService tools = mock(IAgentToolService.class);
        when(runs.appendDeliveryBrief(any(), any())).thenReturn(
            new IAgentRunService.DeliveryBriefVersionView(101L, 201L, 1L, null,
                "delivery-brief-1", "image_to_digital_human_video", "brief-hash"));
        when(runs.appendAcceptanceProfile(any(), any())).thenReturn(
            new IAgentRunService.AcceptanceProfileVersionView(102L, 202L, 101L, 1L, null,
                "acceptance-profile-1", "acceptance-policy-1", "profile-hash"));
        when(runs.createRun(any(), any())).thenReturn(run());
        when(runs.getOwnedRun(any(), anyLong())).thenReturn(run());
        when(runs.getOwnedExecutionSnapshot(any(), anyLong())).thenReturn(snapshot("{\"startAt\":\"new\"}"));
        when(orchestration.plan(any(), anyLong())).thenReturn(plan());
        when(trace.getOwnedTrace(any(), anyLong())).thenReturn(new AgentRunTraceDTO(
            103L, "durable_facts", "queued", 1L, 0L, List.of(
            new AgentRunTraceDTO.Fact(1, "agent_run", 103L, "agent_run", null, "queued",
                null, null, null, null, null, null, null, Instant.EPOCH))));
        return new Fixture(runs, orchestration, trace, generation, projects, tools,
            new AgentRunApplicationServiceImpl(runs, orchestration, trace, generation, projects, tools,
                JsonMapper.builder().build()));
    }

    private CreateAgentRunBo createBody() {
        CreateAgentRunBo body = new CreateAgentRunBo();
        body.setStartAt("new");
        body.setScriptText("固定文案");
        body.setReferenceVoiceId("11");
        body.setPortraitId("12");
        body.setProjectTitle("黄金链");
        body.setIdempotencyKey("client-1");
        return body;
    }

    private AgentRunRevisionBo revision() {
        AgentRunRevisionBo body = new AgentRunRevisionBo();
        body.setRowVersion(0L);
        body.setContractRevision(1L);
        return body;
    }

    private AgentApprovalDecisionBo decision() {
        AgentApprovalDecisionBo body = new AgentApprovalDecisionBo();
        body.setRowVersion(0L);
        body.setContractRevision(1L);
        body.setApprovalRevision(1L);
        body.setType("final");
        body.setApproved(true);
        return body;
    }

    private IAgentRunService.AgentRunView run() {
        return new IAgentRunService.AgentRunView(103L, 101L, 102L, 1L, "queued", 0L, 0L,
            null, null, null, Instant.EPOCH, 0L, 0L, null, 0L,
            null, null, null, null, null);
    }

    private IAgentRunService.ExecutionSnapshot snapshot(String briefJson) {
        return new IAgentRunService.ExecutionSnapshot(run(), briefJson, "brief-hash", "{}", "profile-hash");
    }

    private AgentRunOrchestrationDTOs.PlanResult plan() {
        return new AgentRunOrchestrationDTOs.PlanResult(103L, "new", List.of(
            new AgentRunOrchestrationDTOs.PlanStep(1, "submit_voice", "submit_voice_generation",
                "required", null)), List.of(), List.copyOf(PERMISSIONS), 2, true);
    }

    private DigitalHumanJobDTO job(long jobId, Long parentJobId, DigitalHumanJobType type,
                                   boolean confirmed, String inputHash) {
        return new DigitalHumanJobDTO(jobId, parentJobId, type, DigitalHumanJobStatus.SUCCEEDED,
            DigitalHumanJobStage.COMPLETED, 100, confirmed, true, null, null, inputHash);
    }

    private ICreationProjectService.CreationProjectDTO project() {
        return new ICreationProjectService.CreationProjectDTO(
            "701", "T7 复用", "digital_human_job", "601", "711", "712", "ready",
            1080, 1920, 30, 25_800L, 3L, "timeline-1", "901", Instant.EPOCH, Instant.EPOCH);
    }

    private AgentToolDTOs.RenderStatusResult renderStatus(String status) {
        return new AgentToolDTOs.RenderStatusResult(
            "801", status, "success".equals(status) ? "completed" : "rendering",
            "success".equals(status) ? 100 : 50, "701", "3",
            "success".equals(status) ? "901" : null, false, false, null, null,
            "digital_human_job", "601", "T7 复用");
    }

    private void assertReusableFactRejected(org.assertj.core.api.ThrowableAssert.ThrowingCallable operation) {
        assertThatThrownBy(operation).isInstanceOf(ServiceException.class)
            .hasMessage("可复用任务不存在或状态无效")
            .extracting(error -> ((ServiceException) error).getCode()).isEqualTo(46704);
    }

    private AppPrincipalSnapshotDTO principal(Set<String> permissions) {
        return principal(permissions, 7L);
    }

    private AppPrincipalSnapshotDTO principal(Set<String> permissions, long workspaceOwner) {
        var workspace = new AppWorkspaceSessionSnapshotDTO("workspace", "personal", 1L, "app_user", workspaceOwner,
            "app_user", 7L, "creator", permissions, 1L, null);
        return new AppPrincipalSnapshotDTO(7L, "creator", "desktop-web", 1L, 1L, 1L, 1L, workspace);
    }

    private void assertForbidden(org.assertj.core.api.ThrowableAssert.ThrowingCallable operation) {
        assertThatThrownBy(operation).isInstanceOf(ServiceException.class)
            .extracting(error -> ((ServiceException) error).getCode()).isEqualTo(46703);
    }

    private void assertNotFound(org.assertj.core.api.ThrowableAssert.ThrowingCallable operation) {
        assertThatThrownBy(operation).isInstanceOf(ServiceException.class)
            .hasMessage("AgentRun 不存在")
            .extracting(error -> ((ServiceException) error).getCode()).isEqualTo(46704);
    }

    private void assertConflict(org.assertj.core.api.ThrowableAssert.ThrowingCallable operation) {
        assertThatThrownBy(operation).isInstanceOf(ServiceException.class)
            .hasMessage("Agent 请求已冲突，请刷新后重试")
            .extracting(error -> ((ServiceException) error).getCode()).isEqualTo(46705);
    }

    private record Fixture(
        IAgentRunService runs,
        IAgentRunOrchestrationService orchestration,
        IAgentRunTraceService trace,
        IDigitalHumanGenerationService generation,
        ICreationProjectService projects,
        IAgentToolService tools,
        AgentRunApplicationServiceImpl service
    ) {
    }
}
