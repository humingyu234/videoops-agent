package org.dromara.aivideo.agent.service.impl;

import org.dromara.aivideo.agent.dto.AgentRunOrchestrationDTOs;
import org.dromara.aivideo.agent.dto.AgentToolDTOs;
import org.dromara.aivideo.agent.service.IAgentRunService;
import org.dromara.aivideo.agent.service.IAgentToolService;
import org.dromara.aivideo.identity.dto.AppPrincipalSnapshotDTO;
import org.dromara.aivideo.identity.dto.AppWorkspaceSessionSnapshotDTO;
import org.dromara.aivideo.timeline.dto.TimelineOutputQualityDTO;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    private static final String INPUT_HASH = "a".repeat(64);
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
            "{\"startAt\":\"voice_job\",\"voiceJobId\":\"501\",\"inputHash\":\"" + INPUT_HASH
                + "\",\"portraitId\":\"401\",\"projectTitle\":\"黄金链\"}",
            "{\"startAt\":\"video_job\",\"videoJobId\":\"601\",\"inputHash\":\"" + INPUT_HASH
                + "\",\"projectTitle\":\"黄金链\"}",
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
    void initialApprovalFreezesTheContractBeforeTheFirstToolSideEffect() {
        AppPrincipalSnapshotDTO principal = principal(ALL_PERMISSIONS);
        IAgentRunService.AgentRunView unapproved = run("queued", 0, 0, null, null, 0,
            NOW.minusSeconds(10), 0, null, 0);
        when(runService.getOwnedExecutionSnapshot(principal, 1001L))
            .thenReturn(snapshot(unapproved, briefNew(), profile(2, 1)));
        when(runService.requestInitialApproval(eq(principal), any())).thenReturn(approval("initial"));

        AgentRunOrchestrationDTOs.AdvanceResult result = service().advance(principal, command());

        assertThat(result.runStatus()).isEqualTo("waiting_approval");
        assertThat(result.approvalType()).isEqualTo("initial");
        assertThat(result.pendingApprovalId()).isEqualTo(1301L);
        verifyNoInteractions(toolService);
        verify(runService, never()).claim(any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"initial", "conditional", "final"})
    void approvalRejectsRevokedPermissionsAndNonCanonicalWorkspaceBeforeMutation(String approvalType) {
        AppPrincipalSnapshotDTO missingPermission = principal(Set.of("aivideo:task:query"));
        AppPrincipalSnapshotDTO nonCanonical = new AppPrincipalSnapshotDTO(
            1001L, "creator", "desktop", 1L, 1L, 1L, 1L, null);
        IAgentRunService.ExecutionSnapshot snapshot = snapshot(
            run("waiting_approval", 0, 0, null, null, 901L, NOW.minusSeconds(10), 0, 1301L, 1L),
            briefNew(), profile(2, 1));
        when(runService.getOwnedExecutionSnapshot(missingPermission, 1001L)).thenReturn(snapshot);
        when(runService.getOwnedExecutionSnapshot(nonCanonical, 1001L)).thenReturn(snapshot);
        AgentRunOrchestrationDTOs.ApprovalCommand command = new AgentRunOrchestrationDTOs.ApprovalCommand(
            1001L, 0L, 1L, 1301L, 1L, approvalType, true);

        assertThatThrownBy(() -> service().decideApproval(missingPermission, command))
            .isInstanceOf(ServiceException.class)
            .extracting(exception -> ((ServiceException) exception).getCode())
            .isEqualTo(46703);
        assertThatThrownBy(() -> service().decideApproval(nonCanonical, command))
            .isInstanceOf(ServiceException.class)
            .extracting(exception -> ((ServiceException) exception).getCode())
            .isEqualTo(46703);

        verify(runService, never()).decideApproval(any(), any());
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
    void succeededVideoReuseQueriesAndParksTheExactStoredJobWithoutAnySubmit() {
        AppPrincipalSnapshotDTO principal = principal(ALL_PERMISSIONS);
        String brief = "{\"startAt\":\"video_job\",\"videoJobId\":\"601\",\"inputHash\":\""
            + INPUT_HASH + "\",\"projectTitle\":\"黄金链\"}";
        when(runService.getOwnedExecutionSnapshot(principal, 1001L)).thenReturn(snapshot(
            run("queued", 0, null, null, 0), brief, profile(0, 1)));
        when(runService.claim(eq(principal), any())).thenReturn(lease(null, null));
        when(toolService.execute(eq(principal), any())).thenReturn(video("succeeded"));
        when(runService.waitForExternalTask(eq(principal), any()))
            .thenReturn(waiting("digital_human_generation", 601L));

        AgentRunOrchestrationDTOs.AdvanceResult result = service().advance(principal, command());

        assertThat(result.waitingTaskId()).isEqualTo(601L);
        ArgumentCaptor<AgentToolDTOs.Call> call = ArgumentCaptor.forClass(AgentToolDTOs.Call.class);
        verify(toolService).execute(eq(principal), call.capture());
        assertThat(call.getValue().toolName()).isEqualTo("get_generation_status");
        assertThat(call.getValue().arguments().get("jobId").textValue()).isEqualTo("601");
    }

    @Test
    void reuseRejectsWrongTypeNonSucceededOrInputHashMismatchBeforeParking() {
        AppPrincipalSnapshotDTO principal = principal(ALL_PERMISSIONS);
        String voiceBrief = "{\"startAt\":\"voice_job\",\"voiceJobId\":\"501\",\"inputHash\":\""
            + INPUT_HASH + "\",\"portraitId\":\"401\",\"projectTitle\":\"黄金链\"}";
        String videoBrief = "{\"startAt\":\"video_job\",\"videoJobId\":\"601\",\"inputHash\":\""
            + INPUT_HASH + "\",\"projectTitle\":\"黄金链\"}";
        when(runService.getOwnedExecutionSnapshot(principal, 1001L)).thenReturn(
            snapshot(run("queued", 0, null, null, 0), voiceBrief, profile(1, 1)),
            snapshot(run("queued", 0, null, null, 0), videoBrief, profile(0, 1)),
            snapshot(run("queued", 0, null, null, 0), videoBrief, profile(0, 1)));
        when(runService.claim(eq(principal), any())).thenReturn(
            lease(null, null), lease(null, null), lease(null, null));
        AgentToolDTOs.GenerationJobResult wrongType = video("succeeded");
        AgentToolDTOs.GenerationJobResult running = video("running");
        AgentToolDTOs.GenerationJobResult wrongHash = new AgentToolDTOs.GenerationJobResult(
            "601", "501", "video_generate", "succeeded", "completed", 100,
            true, true, null, null, "b".repeat(64));
        when(toolService.execute(eq(principal), any())).thenReturn(wrongType, running, wrongHash);
        when(runService.stopOwnedRun(eq(principal), any())).thenReturn(true);

        assertThat(service().advance(principal, command()).errorCode()).isEqualTo("AGENT_TOOL_RESULT_INVALID");
        assertThat(service().advance(principal, command()).errorCode()).isEqualTo("AGENT_TOOL_RESULT_INVALID");
        assertThat(service().advance(principal, command()).errorCode()).isEqualTo("AGENT_TOOL_RESULT_INVALID");

        verify(runService, never()).waitForExternalTask(any(), any());
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
            "{\"startAt\":\"video_job\",\"videoJobId\":\"601\",\"inputHash\":\"" + INPUT_HASH
                + "\",\"projectTitle\":\"黄金链\"}",
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
    void successfulRenderInspectionRequestsFinalApprovalForTheExactCandidate() {
        AppPrincipalSnapshotDTO principal = principal(ALL_PERMISSIONS);
        when(runService.getOwnedExecutionSnapshot(principal, 1001L)).thenReturn(snapshot(
            run("waiting_external_task", 0, "ai_task", 801L, 0),
            "{\"startAt\":\"render_task\",\"taskId\":\"801\"}", profile(0, 1)));
        when(runService.claim(eq(principal), any())).thenReturn(lease("ai_task", 801L));
        when(toolService.execute(eq(principal), any())).thenReturn(successRender(), output());
        when(runService.recordQualityEvaluation(eq(principal), any())).thenReturn(evaluation(0, "final", "none"));
        when(runService.requestQualityApproval(eq(principal), any())).thenReturn(approval("final"));

        AgentRunOrchestrationDTOs.AdvanceResult result = service().advance(principal, command());

        assertThat(result.runStatus()).isEqualTo("waiting_approval");
        assertThat(result.outcome()).isEqualTo("approval_required");
        assertThat(result.candidateAssetId()).isEqualTo(901L);
        assertThat(result.approvalType()).isEqualTo("final");
        assertToolNames(principal, "get_timeline_render_status", "inspect_timeline_output");
        verify(runService).recordQualityEvaluation(eq(principal), any());
        verify(runService).requestQualityApproval(eq(principal), any());
        verify(runService, never()).completeExternalTask(any(), any());
    }

    @Test
    void subtitleFailureCreatesOnlyOneSameVideoProjectAndRenderRepair() {
        AppPrincipalSnapshotDTO principal = principal(ALL_PERMISSIONS);
        when(runService.getOwnedExecutionSnapshot(principal, 1001L)).thenReturn(snapshot(
            run("waiting_external_task", 1, "ai_task", 801L, 0),
            "{\"startAt\":\"render_task\",\"taskId\":\"801\"}", profile(0, 1)));
        when(runService.claim(eq(principal), any())).thenReturn(lease("ai_task", 801L));
        AgentToolDTOs.ProjectResult repairedProject = new AgentToolDTOs.ProjectResult(
            "702", "editing", "1", 1080, 1920, 30, 25_800L);
        AgentToolDTOs.RenderTaskResult repairedRender = new AgentToolDTOs.RenderTaskResult(
            "802", "queued", "queued", "702", "1");
        when(toolService.execute(eq(principal), any())).thenReturn(
            successRender(), output(Set.of("subtitle.timing")), repairedProject, repairedRender);
        when(runService.recordQualityEvaluation(eq(principal), any()))
            .thenReturn(evaluation(0, "repair", "timeline_render", quality(Set.of("subtitle.timing"))));
        when(runService.startQualityRepair(eq(principal), any())).thenReturn(
            new IAgentRunService.QualityRepairReceipt(waiting("ai_task", 802L), 1401L, 1L,
                "timeline_render"));

        AgentRunOrchestrationDTOs.AdvanceResult result = service().advance(principal, command());

        assertThat(result.waitingTaskId()).isEqualTo(802L);
        ArgumentCaptor<AgentToolDTOs.Call> calls = ArgumentCaptor.forClass(AgentToolDTOs.Call.class);
        verify(toolService, times(4)).execute(eq(principal), calls.capture());
        assertThat(calls.getAllValues()).extracting(AgentToolDTOs.Call::toolName).containsExactly(
            "get_timeline_render_status", "inspect_timeline_output", "prepare_timeline_project",
            "render_timeline");
        AgentToolDTOs.Call projectCall = calls.getAllValues().get(2);
        assertThat(projectCall.arguments().get("videoJobId").textValue()).isEqualTo("601");
        assertThat(projectCall.arguments().get("idempotencyKey").textValue())
            .isEqualTo("agent-run:1001:repair-project:1");
        assertThat(calls.getAllValues().get(3).arguments().get("idempotencyKey").textValue())
            .isEqualTo("agent-run:1001:repair-render:1");
        verify(runService).startQualityRepair(eq(principal), any());
    }

    @Test
    void highConfidenceMediaFailureCreatesOnlyOneRenderRepair() {
        AppPrincipalSnapshotDTO principal = principal(ALL_PERMISSIONS);
        when(runService.getOwnedExecutionSnapshot(principal, 1001L)).thenReturn(snapshot(
            run("waiting_external_task", 1, "ai_task", 801L, 0),
            "{\"startAt\":\"render_task\",\"taskId\":\"801\"}", profile(0, 1)));
        when(runService.claim(eq(principal), any())).thenReturn(lease("ai_task", 801L));
        AgentToolDTOs.RenderTaskResult repairedRender = new AgentToolDTOs.RenderTaskResult(
            "802", "queued", "queued", "701", "3");
        when(toolService.execute(eq(principal), any())).thenReturn(
            successRender(), output(Set.of("media.playable")), repairedRender);
        when(runService.recordQualityEvaluation(eq(principal), any()))
            .thenReturn(evaluation(0, "repair", "render", quality(Set.of("media.playable"))));
        when(runService.startQualityRepair(eq(principal), any())).thenReturn(
            new IAgentRunService.QualityRepairReceipt(waiting("ai_task", 802L), 1401L, 1L, "render"));

        AgentRunOrchestrationDTOs.AdvanceResult result = service().advance(principal, command());

        assertThat(result.waitingTaskId()).isEqualTo(802L);
        assertToolNames(principal, "get_timeline_render_status", "inspect_timeline_output", "render_timeline");
        verify(runService).startQualityRepair(eq(principal), any());
    }

    @Test
    void unscopedConditionalQualityPersistsAsManualBeforeApproval() {
        AppPrincipalSnapshotDTO principal = principal(ALL_PERMISSIONS);
        when(runService.getOwnedExecutionSnapshot(principal, 1001L)).thenReturn(snapshot(
            run("waiting_external_task", 0, "ai_task", 801L, 0),
            "{\"startAt\":\"render_task\",\"taskId\":\"801\"}", profile(0, 1)));
        when(runService.claim(eq(principal), any())).thenReturn(lease("ai_task", 801L));
        when(toolService.execute(eq(principal), any())).thenReturn(
            successRender(), output(Set.of("style.tone_match")));
        when(runService.recordQualityEvaluation(eq(principal), any()))
            .thenReturn(evaluation(0, "manual", "manual", quality(Set.of("style.tone_match"))));
        when(runService.requestQualityApproval(eq(principal), any())).thenReturn(approval("conditional"));

        AgentRunOrchestrationDTOs.AdvanceResult result = service().advance(principal, command());

        ArgumentCaptor<IAgentRunService.RecordQualityEvaluationCommand> command =
            ArgumentCaptor.forClass(IAgentRunService.RecordQualityEvaluationCommand.class);
        verify(runService).recordQualityEvaluation(eq(principal), command.capture());
        assertThat(command.getValue().decision()).isEqualTo("manual");
        assertThat(command.getValue().repairScope()).isEqualTo("manual");
        assertThat(result.approvalType()).isEqualTo("conditional");
        assertToolNames(principal, "get_timeline_render_status", "inspect_timeline_output");
    }

    @Test
    void firstRepairWithoutMeasurableImprovementRequestsConditionalApprovalWithoutAnotherTool() {
        AppPrincipalSnapshotDTO principal = principal(ALL_PERMISSIONS);
        IAgentRunService.AgentRunView repaired = run("waiting_external_task", 1, 1,
            "ai_task", 801L, 0, NOW.minusSeconds(10), 1, null, 1);
        TimelineOutputQualityDTO unchanged = quality(Set.of("subtitle.timing"));
        when(runService.getOwnedExecutionSnapshot(principal, 1001L)).thenReturn(snapshot(
            repaired, "{\"startAt\":\"render_task\",\"taskId\":\"801\"}", profile(0, 1)));
        when(runService.claim(eq(principal), any())).thenReturn(lease("ai_task", 801L));
        when(toolService.execute(eq(principal), any())).thenReturn(
            successRender(), output(Set.of("subtitle.timing")));
        when(runService.getOwnedQualityEvaluation(principal, 1001L, 0L))
            .thenReturn(evaluation(0, "repair", "timeline_render", unchanged));
        when(runService.recordQualityEvaluation(eq(principal), any()))
            .thenReturn(evaluation(1, "conditional", "timeline_render", unchanged));
        when(runService.requestQualityApproval(eq(principal), any())).thenReturn(approval("conditional"));

        AgentRunOrchestrationDTOs.AdvanceResult result = service().advance(principal, command());

        assertThat(result.runStatus()).isEqualTo("waiting_approval");
        assertThat(result.approvalType()).isEqualTo("conditional");
        assertToolNames(principal, "get_timeline_render_status", "inspect_timeline_output");
        verify(runService, never()).startQualityRepair(any(), any());
        verify(runService).requestQualityApproval(eq(principal), any());
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
        return run(status, retryCount, 0L, waitingSource, waitingId, candidateAssetId, startedAt,
            leaseGeneration, null, 1L);
    }

    private IAgentRunService.AgentRunView run(String status, long retryCount, long qualityRepairCount,
                                               String waitingSource, Long waitingId, long candidateAssetId,
                                               Instant startedAt, long leaseGeneration, Long pendingApprovalId,
                                               long approvalRevision) {
        return new IAgentRunService.AgentRunView(1001L, 1101L, 1201L, 1L, status, 0L, leaseGeneration,
            waitingSource, waitingId, candidateAssetId == 0 ? null : candidateAssetId, NOW.minusSeconds(10),
            retryCount, qualityRepairCount, pendingApprovalId, approvalRevision, startedAt,
            NOW.plusSeconds(5), null, null, null);
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
            confirmed, output, null, null, INPUT_HASH);
    }

    private AgentToolDTOs.GenerationJobResult video(String status) {
        return new AgentToolDTOs.GenerationJobResult("601", "501", "video_generate", status,
            "succeeded".equals(status) ? "completed" : status, "succeeded".equals(status) ? 100 : 20,
            true, "succeeded".equals(status), null, null, INPUT_HASH);
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
            false, false, null, null, "digital_human_job", "601", "黄金链");
    }

    private AgentToolDTOs.OutputInspectionResult output() {
        return output(Set.of());
    }

    private AgentToolDTOs.OutputInspectionResult output(Set<String> failures) {
        return new AgentToolDTOs.OutputInspectionResult("801", "901", "ready", "video",
            "timeline_render_output", "video/mp4", "a".repeat(64), 5_244_591L, 25_800L, 1080, 1920,
            true, true, "/api/studio/creation-assets/901/content", quality(failures));
    }

    private IAgentRunService.QualityEvaluationView evaluation(long candidateNo, String decision,
                                                               String scope) {
        return evaluation(candidateNo, decision, scope, quality(Set.of()));
    }

    private IAgentRunService.QualityEvaluationView evaluation(long candidateNo, String decision,
                                                               String scope, TimelineOutputQualityDTO quality) {
        String qualityJson;
        try {
            qualityJson = JsonMapper.builder().build().writeValueAsString(quality);
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
        return new IAgentRunService.QualityEvaluationView(1401L + candidateNo, 1001L, candidateNo,
            801L, 901L, 701L, quality.ruleSetVersion(), qualityJson, "c".repeat(64), decision, scope);
    }

    private IAgentRunService.ApprovalView approval(String type) {
        return new IAgentRunService.ApprovalView(1301L, 1001L,
            "initial".equals(type) ? null : 1401L, type, "pending", "d".repeat(64), 1L,
            "负责人批准", null, null);
    }

    private TimelineOutputQualityDTO quality(Set<String> failures) {
        List<String> codes = List.of(
            "media.playable", "media.container_codec", "media.video_dimensions", "media.audio_present",
            "media.duration", "content.script_integrity", "content.must_include", "content.prohibited",
            "subtitle.text_integrity", "subtitle.safe_area", "subtitle.timing",
            "perceptual.identity_similarity", "perceptual.lip_sync", "perceptual.voice_consistency",
            "perceptual.visual_stability", "style.tone_match");
        List<TimelineOutputQualityDTO.Criterion> criteria = codes.stream().map(code -> {
            TimelineOutputQualityDTO.Layer layer = code.startsWith("media.")
                ? TimelineOutputQualityDTO.Layer.MEDIA
                : code.startsWith("perceptual.") || code.startsWith("style.")
                ? TimelineOutputQualityDTO.Layer.PERCEPTUAL : TimelineOutputQualityDTO.Layer.CONTENT_LAYOUT;
            boolean subjective = layer == TimelineOutputQualityDTO.Layer.PERCEPTUAL;
            return new TimelineOutputQualityDTO.Criterion(code, layer, "rule-v1",
                failures.contains(code) ? TimelineOutputQualityDTO.Verdict.FAIL
                    : subjective ? TimelineOutputQualityDTO.Verdict.REVIEW : TimelineOutputQualityDTO.Verdict.PASS,
                failures.contains(code) ? TimelineOutputQualityDTO.Confidence.HIGH
                    : subjective ? TimelineOutputQualityDTO.Confidence.LOW : TimelineOutputQualityDTO.Confidence.HIGH,
                Map.of("fixture", true));
        }).toList();
        return new TimelineOutputQualityDTO("801", "901", "a".repeat(64), "811", "b".repeat(64),
            "t5-quality-1", criteria);
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
