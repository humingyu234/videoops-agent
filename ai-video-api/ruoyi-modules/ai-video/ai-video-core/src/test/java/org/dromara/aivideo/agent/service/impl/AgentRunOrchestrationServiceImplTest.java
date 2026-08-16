package org.dromara.aivideo.agent.service.impl;

import org.dromara.aivideo.agent.dto.AgentRunOrchestrationDTOs;
import org.dromara.aivideo.agent.dto.AgentToolDTOs;
import org.dromara.aivideo.agent.service.IAgentRunService;
import org.dromara.aivideo.agent.service.IAgentToolService;
import org.dromara.aivideo.identity.dto.AppPrincipalSnapshotDTO;
import org.dromara.aivideo.identity.dto.AppWorkspaceSessionSnapshotDTO;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("dev")
class AgentRunOrchestrationServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-08-16T00:00:00Z");
    private static final Set<String> ALL_PERMISSIONS = Set.of(
        "aivideo:studio:generate", "aivideo:studio:query", "aivideo:voice:query",
        "aivideo:portrait:query", "aivideo:creation:edit", "aivideo:creation:generate",
        "aivideo:task:query", "aivideo:creation-asset:query");

    @Mock
    private IAgentRunService runService;
    @Mock
    private IAgentToolService toolService;

    @Test
    void plansAllFiveStartsAsNineClosedStepsWithOnlyPriorWorkSkipped() {
        AppPrincipalSnapshotDTO principal = principal(ALL_PERMISSIONS);
        List<String> briefs = List.of(
            briefNew(),
            "{\"startAt\":\"voice_job\",\"voiceJobId\":\"501\",\"portraitId\":\"401\",\"projectTitle\":\"黄金链\"}",
            "{\"startAt\":\"video_job\",\"videoJobId\":\"601\",\"projectTitle\":\"黄金链\"}",
            "{\"startAt\":\"project\",\"projectId\":\"701\",\"expectedRevision\":\"3\"}",
            "{\"startAt\":\"render_task\",\"taskId\":\"801\"}"
        );
        List<Integer> skipped = List.of(0, 1, 4, 6, 7);
        List<Integer> providerSubmissions = List.of(2, 1, 0, 0, 0);

        for (int index = 0; index < briefs.size(); index++) {
            when(runService.getOwnedExecutionSnapshot(principal, 1001L))
                .thenReturn(snapshot(run("queued", 0, null, null, 0), briefs.get(index), profile(2, 1)));

            AgentRunOrchestrationDTOs.PlanResult plan = service().plan(principal, 1001L);

            assertThat(plan.executable()).isTrue();
            assertThat(plan.steps()).hasSize(9);
            assertThat(plan.steps()).filteredOn(step -> "skipped".equals(step.disposition()))
                .hasSize(skipped.get(index));
            assertThat(plan.steps()).allMatch(step -> Set.of("required", "skipped").contains(step.disposition()));
            assertThat(plan.requiredProviderSubmissions()).isEqualTo(providerSubmissions.get(index));
        }
    }

    @Test
    void missingExtraWrongPermissionAndProviderBudgetBlockBeforeClaimOrTool() {
        AppPrincipalSnapshotDTO principal = principal(ALL_PERMISSIONS);
        AppPrincipalSnapshotDTO missingPermission = principal(Set.of("aivideo:task:query"));
        List<IAgentRunService.ExecutionSnapshot> invalid = List.of(
            snapshot(run("queued", 0, null, null, 0),
                "{\"startAt\":\"new\",\"referenceVoiceId\":\"301\",\"portraitId\":\"401\",\"projectTitle\":\"黄金链\"}",
                profile(2, 1)),
            snapshot(run("queued", 0, null, null, 0),
                "{\"startAt\":\"render_task\",\"taskId\":\"801\",\"ownerId\":\"1001\"}", profile(2, 1)),
            snapshot(run("queued", 0, null, null, 0),
                "{\"startAt\":\"new\",\"scriptText\":\"固定文案\",\"referenceVoiceId\":301,\"portraitId\":\"401\",\"projectTitle\":\"黄金链\"}",
                profile(2, 1)),
            snapshot(run("queued", 0, null, null, 0), briefNew(), profile(1, 1))
        );
        when(runService.blockForInput(any(), any())).thenReturn(true);
        when(runService.getOwnedExecutionSnapshot(eq(principal), eq(1001L)))
            .thenReturn(invalid.get(0), invalid.get(1), invalid.get(2), invalid.get(3));
        when(runService.getOwnedExecutionSnapshot(missingPermission, 1001L))
            .thenReturn(snapshot(run("queued", 0, null, null, 0), briefNew(), profile(2, 1)));

        for (int index = 0; index < invalid.size(); index++) {
            assertThat(service().advance(principal, command())).extracting(AgentRunOrchestrationDTOs.AdvanceResult::outcome)
                .isEqualTo("blocked");
        }
        assertThat(service().advance(missingPermission, command())).extracting(
            AgentRunOrchestrationDTOs.AdvanceResult::outcome).isEqualTo("blocked");

        verifyNoInteractions(toolService);
        verify(runService, never()).claim(any(), any());
    }

    @Test
    void newStartSubmitsVoiceWithStableKeyAndParksEvenWhenAlreadySucceeded() {
        AppPrincipalSnapshotDTO principal = principal(ALL_PERMISSIONS);
        IAgentRunService.AgentRunView run = run("queued", 0, null, null, 0);
        when(runService.getOwnedExecutionSnapshot(principal, 1001L))
            .thenReturn(snapshot(run, briefNew(), profile(2, 1)));
        when(runService.claim(eq(principal), any())).thenReturn(lease(null, null));
        when(toolService.execute(eq(principal), any())).thenReturn(voice("succeeded", true, true));
        when(runService.waitForExternalTask(eq(principal), any())).thenReturn(waiting("digital_human_generation", 501L));

        AgentRunOrchestrationDTOs.AdvanceResult result = service().advance(principal, command());

        assertThat(result.runStatus()).isEqualTo("waiting_external_task");
        ArgumentCaptor<AgentToolDTOs.Call> call = ArgumentCaptor.forClass(AgentToolDTOs.Call.class);
        verify(toolService).execute(eq(principal), call.capture());
        assertThat(call.getValue().toolName()).isEqualTo("submit_voice_generation");
        assertThat(call.getValue().arguments().get("idempotencyKey").textValue())
            .isEqualTo("agent-run:1001:voice:0");
    }

    @Test
    void waitingRecoveryQueriesThePersistedTaskAndDefersWithoutAnySubmit() {
        AppPrincipalSnapshotDTO principal = principal(ALL_PERMISSIONS);
        when(runService.getOwnedExecutionSnapshot(principal, 1001L)).thenReturn(snapshot(
            run("waiting_external_task", 0, "digital_human_generation", 501L, 0), briefNew(), profile(2, 1)));
        when(runService.claim(eq(principal), any())).thenReturn(lease("digital_human_generation", 501L));
        when(toolService.execute(eq(principal), any())).thenReturn(voice("running", false, false));
        when(runService.deferExternalTask(eq(principal), any())).thenReturn(waiting("digital_human_generation", 501L));

        AgentRunOrchestrationDTOs.AdvanceResult result = service().advance(principal, command());

        assertThat(result.waitingTaskId()).isEqualTo(501L);
        ArgumentCaptor<AgentToolDTOs.Call> call = ArgumentCaptor.forClass(AgentToolDTOs.Call.class);
        verify(toolService).execute(eq(principal), call.capture());
        assertThat(call.getAllValues()).extracting(AgentToolDTOs.Call::toolName)
            .containsExactly("get_generation_status");
        verify(runService).deferExternalTask(eq(principal), any());
        verify(runService, never()).advanceExternalTask(any(), any());
    }

    @Test
    void maxOneAllowsInitialClaimAndFirstFreshRecoveryThenStopsBeforeASecondRecovery() {
        AppPrincipalSnapshotDTO principal = principal(ALL_PERMISSIONS);
        when(runService.getOwnedExecutionSnapshot(principal, 1001L)).thenReturn(
            snapshot(run("queued", 0, null, null, 0, NOW.minusSeconds(10), 0),
                briefNew(), profile(2, 1, 3_600, 1)),
            snapshot(run("waiting_external_task", 0, "digital_human_generation", 501L, 0,
                    NOW.minusSeconds(10), 1), briefNew(), profile(2, 1, 3_600, 1)),
            snapshot(run("waiting_external_task", 0, "digital_human_generation", 501L, 0,
                    NOW.minusSeconds(10), 2), briefNew(), profile(2, 1, 3_600, 1)));
        when(runService.claim(eq(principal), any())).thenReturn(
            lease(null, null), lease("digital_human_generation", 501L));
        when(toolService.execute(eq(principal), any())).thenReturn(
            voice("queued", false, false), voice("running", false, false));
        when(runService.waitForExternalTask(eq(principal), any()))
            .thenReturn(waiting("digital_human_generation", 501L));
        when(runService.deferExternalTask(eq(principal), any()))
            .thenReturn(waiting("digital_human_generation", 501L));
        when(runService.stopOwnedRun(eq(principal), any())).thenReturn(true);

        AgentRunOrchestrationDTOs.AdvanceResult initial = service().advance(principal, command());
        AgentRunOrchestrationDTOs.AdvanceResult firstRecovery = service().advance(principal, command());
        AgentRunOrchestrationDTOs.AdvanceResult exhausted = service().advance(principal, command());

        assertThat(initial.waitingTaskId()).isEqualTo(501L);
        assertThat(firstRecovery.waitingTaskId()).isEqualTo(501L);
        assertThat(exhausted.errorCode()).isEqualTo("AGENT_RESUME_BUDGET_EXHAUSTED");
        verify(runService, times(2)).claim(eq(principal), any());
        ArgumentCaptor<AgentToolDTOs.Call> calls = ArgumentCaptor.forClass(AgentToolDTOs.Call.class);
        verify(toolService, times(2)).execute(eq(principal), calls.capture());
        assertThat(calls.getAllValues()).extracting(AgentToolDTOs.Call::toolName)
            .containsExactly("submit_voice_generation", "get_generation_status");
        verify(runService, times(1)).waitForExternalTask(eq(principal), any());
        verify(runService, times(1)).deferExternalTask(eq(principal), any());
    }

    @Test
    void successfulWaitingVoiceConfirmsThenSubmitsOneVideoAndAtomicallyAdvances() {
        AppPrincipalSnapshotDTO principal = principal(ALL_PERMISSIONS);
        when(runService.getOwnedExecutionSnapshot(principal, 1001L)).thenReturn(snapshot(
            run("waiting_external_task", 0, "digital_human_generation", 501L, 0), briefNew(), profile(2, 1)));
        when(runService.claim(eq(principal), any())).thenReturn(lease("digital_human_generation", 501L));
        when(toolService.execute(eq(principal), any())).thenReturn(
            voice("succeeded", false, true), voice("succeeded", true, true), video("queued"));
        when(runService.advanceExternalTask(eq(principal), any()))
            .thenReturn(waiting("digital_human_generation", 601L));

        AgentRunOrchestrationDTOs.AdvanceResult result = service().advance(principal, command());

        assertThat(result.waitingTaskId()).isEqualTo(601L);
        assertToolNames(principal, "get_generation_status", "confirm_voice_generation",
            "submit_digital_human_video");
        ArgumentCaptor<IAgentRunService.AdvanceExternalTaskCommand> command =
            ArgumentCaptor.forClass(IAgentRunService.AdvanceExternalTaskCommand.class);
        verify(runService).advanceExternalTask(eq(principal), command.capture());
        assertThat(command.getValue().completedTaskId()).isEqualTo(501L);
        assertThat(command.getValue().nextTaskId()).isEqualTo(601L);
    }

    @Test
    void successfulWaitingVideoPreparesProjectAndRenderThenAtomicallyAdvances() {
        AppPrincipalSnapshotDTO principal = principal(ALL_PERMISSIONS);
        when(runService.getOwnedExecutionSnapshot(principal, 1001L)).thenReturn(snapshot(
            run("waiting_external_task", 0, "digital_human_generation", 601L, 0),
            "{\"startAt\":\"video_job\",\"videoJobId\":\"601\",\"projectTitle\":\"黄金链\"}",
            profile(2, 1)));
        when(runService.claim(eq(principal), any())).thenReturn(lease("digital_human_generation", 601L));
        when(toolService.execute(eq(principal), any())).thenReturn(
            video("succeeded"), project(), renderTask("801"));
        when(runService.advanceExternalTask(eq(principal), any())).thenReturn(waiting("ai_task", 801L));

        AgentRunOrchestrationDTOs.AdvanceResult result = service().advance(principal, command());

        assertThat(result.waitingTaskSource()).isEqualTo("ai_task");
        assertToolNames(principal, "get_generation_status", "prepare_timeline_project", "render_timeline");
    }

    @Test
    void exactDeadlineAndResumeBudgetStopBeforeClaimOrTool() {
        AppPrincipalSnapshotDTO principal = principal(ALL_PERMISSIONS);
        IAgentRunService.AgentRunView deadline = run("queued", 0, null, null, 0, NOW.minusSeconds(60), 0);
        IAgentRunService.AgentRunView budget = run("queued", 0, null, null, 0, NOW.minusSeconds(1), 4);
        when(runService.getOwnedExecutionSnapshot(principal, 1001L)).thenReturn(
            snapshot(deadline, briefNew(), profile(2, 1, 60, 3)),
            snapshot(budget, briefNew(), profile(2, 1, 60, 3)));
        when(runService.stopOwnedRun(eq(principal), any())).thenReturn(true);

        assertThat(service().advance(principal, command()).errorCode()).isEqualTo("AGENT_RUN_TIMEOUT");
        assertThat(service().advance(principal, command()).errorCode())
            .isEqualTo("AGENT_RESUME_BUDGET_EXHAUSTED");

        verifyNoInteractions(toolService);
        verify(runService, never()).claim(any(), any());
    }

    @Test
    void retryableRenderUsesOnePersistedAttemptThenTheSameFailureTurnsManual() {
        AppPrincipalSnapshotDTO principal = principal(ALL_PERMISSIONS);
        IAgentRunService.AgentRunView first = run("waiting_external_task", 0, "ai_task", 801L, 0);
        when(runService.getOwnedExecutionSnapshot(principal, 1001L)).thenReturn(
            snapshot(first, "{\"startAt\":\"render_task\",\"taskId\":\"801\"}", profile(0, 1)),
            snapshot(run("waiting_external_task", 1, "ai_task", 802L, 0),
                "{\"startAt\":\"render_task\",\"taskId\":\"801\"}", profile(0, 1)));
        when(runService.claim(eq(principal), any())).thenReturn(
            lease("ai_task", 801L), lease("ai_task", 802L));
        when(toolService.execute(eq(principal), any())).thenReturn(
            failedRender("801", true), renderTask("802"), failedRender("802", true));
        when(runService.retryExternalTask(eq(principal), any())).thenReturn(waiting("ai_task", 802L));
        when(runService.stopOwnedRun(eq(principal), any())).thenReturn(true);

        AgentRunOrchestrationDTOs.AdvanceResult retried = service().advance(principal, command());
        AgentRunOrchestrationDTOs.AdvanceResult manual = service().advance(principal, command(0));

        assertThat(retried.waitingTaskId()).isEqualTo(802L);
        assertThat(manual.outcome()).isEqualTo("manual_required");
        assertThat(manual.errorCode()).isEqualTo("TIMEOUT");
        ArgumentCaptor<AgentToolDTOs.Call> calls = ArgumentCaptor.forClass(AgentToolDTOs.Call.class);
        verify(toolService, atLeastOnce()).execute(eq(principal), calls.capture());
        AgentToolDTOs.Call retryCall = calls.getAllValues().stream()
            .filter(call -> "render_timeline".equals(call.toolName())).findFirst().orElseThrow();
        assertThat(retryCall.arguments().get("idempotencyKey").textValue())
            .isEqualTo("agent-run:1001:render:1");
        verify(runService).retryExternalTask(eq(principal), any());
    }

    @Test
    void cancellationAndLaterAdvanceOfTheTerminalRunNeverTouchTools() {
        AppPrincipalSnapshotDTO principal = principal(ALL_PERMISSIONS);
        IAgentRunService.AgentRunView waiting = run("waiting_external_task", 0, "ai_task", 801L, 0);
        IAgentRunService.AgentRunView cancelled = run("cancelled", 0, null, null, 0);
        when(runService.getOwnedExecutionSnapshot(principal, 1001L)).thenReturn(
            snapshot(waiting, briefNew(), profile(2, 1)), snapshot(cancelled, briefNew(), profile(2, 1)));
        when(runService.stopOwnedRun(eq(principal), any())).thenReturn(true);

        AgentRunOrchestrationDTOs.AdvanceResult cancellation = service().cancel(principal,
            new AgentRunOrchestrationDTOs.CancelCommand(1001L, 0, 1));
        AgentRunOrchestrationDTOs.AdvanceResult late = service().advance(principal, command());

        assertThat(cancellation.runStatus()).isEqualTo("cancelled");
        assertThat(late.outcome()).isEqualTo("terminal");
        verifyNoInteractions(toolService);
        verify(runService, never()).claim(any(), any());
    }

    @Test
    void successfulRenderInspectionCompletesTheExactWaitingTaskAndOwnedAsset() {
        AppPrincipalSnapshotDTO principal = principal(ALL_PERMISSIONS);
        when(runService.getOwnedExecutionSnapshot(principal, 1001L)).thenReturn(snapshot(
            run("waiting_external_task", 0, "ai_task", 801L, 0),
            "{\"startAt\":\"render_task\",\"taskId\":\"801\"}", profile(0, 1)));
        when(runService.claim(eq(principal), any())).thenReturn(lease("ai_task", 801L));
        when(toolService.execute(eq(principal), any())).thenReturn(successRender(), output());
        when(runService.completeExternalTask(eq(principal), any())).thenReturn(true);

        AgentRunOrchestrationDTOs.AdvanceResult result = service().advance(principal, command());

        assertThat(result.runStatus()).isEqualTo("completed");
        assertThat(result.candidateAssetId()).isEqualTo(901L);
        assertToolNames(principal, "get_timeline_render_status", "inspect_timeline_output");
        ArgumentCaptor<IAgentRunService.CompleteExternalTaskCommand> completion =
            ArgumentCaptor.forClass(IAgentRunService.CompleteExternalTaskCommand.class);
        verify(runService).completeExternalTask(eq(principal), completion.capture());
        assertThat(completion.getValue().taskId()).isEqualTo(801L);
        assertThat(completion.getValue().candidateAssetId()).isEqualTo(901L);
    }

    private AgentRunOrchestrationServiceImpl service() {
        return new AgentRunOrchestrationServiceImpl(runService, toolService, JsonMapper.builder().build(),
            Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private AppPrincipalSnapshotDTO principal(Set<String> permissions) {
        return new AppPrincipalSnapshotDTO(1001L, "creator", "desktop", 1L, 1L, 1L, 1L,
            new AppWorkspaceSessionSnapshotDTO("workspace-key", "personal", 2001L, "app_user", 1001L,
                "app_user", 1001L, "personal_creator", permissions, 1L, null));
    }

    private AgentRunOrchestrationDTOs.AdvanceCommand command() {
        return command(0);
    }

    private AgentRunOrchestrationDTOs.AdvanceCommand command(long expectedRowVersion) {
        return new AgentRunOrchestrationDTOs.AdvanceCommand(1001L, expectedRowVersion, 1L, "worker-1");
    }

    private IAgentRunService.ExecutionSnapshot snapshot(IAgentRunService.AgentRunView run, String brief,
                                                         String profile) {
        return new IAgentRunService.ExecutionSnapshot(run, brief, "brief-hash", profile, "profile-hash");
    }

    private IAgentRunService.AgentRunView run(String status, long retryCount, String waitingSource,
                                               Long waitingId, long candidateAssetId) {
        return run(status, retryCount, waitingSource, waitingId, candidateAssetId, NOW.minusSeconds(10), 0);
    }

    private IAgentRunService.AgentRunView run(String status, long retryCount, String waitingSource,
                                               Long waitingId, long candidateAssetId, Instant startedAt,
                                               long leaseGeneration) {
        return new IAgentRunService.AgentRunView(1001L, 1101L, 1201L, 1L, status, 0L, leaseGeneration,
            waitingSource, waitingId, candidateAssetId == 0 ? null : candidateAssetId, NOW.minusSeconds(10),
            retryCount, startedAt, NOW.plusSeconds(5), null, null, null);
    }

    private IAgentRunService.AgentRunLease lease(String waitingSource, Long waitingId) {
        return new IAgentRunService.AgentRunLease(1001L, 1L, 1L, 1L, "opaque-proof",
            NOW.plusSeconds(60), waitingSource, waitingId);
    }

    private IAgentRunService.WaitingReceipt waiting(String source, long taskId) {
        return new IAgentRunService.WaitingReceipt(new IAgentRunService.LeaseProof(
            1001L, 2L, 1L, 1L, "opaque-proof"), source, taskId, NOW.plusSeconds(5));
    }

    private AgentToolDTOs.GenerationJobResult voice(String status, boolean confirmed, boolean output) {
        return new AgentToolDTOs.GenerationJobResult("501", null, "voice_generate", status,
            "succeeded".equals(status) ? "completed" : status, "succeeded".equals(status) ? 100 : 20,
            confirmed, output, null, null);
    }

    private AgentToolDTOs.GenerationJobResult video(String status) {
        return new AgentToolDTOs.GenerationJobResult("601", "501", "video_generate", status,
            "succeeded".equals(status) ? "completed" : status, "succeeded".equals(status) ? 100 : 20,
            true, "succeeded".equals(status), null, null);
    }

    private AgentToolDTOs.ProjectResult project() {
        return new AgentToolDTOs.ProjectResult("701", "editing", "3", 1080, 1920, 30, 25_800L);
    }

    private AgentToolDTOs.RenderTaskResult renderTask(String taskId) {
        return new AgentToolDTOs.RenderTaskResult(taskId, "queued", "queued", "701", "3");
    }

    private AgentToolDTOs.RenderStatusResult failedRender(String taskId, boolean retryable) {
        return new AgentToolDTOs.RenderStatusResult(taskId, "failed", "failed", 80, "701", "3", null,
            false, retryable, "TIMEOUT", "视频渲染超时，请稍后重试");
    }

    private AgentToolDTOs.RenderStatusResult successRender() {
        return new AgentToolDTOs.RenderStatusResult("801", "success", "success", 100, "701", "3", "901",
            false, false, null, null);
    }

    private AgentToolDTOs.OutputInspectionResult output() {
        return new AgentToolDTOs.OutputInspectionResult("801", "901", "ready", "video",
            "timeline_render_output", "video/mp4", "a".repeat(64), 5_244_591L, 25_800L, 1080, 1920,
            true, true, "/api/studio/creation-assets/901/content");
    }

    private String briefNew() {
        return "{\"startAt\":\"new\",\"scriptText\":\"固定文案\",\"referenceVoiceId\":\"301\","
            + "\"portraitId\":\"401\",\"projectTitle\":\"黄金链\"}";
    }

    private String profile(int providerSubmissions, int renderRetries) {
        return profile(providerSubmissions, renderRetries, 3_600, 20);
    }

    private String profile(int providerSubmissions, int renderRetries, int maxRunSeconds,
                           int maxResumeAttempts) {
        return "{\"maxRunSeconds\":" + maxRunSeconds + ",\"maxResumeAttempts\":" + maxResumeAttempts
            + ",\"maxProviderSubmissions\":" + providerSubmissions + ",\"maxRenderRetries\":"
            + renderRetries + ",\"pollIntervalSeconds\":5}";
    }

    private void assertToolNames(AppPrincipalSnapshotDTO principal, String... expected) {
        ArgumentCaptor<AgentToolDTOs.Call> calls = ArgumentCaptor.forClass(AgentToolDTOs.Call.class);
        verify(toolService, atLeastOnce()).execute(eq(principal), calls.capture());
        assertThat(calls.getAllValues()).extracting(AgentToolDTOs.Call::toolName).containsExactly(expected);
    }
}
