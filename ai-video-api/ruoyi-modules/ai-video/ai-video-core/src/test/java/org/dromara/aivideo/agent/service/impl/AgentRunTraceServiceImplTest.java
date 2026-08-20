package org.dromara.aivideo.agent.service.impl;

import org.dromara.aivideo.agent.domain.AgentRunApproval;
import org.dromara.aivideo.agent.domain.AgentRunEvaluation;
import org.dromara.aivideo.agent.dto.AgentRunTraceDTO;
import org.dromara.aivideo.agent.mapper.AgentRunApprovalMapper;
import org.dromara.aivideo.agent.mapper.AgentRunEvaluationMapper;
import org.dromara.aivideo.agent.service.IAgentRunService;
import org.dromara.aivideo.creation.domain.CreationProject;
import org.dromara.aivideo.creation.mapper.CreationProjectMapper;
import org.dromara.aivideo.digitalhuman.domain.DigitalHumanGenerationJob;
import org.dromara.aivideo.digitalhuman.domain.DigitalHumanJobStage;
import org.dromara.aivideo.digitalhuman.domain.DigitalHumanJobStatus;
import org.dromara.aivideo.digitalhuman.domain.DigitalHumanJobType;
import org.dromara.aivideo.digitalhuman.mapper.DigitalHumanGenerationJobMapper;
import org.dromara.aivideo.identity.dto.AppPrincipalSnapshotDTO;
import org.dromara.aivideo.identity.dto.AppWorkspaceSessionSnapshotDTO;
import org.dromara.aivideo.task.domain.AiTask;
import org.dromara.aivideo.task.mapper.AiTaskMapper;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("dev")
class AgentRunTraceServiceImplTest {

    private static final long RUN_ID = 1001L;
    private static final long OWNER_ID = 2001L;
    private static final long TENANT_ID = 3001L;
    private static final Instant BASE = Instant.parse("2026-08-16T01:00:00Z");

    @Mock
    private IAgentRunService runService;
    @Mock
    private DigitalHumanGenerationJobMapper generationJobMapper;
    @Mock
    private CreationProjectMapper projectMapper;
    @Mock
    private AiTaskMapper taskMapper;
    @Mock
    private AgentRunEvaluationMapper evaluationMapper;
    @Mock
    private AgentRunApprovalMapper approvalMapper;

    @Test
    void returnsOnlyOwnedWhitelistedAndFinalChainFactsInStableSafeOrder() {
        AppPrincipalSnapshotDTO principal = principal(OWNER_ID);
        when(runService.getOwnedRun(principal, RUN_ID)).thenReturn(run());

        AgentRunEvaluation evaluation = evaluation(9001L, 801L, 901L, 701L, 5);
        AgentRunEvaluation outsiderEvaluation = evaluation(9002L, 802L, 902L, 702L, 5);
        outsiderEvaluation.setOwnerUserId(9999L);
        when(evaluationMapper.selectList(any())).thenReturn(List.of(evaluation, outsiderEvaluation));

        AgentRunApproval approval = approval(9101L, 9001L, 6);
        AgentRunApproval outsiderApproval = approval(9102L, 9002L, 6);
        outsiderApproval.setAgentRunId(9999L);
        when(approvalMapper.selectList(any())).thenReturn(List.of(approval, outsiderApproval));

        AiTask task = task(801L, OWNER_ID, "agent-run:1001:render:0", 701L, 901L, 4);
        AiTask unknown = task(802L, OWNER_ID, "agent-run:1001:unexpected:0", 702L, 902L, 4);
        AiTask crossOwner = task(803L, 9999L, "agent-run:1001:render:1", 703L, 903L, 4);
        when(taskMapper.selectList(any())).thenReturn(List.of(task, unknown, crossOwner));

        CreationProject project = project(701L, OWNER_ID, "agent-run:1001:project:0", 601L, 3);
        CreationProject unknownProject = project(702L, OWNER_ID, "agent-run:1001:unexpected:0", 602L, 3);
        when(projectMapper.selectList(any())).thenReturn(List.of(project, unknownProject));

        DigitalHumanGenerationJob video = job(601L, OWNER_ID, DigitalHumanJobType.VIDEO_GENERATE,
            "legacy-video", 501L, 2);
        video.setProvider("https://provider.invalid/private?token=secret-provider-token");
        video.setPollToken("secret-poll-token");
        video.setOutputMediaKey("private/output/path.mp4");
        DigitalHumanGenerationJob unknownJob = job(602L, OWNER_ID, DigitalHumanJobType.VIDEO_GENERATE,
            "agent-run:1001:unexpected:0", null, 2);
        DigitalHumanGenerationJob voice = job(501L, OWNER_ID, DigitalHumanJobType.VOICE_GENERATE,
            "legacy-voice", null, 1);
        voice.setScriptText("secret-script-body");
        when(generationJobMapper.selectList(any())).thenReturn(List.of(video, unknownJob), List.of(voice));

        AgentRunTraceDTO trace = service().getOwnedTrace(principal, RUN_ID);

        assertThat(trace.completeness()).isEqualTo(AgentRunTraceDTO.DURABLE_FACTS);
        assertThat(trace.facts()).extracting(AgentRunTraceDTO.Fact::sequence)
            .containsExactly(1, 2, 3, 4, 5, 6, 7);
        assertThat(trace.facts()).extracting(AgentRunTraceDTO.Fact::factType)
            .containsExactly("generation_job", "generation_job", "creation_project", "ai_task",
                "quality_evaluation", "approval", "agent_run");
        assertThat(trace.facts()).extracting(AgentRunTraceDTO.Fact::factId)
            .containsExactly(501L, 601L, 701L, 801L, 9001L, 9101L, RUN_ID);
        assertThat(trace.facts()).extracting(AgentRunTraceDTO.Fact::stepCode)
            .containsExactly("submit_voice", "submit_video", "prepare_project", "submit_render",
                "quality_evaluation", "approval", "agent_run");
        assertThat(trace.facts()).allMatch(fact -> fact.stepCode() != null && fact.status() != null
            && fact.persistedAt() != null);
        assertThat(trace.toString()).doesNotContain("secret", "provider.invalid", "private/output",
            "agent-run:1001");
    }

    @Test
    void rejectsCrossOwnerBeforeReadingAnyTraceFactMapper() {
        AppPrincipalSnapshotDTO principal = principal(OWNER_ID);
        when(runService.getOwnedRun(principal, RUN_ID)).thenThrow(new ServiceException("AgentRun 不存在"));

        assertThatThrownBy(() -> service().getOwnedTrace(principal, RUN_ID))
            .isInstanceOf(ServiceException.class)
            .hasMessage("AgentRun 不存在");

        verifyNoInteractions(generationJobMapper, projectMapper, taskMapper, evaluationMapper, approvalMapper);
    }

    @Test
    void rejectsNonCanonicalWorkspaceBeforeAnyPersistenceRead() {
        AppPrincipalSnapshotDTO principal = principal("", OWNER_ID);

        assertThatThrownBy(() -> service().getOwnedTrace(principal, RUN_ID))
            .isInstanceOf(ServiceException.class)
            .hasMessage("AgentRun Trace 不存在");

        verifyNoInteractions(runService, generationJobMapper, projectMapper, taskMapper, evaluationMapper,
            approvalMapper);
    }

    private AgentRunTraceServiceImpl service() {
        return new AgentRunTraceServiceImpl(runService, generationJobMapper, projectMapper, taskMapper,
            evaluationMapper, approvalMapper);
    }

    private AppPrincipalSnapshotDTO principal(long workspaceOwner) {
        return principal("personal:" + OWNER_ID, workspaceOwner);
    }

    private AppPrincipalSnapshotDTO principal(String workspaceKey, long workspaceOwner) {
        AppWorkspaceSessionSnapshotDTO workspace = new AppWorkspaceSessionSnapshotDTO(
            workspaceKey, "personal", TENANT_ID, "app_user", workspaceOwner,
            "app_user", OWNER_ID, "creator", Set.of("aivideo:studio:query"), 1L, null);
        return new AppPrincipalSnapshotDTO(OWNER_ID, "creator", "desktop", 1L, 1L, 1L, 1L, workspace);
    }

    private IAgentRunService.AgentRunView run() {
        return new IAgentRunService.AgentRunView(RUN_ID, 101L, 201L, 1L, "waiting_approval", 8L, 2L,
            null, null, 901L, BASE.plusSeconds(7), 0L, 0L, 9101L, 1L,
            BASE.minusSeconds(20), null, null, null, null, null);
    }

    private AgentRunEvaluation evaluation(long id, long taskId, long assetId, long projectId, long seconds) {
        AgentRunEvaluation row = new AgentRunEvaluation();
        row.setEvaluationId(id);
        row.setAgentRunId(RUN_ID);
        row.setOwnerUserId(OWNER_ID);
        row.setCandidateNo(0L);
        row.setRenderTaskId(taskId);
        row.setResultAssetId(assetId);
        row.setProjectId(projectId);
        row.setRuleSetVersion("quality-v1");
        row.setQualityJson("{\"private\":\"secret-quality-json\"}");
        row.setQualityDigest("quality-digest");
        row.setDecision("final");
        row.setRepairScope("none");
        row.setCreateTime(local(seconds));
        return row;
    }

    private AgentRunApproval approval(long id, long evaluationId, long seconds) {
        AgentRunApproval row = new AgentRunApproval();
        row.setApprovalId(id);
        row.setAgentRunId(RUN_ID);
        row.setOwnerUserId(OWNER_ID);
        row.setEvaluationId(evaluationId);
        row.setApprovalType("final");
        row.setApprovalStatus("pending");
        row.setSubjectDigest("secret-subject-digest");
        row.setRevision(1L);
        row.setRequestSummary("质量检查需要最终批准");
        row.setCreateTime(local(seconds));
        return row;
    }

    private AiTask task(long id, long owner, String key, long projectId, long assetId, long seconds) {
        AiTask row = new AiTask();
        row.setTaskId(id);
        row.setOwnerUserId(owner);
        row.setTaskType("timeline_render");
        row.setResourceType("creation_project");
        row.setResourceId(projectId);
        row.setIdempotencyKey(key);
        row.setRequestPayloadJson("{\"url\":\"https://secret.invalid\"}");
        row.setTaskStatus("success");
        row.setStage("completed");
        row.setProgressPercent(100);
        row.setResultAssetId(assetId);
        row.setResultPayloadJson("{\"path\":\"private/output.mp4\"}");
        row.setUpdateTime(local(seconds));
        return row;
    }

    private CreationProject project(long id, long owner, String key, long sourceId, long seconds) {
        CreationProject row = new CreationProject();
        row.setProjectId(id);
        row.setOwnerUserId(owner);
        row.setProjectTitle("secret-project-title");
        row.setIdempotencyKey(key);
        row.setSourceType("digital_human_job");
        row.setSourceRefId(sourceId);
        row.setProjectStatus("ready");
        row.setCurrentOutputAssetId(901L);
        row.setDelFlag("0");
        row.setUpdateTime(local(seconds));
        return row;
    }

    private DigitalHumanGenerationJob job(long id, long owner, DigitalHumanJobType type, String key,
                                           Long parentId, long seconds) {
        DigitalHumanGenerationJob row = new DigitalHumanGenerationJob();
        row.setId(id);
        row.setTenantId(TENANT_ID);
        row.setOwnerUserId(owner);
        row.setJobType(type);
        row.setStatus(DigitalHumanJobStatus.SUCCEEDED);
        row.setStage(DigitalHumanJobStage.COMPLETED);
        row.setProgress(100);
        row.setParentJobId(parentId);
        row.setIdempotencyKey(key);
        row.setUpdateTime(local(seconds));
        return row;
    }

    private LocalDateTime local(long seconds) {
        return LocalDateTime.ofInstant(BASE.plusSeconds(seconds), ZoneOffset.UTC);
    }
}
