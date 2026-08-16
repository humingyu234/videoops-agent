package org.dromara.aivideo.agent.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.aivideo.agent.domain.AcceptanceProfileVersion;
import org.dromara.aivideo.agent.domain.AgentRun;
import org.dromara.aivideo.agent.domain.AgentRunApproval;
import org.dromara.aivideo.agent.domain.AgentRunEvaluation;
import org.dromara.aivideo.agent.domain.DeliveryBriefVersion;
import org.dromara.aivideo.agent.mapper.AcceptanceProfileVersionMapper;
import org.dromara.aivideo.agent.mapper.AgentRunMapper;
import org.dromara.aivideo.agent.mapper.AgentRunApprovalMapper;
import org.dromara.aivideo.agent.mapper.AgentRunEvaluationMapper;
import org.dromara.aivideo.agent.mapper.DeliveryBriefVersionMapper;
import org.dromara.aivideo.agent.service.IAgentRunService;
import org.dromara.aivideo.identity.dto.AppPrincipalSnapshotDTO;
import org.dromara.aivideo.timeline.dto.TimelineOutputQualityDTO;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.lang.reflect.Method;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("dev")
class AgentRunServiceImplTest {

    private static final LocalDateTime DATABASE_NOW = LocalDateTime.of(2026, 8, 15, 12, 0);
    private static final List<String> QUALITY_CODES = List.of(
        "media.playable", "media.container_codec", "media.video_dimensions", "media.audio_present",
        "media.duration", "content.script_integrity", "content.must_include", "content.prohibited",
        "subtitle.text_integrity", "subtitle.safe_area", "subtitle.timing",
        "perceptual.identity_similarity", "perceptual.lip_sync", "perceptual.voice_consistency",
        "perceptual.visual_stability", "style.tone_match");

    @Mock
    private DeliveryBriefVersionMapper briefMapper;
    @Mock
    private AcceptanceProfileVersionMapper profileMapper;
    @Mock
    private AgentRunMapper runMapper;
    @Mock
    private AgentRunEvaluationMapper evaluationMapper;
    @Mock
    private AgentRunApprovalMapper approvalMapper;

    @BeforeAll
    static void initializeMybatisMetadata() {
        initialize(DeliveryBriefVersion.class);
        initialize(AcceptanceProfileVersion.class);
        initialize(AgentRun.class);
        initialize(AgentRunEvaluation.class);
        initialize(AgentRunApproval.class);
    }

    @Test
    void appendsAnImmutableBriefAndReplaysCanonicalEquivalentJson() {
        when(briefMapper.insert(any(DeliveryBriefVersion.class))).thenReturn(1);

        IAgentRunService.DeliveryBriefVersionView created = service().appendDeliveryBrief(principal(7),
            new IAgentRunService.AppendDeliveryBriefCommand(null, null, "brief-key", "{\"b\":2,\"a\":1.0}"));

        ArgumentCaptor<DeliveryBriefVersion> inserted = ArgumentCaptor.forClass(DeliveryBriefVersion.class);
        verify(briefMapper).insert(inserted.capture());
        DeliveryBriefVersion row = inserted.getValue();
        assertThat(row.getOwnerUserId()).isEqualTo(7L);
        assertThat(row.getVersionNo()).isEqualTo(1L);
        assertThat(row.getParentVersionId()).isNull();
        assertThat(row.getBriefJson()).isEqualTo("{\"a\":1,\"b\":2}");
        assertThat(row.getBriefHash()).matches("[0-9a-f]{64}");
        assertThat(row.getActorType()).isEqualTo("app_user");
        assertThat(created.deliveryBriefVersionId()).isEqualTo(row.getDeliveryBriefVersionId());

        reset(briefMapper);
        when(briefMapper.selectOne(any(Wrapper.class))).thenReturn(row);
        IAgentRunService.DeliveryBriefVersionView replay = service().appendDeliveryBrief(principal(7),
            new IAgentRunService.AppendDeliveryBriefCommand(null, null, "brief-key", "{\"a\":1,\"b\":2}"));

        assertThat(replay.deliveryBriefVersionId()).isEqualTo(created.deliveryBriefVersionId());
        verify(briefMapper, never()).insert(any(DeliveryBriefVersion.class));
    }

    @Test
    void rejectsAnIdempotencyKeyReusedForDifferentBriefContent() {
        DeliveryBriefVersion existing = brief(101, 201, 7, 1, null);
        existing.setIdempotencyKey("brief-key");
        existing.setRequestDigest("0".repeat(64));
        when(briefMapper.selectOne(any(Wrapper.class))).thenReturn(existing);

        assertThatThrownBy(() -> service().appendDeliveryBrief(principal(7),
            new IAgentRunService.AppendDeliveryBriefCommand(null, null, "brief-key", "{\"goal\":\"new\"}")))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("幂等键");
        verify(briefMapper, never()).insert(any(DeliveryBriefVersion.class));
    }

    @Test
    void appendsOnlyFromTheCurrentOwnedParentVersion() {
        DeliveryBriefVersion parent = brief(101, 201, 7, 1, null);
        when(briefMapper.selectOne(any(Wrapper.class))).thenReturn(null, parent, parent);
        when(briefMapper.insert(any(DeliveryBriefVersion.class))).thenReturn(1);

        IAgentRunService.DeliveryBriefVersionView appended = service().appendDeliveryBrief(principal(7),
            new IAgentRunService.AppendDeliveryBriefCommand(201L, 101L, "brief-v2", "{\"goal\":\"v2\"}"));

        ArgumentCaptor<DeliveryBriefVersion> inserted = ArgumentCaptor.forClass(DeliveryBriefVersion.class);
        verify(briefMapper).insert(inserted.capture());
        assertThat(appended.versionNo()).isEqualTo(2);
        assertThat(inserted.getValue().getBriefId()).isEqualTo(201L);
        assertThat(inserted.getValue().getParentVersionId()).isEqualTo(101L);

        reset(briefMapper);
        when(briefMapper.selectOne(any(Wrapper.class)))
            .thenReturn((DeliveryBriefVersion) null, (DeliveryBriefVersion) null, (DeliveryBriefVersion) null);
        assertThatThrownBy(() -> service().appendDeliveryBrief(principal(8),
            new IAgentRunService.AppendDeliveryBriefCommand(201L, 101L, "foreign", "{\"goal\":\"x\"}")))
            .isInstanceOf(ServiceException.class);
        ArgumentCaptor<Wrapper<DeliveryBriefVersion>> queries = wrapperCaptor();
        verify(briefMapper, org.mockito.Mockito.times(3)).selectOne(queries.capture());
        assertThat(queries.getAllValues()).allSatisfy(wrapper ->
            assertThat(wrapper.getCustomSqlSegment()).contains("owner_user_id"));
        verify(briefMapper, never()).insert(any(DeliveryBriefVersion.class));
    }

    @Test
    void createsAProfileAndRunOnlyForTheSameOwnedBriefRevision() {
        DeliveryBriefVersion brief = brief(101, 201, 7, 1, null);
        when(briefMapper.selectOne(any(Wrapper.class))).thenReturn(brief);
        when(profileMapper.insert(any(AcceptanceProfileVersion.class))).thenReturn(1);

        IAgentRunService.AcceptanceProfileVersionView profileView = service().appendAcceptanceProfile(principal(7),
            new IAgentRunService.AppendAcceptanceProfileCommand(null, null, 101, "profile-key",
                "{\"subtitleReadable\":true}"));
        ArgumentCaptor<AcceptanceProfileVersion> profileCaptor =
            ArgumentCaptor.forClass(AcceptanceProfileVersion.class);
        verify(profileMapper).insert(profileCaptor.capture());
        AcceptanceProfileVersion profile = profileCaptor.getValue();
        assertThat(profileView.policyVersion()).isEqualTo("acceptance-policy-1");

        reset(briefMapper, profileMapper);
        when(briefMapper.selectOne(any(Wrapper.class))).thenReturn(brief);
        when(profileMapper.selectOne(any(Wrapper.class))).thenReturn(profile);
        when(runMapper.selectDatabaseNow()).thenReturn(DATABASE_NOW);
        when(runMapper.insert(any(AgentRun.class))).thenReturn(1);

        IAgentRunService.AgentRunView run = service().createRun(principal(7),
            new IAgentRunService.CreateAgentRunCommand(101, profile.getAcceptanceProfileVersionId(), "run-key"));

        ArgumentCaptor<AgentRun> runCaptor = ArgumentCaptor.forClass(AgentRun.class);
        verify(runMapper).insert(runCaptor.capture());
        assertThat(run.runStatus()).isEqualTo("queued");
        assertThat(run.contractRevision()).isEqualTo(1);
        assertThat(runCaptor.getValue().getOwnerUserId()).isEqualTo(7L);
        assertThat(runCaptor.getValue().getRowVersion()).isZero();
        assertThat(runCaptor.getValue().getLeaseGeneration()).isZero();
        assertThat(runCaptor.getValue().getRetryCount()).isZero();
        assertThat(runCaptor.getValue().getQualityRepairCount()).isZero();
        assertThat(runCaptor.getValue().getApprovalRevision()).isZero();
    }

    @Test
    void exposesTheOwnedFrozenContractAndBlocksOnlyTheExpectedQueuedRevision() {
        AgentRun queued = run(501, 7, "queued", 0, 0);
        queued.setRetryCount(2L);
        DeliveryBriefVersion brief = brief(101, 201, 7, 1, null);
        brief.setBriefJson("{\"scriptText\":\"hello\"}");
        AcceptanceProfileVersion profile = profile(301, 401, 7, 101);
        profile.setProfileJson("{\"maxRetries\":2}");
        when(runMapper.selectOne(any(Wrapper.class))).thenReturn(queued);
        when(briefMapper.selectOne(any(Wrapper.class))).thenReturn(brief);
        when(profileMapper.selectOne(any(Wrapper.class))).thenReturn(profile);

        IAgentRunService.ExecutionSnapshot snapshot =
            service().getOwnedExecutionSnapshot(principal(7), 501);

        assertThat(snapshot.run().agentRunId()).isEqualTo(501L);
        assertThat(snapshot.run().retryCount()).isEqualTo(2L);
        assertThat(snapshot.deliveryBriefJson()).isEqualTo("{\"scriptText\":\"hello\"}");
        assertThat(snapshot.acceptanceProfileJson()).isEqualTo("{\"maxRetries\":2}");
        assertThat(snapshot.deliveryBriefHash()).isEqualTo("a".repeat(64));
        assertThat(snapshot.acceptanceProfileHash()).isEqualTo("b".repeat(64));

        when(runMapper.selectDatabaseNow()).thenReturn(DATABASE_NOW);
        when(runMapper.blockForInput(anyLong(), anyLong(), anyLong(), anyLong(), anyString(), anyString(),
            any(LocalDateTime.class))).thenReturn(1, 0);
        IAgentRunService.BlockForInputCommand command = new IAgentRunService.BlockForInputCommand(
            501, 0, 1, "MISSING_SCRIPT", "请确认口播文案");

        assertThat(service().blockForInput(principal(7), command)).isTrue();
        assertThat(service().blockForInput(principal(7), command)).isFalse();
        verify(runMapper, org.mockito.Mockito.times(2)).blockForInput(eq(501L), eq(7L), eq(1L), eq(0L),
            eq("MISSING_SCRIPT"), eq("请确认口播文案"), eq(DATABASE_NOW));
    }

    @Test
    void recordsAndReplaysOnlyTheExactFencedQualityCandidate() {
        when(runMapper.selectDatabaseNow()).thenReturn(DATABASE_NOW);
        when(evaluationMapper.insertFenced(any(AgentRunEvaluation.class), anyString(), anyLong(), anyLong(),
            anyLong(), anyString(), any(LocalDateTime.class))).thenReturn(1);
        IAgentRunService.LeaseProof lease = new IAgentRunService.LeaseProof(501, 3, 1, 2, "raw-token");
        var command = new IAgentRunService.RecordQualityEvaluationCommand(lease, 0, 701, 901, 801,
            "quality-rules-1", qualityJson(701, 901, Set.of("subtitle.timing"),
                TimelineOutputQualityDTO.Confidence.HIGH),
            "repair", "timeline_render");

        var created = service().recordQualityEvaluation(principal(7), command);

        assertThat(created.agentRunId()).isEqualTo(501L);
        assertThat(created.qualityJson()).contains("subtitle.text");
        assertThat(created.qualityDigest()).matches("[0-9a-f]{64}");
        ArgumentCaptor<AgentRunEvaluation> inserted = ArgumentCaptor.forClass(AgentRunEvaluation.class);
        verify(evaluationMapper).insertFenced(inserted.capture(), eq("a".repeat(64)), eq(1L), eq(3L), eq(2L),
            org.mockito.ArgumentMatchers.matches("[0-9a-f]{64}"), eq(DATABASE_NOW));
        assertThat(inserted.getValue().getCandidateNo()).isZero();

        when(evaluationMapper.selectOne(any(Wrapper.class))).thenReturn(inserted.getValue());
        when(evaluationMapper.countFencedReplay(anyLong(), anyLong(), anyLong(), anyLong(), anyLong(), anyLong(),
            anyLong(), anyString(), anyString(), anyString(), anyString(), anyLong(), anyLong(), anyLong(),
            anyString(), any(LocalDateTime.class))).thenReturn(1L);
        var replay = service().recordQualityEvaluation(principal(7), command);
        assertThat(replay.evaluationId()).isEqualTo(created.evaluationId());

        reset(evaluationMapper);
        when(evaluationMapper.insertFenced(any(AgentRunEvaluation.class), anyString(), anyLong(), anyLong(),
            anyLong(), anyString(), any(LocalDateTime.class))).thenReturn(1);
        var conditional = service().recordQualityEvaluation(principal(7),
            new IAgentRunService.RecordQualityEvaluationCommand(lease, 0, 702, 902, 802,
                "quality-rules-1", qualityJson(702, 902, Set.of("subtitle.timing"),
                TimelineOutputQualityDTO.Confidence.LOW), "conditional", "timeline_render"));
        assertThat(conditional.decision()).isEqualTo("conditional");
        assertThat(conditional.repairScope()).isEqualTo("timeline_render");
    }

    @Test
    void recomputesQualityIdentityAndRoutesUnscopedOrMalformedFactsToManual() {
        when(runMapper.selectDatabaseNow()).thenReturn(DATABASE_NOW);
        when(evaluationMapper.insertFenced(any(AgentRunEvaluation.class), anyString(), anyLong(), anyLong(),
            anyLong(), anyString(), any(LocalDateTime.class))).thenReturn(1);
        IAgentRunService.LeaseProof lease = new IAgentRunService.LeaseProof(501, 3, 1, 2, "raw-token");

        assertThatThrownBy(() -> service().recordQualityEvaluation(principal(7),
            new IAgentRunService.RecordQualityEvaluationCommand(lease, 0, 701, 901, 801,
                "quality-rules-1", qualityJson(701, 901, Set.of("subtitle.timing"),
                TimelineOutputQualityDTO.Confidence.HIGH), "final", "none")))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("复算");
        assertThatThrownBy(() -> service().recordQualityEvaluation(principal(7),
            new IAgentRunService.RecordQualityEvaluationCommand(lease, 0, 701, 901, 801,
                "quality-rules-1", qualityJson(999, 901, Set.of(),
                TimelineOutputQualityDTO.Confidence.HIGH), "final", "none")))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("身份");

        var style = service().recordQualityEvaluation(principal(7),
            new IAgentRunService.RecordQualityEvaluationCommand(lease, 0, 702, 902, 802,
                "quality-rules-1", qualityJson(702, 902, Set.of("style.tone_match"),
                TimelineOutputQualityDTO.Confidence.HIGH), "manual", "manual"));
        assertThat(style.decision()).isEqualTo("manual");
        assertThat(style.repairScope()).isEqualTo("manual");

        String malformedQuality = """
            {"taskId":"703","assetId":"903","artifactSha256":"%s","inputVersionId":"1",
             "timelineContentHash":"%s","ruleSetVersion":"quality-rules-1","criteria":[]}
            """.formatted("a".repeat(64), "b".repeat(64));
        var malformed = service().recordQualityEvaluation(principal(7),
            new IAgentRunService.RecordQualityEvaluationCommand(lease, 0, 703, 903, 803,
                "quality-rules-1", malformedQuality, "manual", "manual"));
        assertThat(malformed.decision()).isEqualTo("manual");
        assertThat(malformed.repairScope()).isEqualTo("manual");
    }

    @Test
    void persistsInitialAndFinalApprovalTransitionsWithExactRevision() {
        AgentRun queued = run(501, 7, "queued", 0, 0);
        queued.setRequestDigest("a".repeat(64));
        when(runMapper.selectOne(any(Wrapper.class))).thenReturn(queued);
        when(approvalMapper.insert(any(AgentRunApproval.class))).thenReturn(1);
        when(runMapper.selectDatabaseNow()).thenReturn(DATABASE_NOW);
        when(runMapper.requestInitialApproval(anyLong(), anyLong(), anyLong(), anyLong(), anyLong(), anyLong(),
            any(LocalDateTime.class))).thenReturn(1);

        var initial = service().requestInitialApproval(principal(7),
            new IAgentRunService.RequestInitialApprovalCommand(501, 0, 1, "确认按冻结交付合同执行"));

        assertThat(initial.approvalType()).isEqualTo("initial");
        assertThat(initial.revision()).isEqualTo(1L);
        assertThat(initial.subjectDigest()).matches("[0-9a-f]{64}");
        when(approvalMapper.decidePending(anyLong(), anyLong(), anyLong(), anyString(), anyLong(), anyLong(),
            anyLong(), anyString(), anyString(), any(LocalDateTime.class))).thenReturn(1);
        when(runMapper.approveInitial(anyLong(), anyLong(), anyLong(), anyLong(), anyLong(), anyLong(),
            any(LocalDateTime.class))).thenReturn(1);
        var approved = service().decideApproval(principal(7), new IAgentRunService.DecideApprovalCommand(
            501, 1, 1, initial.approvalId(), 1, "initial", "approved", "负责人确认执行"));
        assertThat(approved.runStatus()).isEqualTo("queued");

        AgentRun waiting = run(502, 7, "waiting_external_task", 4, 2);
        waiting.setQualityRepairCount(1L);
        waiting.setApprovalRevision(1L);
        AgentRunEvaluation evaluation = evaluation(601, 502, 7, 1, 702, 902, 802, "final", "none");
        reset(runMapper, evaluationMapper, approvalMapper);
        when(runMapper.selectOne(any(Wrapper.class))).thenReturn(waiting);
        when(evaluationMapper.selectOne(any(Wrapper.class))).thenReturn(evaluation);
        when(approvalMapper.insert(any(AgentRunApproval.class))).thenReturn(1);
        when(runMapper.selectDatabaseNow()).thenReturn(DATABASE_NOW);
        when(runMapper.requestQualityApproval(anyLong(), anyLong(), anyLong(), anyLong(), anyLong(), anyString(),
            anyLong(), anyLong(), anyLong(), anyString(), any(LocalDateTime.class))).thenReturn(1);
        var lease = new IAgentRunService.LeaseProof(502, 4, 1, 2, "raw-token");
        var finalApproval = service().requestQualityApproval(principal(7),
            new IAgentRunService.RequestQualityApprovalCommand(lease, 601, "final", "确认交付候选 1"));
        assertThat(finalApproval.evaluationId()).isEqualTo(601L);
        assertThat(finalApproval.revision()).isEqualTo(2L);

        when(approvalMapper.decidePending(anyLong(), anyLong(), anyLong(), anyString(), anyLong(), anyLong(),
            anyLong(), anyString(), anyString(), any(LocalDateTime.class))).thenReturn(1);
        when(runMapper.approveFinal(anyLong(), anyLong(), anyLong(), anyLong(), anyLong(), anyLong(), anyString(),
            anyString(), any(LocalDateTime.class))).thenReturn(1);
        var completed = service().decideApproval(principal(7), new IAgentRunService.DecideApprovalCommand(
            502, 5, 1, finalApproval.approvalId(), 2, "final", "approved", "最终成品确认交付"));
        assertThat(completed.runStatus()).isEqualTo("completed");
    }

    @Test
    void rejectsAProfileAndBriefPairThatDoNotReferenceEachOther() {
        DeliveryBriefVersion brief = brief(101, 201, 7, 1, null);
        AcceptanceProfileVersion profile = profile(301, 401, 7, 999);
        when(briefMapper.selectOne(any(Wrapper.class))).thenReturn(brief);
        when(profileMapper.selectOne(any(Wrapper.class))).thenReturn(profile);

        assertThatThrownBy(() -> service().createRun(principal(7),
            new IAgentRunService.CreateAgentRunCommand(101, 301, "run-key")))
            .isInstanceOf(ServiceException.class);
        verify(runMapper, never()).insert(any(AgentRun.class));
    }

    @Test
    void claimsQueuedWorkWithADigestOnlyAndRejectsTheOldLeaseAfterRecovery() {
        AgentRun queued = run(501, 7, "queued", 0, 0);
        when(runMapper.selectOne(any(Wrapper.class))).thenReturn(queued);
        when(runMapper.selectDatabaseNow()).thenReturn(DATABASE_NOW);
        when(runMapper.claimLease(anyLong(), anyLong(), anyLong(), anyLong(), anyLong(),
            anyString(), anyString(), any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(1);

        IAgentRunService.AgentRunLease first = service().claim(principal(7),
            new IAgentRunService.ClaimAgentRunCommand(501, 0, 1, "worker-a", 30));

        assertThat(first.rowVersion()).isEqualTo(1);
        assertThat(first.leaseGeneration()).isEqualTo(1);
        assertThat(first.leaseToken()).hasSizeGreaterThan(30);
        ArgumentCaptor<String> digest = ArgumentCaptor.forClass(String.class);
        verify(runMapper).claimLease(eq(501L), eq(7L), eq(1L), eq(0L), eq(0L), eq("worker-a"),
            digest.capture(), eq(DATABASE_NOW), eq(DATABASE_NOW.plusSeconds(30)));
        assertThat(digest.getValue()).matches("[0-9a-f]{64}").isNotEqualTo(first.leaseToken());

        AgentRun expired = run(501, 7, "running", 1, 1);
        when(runMapper.selectOne(any(Wrapper.class))).thenReturn(expired);
        IAgentRunService.AgentRunLease recovered = service().claim(principal(7),
            new IAgentRunService.ClaimAgentRunCommand(501, 1, 1, "worker-b", 30));
        assertThat(recovered.agentRunId()).isEqualTo(first.agentRunId());
        assertThat(recovered.rowVersion()).isEqualTo(2);
        assertThat(recovered.leaseGeneration()).isEqualTo(2);
        assertThat(recovered.leaseToken()).isNotEqualTo(first.leaseToken());

        when(runMapper.finishLease(anyLong(), anyLong(), anyLong(), anyLong(), anyLong(), anyString(),
            anyString(), any(), any(), any(), any(), any(), any(LocalDateTime.class))).thenReturn(0);
        boolean stale = service().finishLease(principal(7), new IAgentRunService.FinishAgentRunCommand(
            first.proof(), "completed", 901L, "{\"assetId\":901}", null, null));
        assertThat(stale).isFalse();
    }

    @Test
    void persistsTheExactExternalTaskAndAcceptsItsCurrentFencedResultOnlyOnce() {
        when(runMapper.selectDatabaseNow()).thenReturn(DATABASE_NOW);
        when(runMapper.waitForExternalTask(anyLong(), anyLong(), anyLong(), anyLong(), anyLong(), anyString(),
            anyString(), anyLong(), any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(1);
        IAgentRunService.LeaseProof running = new IAgentRunService.LeaseProof(501, 1, 1, 1, "raw-token");

        IAgentRunService.WaitingReceipt waiting = service().waitForExternalTask(principal(7),
            new IAgentRunService.WaitForExternalTaskCommand(running, "ai_task", 701,
                Instant.parse("2026-08-15T12:01:00Z")));

        assertThat(waiting.lease().rowVersion()).isEqualTo(2);
        assertThat(waiting.lease().leaseGeneration()).isEqualTo(1);
        verify(runMapper).waitForExternalTask(eq(501L), eq(7L), eq(1L), eq(1L), eq(1L),
            org.mockito.ArgumentMatchers.matches("[0-9a-f]{64}"), eq("ai_task"), eq(701L),
            eq(LocalDateTime.of(2026, 8, 15, 12, 1)), eq(DATABASE_NOW));

        when(runMapper.completeExternalTask(anyLong(), anyLong(), anyLong(), anyLong(), anyLong(), anyString(),
            anyString(), anyLong(), anyLong(), anyString(), anyString(), any(LocalDateTime.class)))
            .thenReturn(1, 0);
        IAgentRunService.CompleteExternalTaskCommand result = new IAgentRunService.CompleteExternalTaskCommand(
            waiting.lease(), "ai_task", 701, 901, "{\"ok\":true}");

        assertThat(service().completeExternalTask(principal(7), result)).isTrue();
        assertThat(service().completeExternalTask(principal(7), result)).isFalse();
    }

    @Test
    void defersAdvancesRetriesAndStopsWithTheSameOwnerScopedFence() {
        when(runMapper.selectDatabaseNow()).thenReturn(DATABASE_NOW);
        when(runMapper.deferExternalTask(anyLong(), anyLong(), anyLong(), anyLong(), anyLong(), anyString(),
            anyString(), anyLong(), any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(1);
        when(runMapper.advanceExternalTask(anyLong(), anyLong(), anyLong(), anyLong(), anyLong(), anyString(),
            anyString(), anyLong(), anyString(), anyLong(), any(LocalDateTime.class), any(LocalDateTime.class)))
            .thenReturn(1);
        when(runMapper.retryExternalTask(anyLong(), anyLong(), anyLong(), anyLong(), anyLong(), anyString(),
            anyLong(), anyLong(), any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(1);
        when(runMapper.stopOwnedRun(anyLong(), anyLong(), anyLong(), anyLong(), anyString(), anyString(),
            anyString(), any(LocalDateTime.class))).thenReturn(1);
        IAgentRunService.LeaseProof lease = new IAgentRunService.LeaseProof(501, 3, 1, 2, "raw-token");
        Instant nextObservation = Instant.parse("2026-08-15T12:01:00Z");

        IAgentRunService.WaitingReceipt deferred = service().deferExternalTask(principal(7),
            new IAgentRunService.DeferExternalTaskCommand(
                lease, "digital_human_generation", 701, nextObservation));
        IAgentRunService.WaitingReceipt advanced = service().advanceExternalTask(principal(7),
            new IAgentRunService.AdvanceExternalTaskCommand(lease, "digital_human_generation", 701,
                "digital_human_generation", 702, nextObservation));
        IAgentRunService.WaitingReceipt retried = service().retryExternalTask(principal(7),
            new IAgentRunService.RetryExternalTaskCommand(lease, 801, 802, nextObservation));
        boolean stopped = service().stopOwnedRun(principal(7), new IAgentRunService.StopOwnedRunCommand(
            501, 3, 1, "cancelled", "USER_CANCELLED", "用户已取消执行"));

        assertThat(deferred.lease().rowVersion()).isEqualTo(4L);
        assertThat(deferred.taskId()).isEqualTo(701L);
        assertThat(advanced.lease().rowVersion()).isEqualTo(4L);
        assertThat(advanced.taskId()).isEqualTo(702L);
        assertThat(retried.lease().rowVersion()).isEqualTo(4L);
        assertThat(retried.taskSource()).isEqualTo("ai_task");
        assertThat(retried.taskId()).isEqualTo(802L);
        assertThat(stopped).isTrue();

        verify(runMapper).deferExternalTask(eq(501L), eq(7L), eq(1L), eq(3L), eq(2L),
            org.mockito.ArgumentMatchers.matches("[0-9a-f]{64}"), eq("digital_human_generation"), eq(701L),
            eq(LocalDateTime.of(2026, 8, 15, 12, 1)), eq(DATABASE_NOW));
        verify(runMapper).advanceExternalTask(eq(501L), eq(7L), eq(1L), eq(3L), eq(2L),
            org.mockito.ArgumentMatchers.matches("[0-9a-f]{64}"), eq("digital_human_generation"), eq(701L),
            eq("digital_human_generation"), eq(702L), eq(LocalDateTime.of(2026, 8, 15, 12, 1)),
            eq(DATABASE_NOW));
        verify(runMapper).retryExternalTask(eq(501L), eq(7L), eq(1L), eq(3L), eq(2L),
            org.mockito.ArgumentMatchers.matches("[0-9a-f]{64}"), eq(801L), eq(802L),
            eq(LocalDateTime.of(2026, 8, 15, 12, 1)), eq(DATABASE_NOW));
        verify(runMapper).stopOwnedRun(eq(501L), eq(7L), eq(1L), eq(3L), eq("cancelled"),
            eq("USER_CANCELLED"), eq("用户已取消执行"), eq(DATABASE_NOW));
    }

    @Test
    void recoversTheSameWaitingRunAndTaskWithANewFenceOnlyAfterExpiry() {
        AgentRun waiting = run(501, 7, "waiting_external_task", 2, 1);
        waiting.setWaitingTaskSource("digital_human_generation");
        waiting.setWaitingTaskId(701L);
        waiting.setWaitingContractRevision(1L);
        when(runMapper.selectOne(any(Wrapper.class))).thenReturn(waiting);
        when(runMapper.selectDatabaseNow()).thenReturn(DATABASE_NOW);
        when(runMapper.recoverWaitingLease(anyLong(), anyLong(), anyLong(), anyLong(), anyLong(),
            anyString(), anyLong(), anyString(), anyString(), any(LocalDateTime.class), any(LocalDateTime.class)))
            .thenReturn(1);

        IAgentRunService.AgentRunLease recovered = service().claim(principal(7),
            new IAgentRunService.ClaimAgentRunCommand(501, 2, 1, "worker-b", 45));

        assertThat(recovered.agentRunId()).isEqualTo(501);
        assertThat(recovered.rowVersion()).isEqualTo(3);
        assertThat(recovered.leaseGeneration()).isEqualTo(2);
        assertThat(recovered.waitingTaskSource()).isEqualTo("digital_human_generation");
        assertThat(recovered.waitingTaskId()).isEqualTo(701);
        verify(runMapper).recoverWaitingLease(eq(501L), eq(7L), eq(1L), eq(2L), eq(1L),
            eq("digital_human_generation"), eq(701L), eq("worker-b"),
            org.mockito.ArgumentMatchers.matches("[0-9a-f]{64}"), eq(DATABASE_NOW),
            eq(DATABASE_NOW.plusSeconds(45)));
    }

    @Test
    void terminalRunCannotBeClaimedAndEveryAnnotatedCasCarriesItsFences() {
        AgentRun terminal = run(501, 7, "completed", 3, 2);
        when(runMapper.selectOne(any(Wrapper.class))).thenReturn(terminal);

        assertThat(service().claim(principal(7),
            new IAgentRunService.ClaimAgentRunCommand(501, 3, 1, "worker", 30))).isNull();
        verify(runMapper, never()).claimLease(anyLong(), anyLong(), anyLong(), anyLong(), anyLong(),
            anyString(), anyString(), any(), any());
        verify(runMapper, never()).recoverWaitingLease(anyLong(), anyLong(), anyLong(), anyLong(), anyLong(),
            anyString(), anyLong(), anyString(), anyString(), any(), any());

        String waitCas = updateSql("waitForExternalTask");
        assertThat(waitCas).contains("lease_expires_at > #{databaseNow}",
            "FROM av_ai_task task", "task.task_id = #{taskId}", "task.owner_user_id = #{ownerUserId}",
            "task.task_type = 'timeline_render'",
            "task.task_status IN ('pending', 'queued', 'running', 'success')",
            "FROM av_dh_generation_job job", "job.id = #{taskId}", "job.owner_user_id = #{ownerUserId}",
            "job.job_type IN ('voice_generate', 'video_generate')",
            "job.status IN ('queued', 'running', 'succeeded')",
            "#{taskSource} = 'ai_task'", "#{taskSource} = 'digital_human_generation'");
        String blockCas = updateSql("blockForInput");
        assertThat(blockCas).contains("run_status = 'queued'", "run_status = 'waiting_input'",
            "owner_user_id = #{ownerUserId}", "contract_revision = #{expectedContractRevision}",
            "row_version = #{expectedRowVersion}", "lease_token_digest IS NULL",
            "waiting_task_source IS NULL", "error_code = #{errorCode}", "error_summary = #{errorSummary}");
        String deferCas = updateSql("deferExternalTask");
        assertThat(deferCas).contains("run_status = 'waiting_external_task'",
            "contract_revision = #{expectedContractRevision}", "row_version = #{expectedRowVersion}",
            "lease_generation = #{expectedLeaseGeneration}", "lease_token_digest = #{leaseTokenDigest}",
            "lease_expires_at > #{databaseNow}", "waiting_task_source = #{taskSource}",
            "waiting_task_id = #{taskId}", "job.job_type IN ('voice_generate', 'video_generate')",
            "job.status IN ('queued', 'running')");
        String advanceCas = updateSql("advanceExternalTask");
        assertThat(advanceCas).contains("waiting_task_source = #{completedTaskSource}",
            "waiting_task_id = #{completedTaskId}", "waiting_task_source = #{nextTaskSource}",
            "waiting_task_id = #{nextTaskId}", "next_job.parent_job_id = completed_job.id",
            "completed_job.job_type = 'voice_generate'", "next_job.job_type = 'video_generate'",
            "project.source_type = 'digital_human_job'", "project.source_ref_id = completed_job.id",
            "next_task.resource_type = 'creation_project'", "next_task.resource_id = project.project_id",
            "lease_generation = #{expectedLeaseGeneration}", "lease_token_digest = #{leaseTokenDigest}",
            "lease_expires_at > #{databaseNow}");
        String retryCas = updateSql("retryExternalTask");
        assertThat(retryCas).contains("retry_count = retry_count + 1", "waiting_task_id = #{failedTaskId}",
            "waiting_task_id = #{retryTaskId}", "failed_task.task_status = 'failed'",
            "retry_task.resource_id = failed_task.resource_id",
            "retry_task.input_version_id = failed_task.input_version_id",
            "retry_task.input_version_id IS NULL AND failed_task.input_version_id IS NULL",
            "retry_task.task_id <> failed_task.task_id", "retry_task.task_type = 'timeline_render'",
            "lease_generation = #{expectedLeaseGeneration}", "lease_token_digest = #{leaseTokenDigest}",
            "lease_expires_at > #{databaseNow}");
        String externalCas = updateSql("completeExternalTask");
        assertThat(externalCas).contains("owner_user_id", "run_status = 'waiting_external_task'", "row_version",
            "lease_generation", "lease_token_digest", "waiting_task_source", "waiting_task_id",
            "waiting_contract_revision", "contract_revision", "FROM av_creation_asset asset",
            "asset.asset_id = #{candidateAssetId}", "asset.owner_user_id = #{ownerUserId}",
            "asset.asset_status = 'ready'", "asset.asset_type = 'video'", "asset.del_flag = '0'",
            "asset.source_ref_id = #{taskId}", "asset.usage_origin = 'timeline_render_output'",
            "task.task_status = 'success'", "task.result_asset_id = asset.asset_id",
            "asset.usage_origin = 'digital_human_output'", "job.status = 'succeeded'",
            "job.job_type = 'video_generate'");
        String finishCas = updateSql("finishLease");
        assertThat(finishCas).contains("lease_expires_at > #{databaseNow}",
            "waiting_task_source = NULL", "waiting_task_id = NULL", "waiting_contract_revision = NULL",
            "run_status = 'waiting_external_task'", "#{terminalStatus} IN ('failed', 'cancelled')",
            "waiting_contract_revision = #{expectedContractRevision}",
            "FROM av_ai_task task", "task.task_id = waiting_task_id",
            "task.owner_user_id = #{ownerUserId}", "task.task_type = 'timeline_render'",
            "task.task_status = #{terminalStatus}",
            "FROM av_dh_generation_job job", "job.id = waiting_task_id",
            "job.owner_user_id = #{ownerUserId}", "job.job_type IN ('voice_generate', 'video_generate')",
            "job.status = #{terminalStatus}",
            "#{terminalStatus} <> 'completed'", "FROM av_creation_asset asset",
            "asset.asset_id = #{candidateAssetId}", "asset.owner_user_id = #{ownerUserId}",
            "asset.asset_status = 'ready'", "asset.asset_type = 'video'", "asset.del_flag = '0'",
            "asset.usage_origin IN ('digital_human_output', 'timeline_render_output')");
        String stopCas = updateSql("stopOwnedRun");
        assertThat(stopCas).contains("owner_user_id = #{ownerUserId}",
            "contract_revision = #{expectedContractRevision}", "row_version = #{expectedRowVersion}",
            "run_status IN ('queued', 'waiting_input', 'waiting_approval', 'running', 'waiting_external_task')",
            "lease_owner = NULL", "lease_token_digest = NULL", "waiting_task_source = NULL",
            "waiting_task_id = NULL", "waiting_contract_revision = NULL", "finished_at = #{databaseNow}");
        String waitingRecovery = updateSql("recoverWaitingLease");
        assertThat(waitingRecovery).contains("resume_after <= #{databaseNow}",
            "lease_expires_at <= #{databaseNow}", "waiting_task_source", "waiting_task_id");
        String evaluationCas = insertSql(AgentRunEvaluationMapper.class, "insertFenced");
        assertThat(evaluationCas).contains("run.quality_repair_count = #{row.candidateNo}",
            "run.lease_token_digest = #{leaseTokenDigest}", "task.task_status = 'success'",
            "asset.usage_origin = 'timeline_render_output'", "asset.source_ref_id = task.task_id",
            "asset.sha256 = #{artifactSha256}", "project.owner_user_id = run.owner_user_id");
        String qualityApprovalCas = updateSql("requestQualityApproval");
        assertThat(qualityApprovalCas).contains("run_status = 'waiting_approval'",
            "run.candidate_asset_id = evaluation.result_asset_id", "run.lease_token_digest = NULL",
            "evaluation.candidate_no = run.quality_repair_count", "evaluation.decision = #{requiredDecision}");
        String repairCas = updateSql("startQualityRepair");
        assertThat(repairCas).contains("quality_repair_count = run.quality_repair_count + 1",
            "run.quality_repair_count < 2", "#{repairScope} = 'render'",
            "next_task.resource_id = old_task.resource_id", "#{repairScope} = 'timeline_render'",
            "next_project.source_ref_id = old_project.source_ref_id").doesNotContain("retry_count =");
        String finalCas = updateSql("approveFinal");
        assertThat(finalCas).contains("approval.approval_type = 'final'", "evaluation.decision = 'final'",
            "task.result_asset_id = evaluation.result_asset_id", "asset.asset_status = 'ready'",
            "asset.usage_origin = 'timeline_render_output'", "run.run_status = 'completed'");
        Method databaseClock = List.of(AgentRunMapper.class.getDeclaredMethods()).stream()
            .filter(candidate -> candidate.getName().equals("selectDatabaseNow"))
            .findFirst()
            .orElseThrow();
        assertThat(databaseClock.getAnnotation(Select.class).value()).containsExactly("SELECT UTC_TIMESTAMP(6)");
    }

    private AgentRunServiceImpl service() {
        return new AgentRunServiceImpl(briefMapper, profileMapper, runMapper, evaluationMapper, approvalMapper,
            JsonMapper.builder().build());
    }

    private String qualityJson(long taskId, long assetId, Set<String> failures,
                               TimelineOutputQualityDTO.Confidence failureConfidence) {
        List<TimelineOutputQualityDTO.Criterion> criteria = QUALITY_CODES.stream().map(code -> {
            TimelineOutputQualityDTO.Layer layer = code.startsWith("media.")
                ? TimelineOutputQualityDTO.Layer.MEDIA
                : code.startsWith("perceptual.") || code.startsWith("style.")
                ? TimelineOutputQualityDTO.Layer.PERCEPTUAL : TimelineOutputQualityDTO.Layer.CONTENT_LAYOUT;
            boolean subjective = layer == TimelineOutputQualityDTO.Layer.PERCEPTUAL;
            return new TimelineOutputQualityDTO.Criterion(code, layer, "rule-v1",
                failures.contains(code) ? TimelineOutputQualityDTO.Verdict.FAIL
                    : subjective ? TimelineOutputQualityDTO.Verdict.REVIEW : TimelineOutputQualityDTO.Verdict.PASS,
                failures.contains(code) ? failureConfidence
                    : subjective ? TimelineOutputQualityDTO.Confidence.LOW : TimelineOutputQualityDTO.Confidence.HIGH,
                Map.of());
        }).toList();
        try {
            return JsonMapper.builder().build().writeValueAsString(new TimelineOutputQualityDTO(
                Long.toString(taskId), Long.toString(assetId), "a".repeat(64), "1", "b".repeat(64),
                "quality-rules-1", criteria));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private AppPrincipalSnapshotDTO principal(long userId) {
        return new AppPrincipalSnapshotDTO(userId, "user" + userId, "web", 1L, 1L, 1L, 1L, null);
    }

    private DeliveryBriefVersion brief(long versionId, long briefId, long owner, long versionNo, Long parentId) {
        DeliveryBriefVersion row = new DeliveryBriefVersion();
        row.setDeliveryBriefVersionId(versionId);
        row.setBriefId(briefId);
        row.setOwnerUserId(owner);
        row.setVersionNo(versionNo);
        row.setParentVersionId(parentId);
        row.setSchemaVersion("delivery-brief-1");
        row.setDeliveryType("image_to_digital_human_video");
        row.setBriefHash("a".repeat(64));
        return row;
    }

    private AcceptanceProfileVersion profile(long versionId, long profileId, long owner, long briefVersionId) {
        AcceptanceProfileVersion row = new AcceptanceProfileVersion();
        row.setAcceptanceProfileVersionId(versionId);
        row.setAcceptanceProfileId(profileId);
        row.setOwnerUserId(owner);
        row.setDeliveryBriefVersionId(briefVersionId);
        row.setVersionNo(1L);
        row.setSchemaVersion("acceptance-profile-1");
        row.setPolicyVersion("acceptance-policy-1");
        row.setProfileHash("b".repeat(64));
        return row;
    }

    private AgentRun run(long runId, long owner, String status, long rowVersion, long generation) {
        AgentRun run = new AgentRun();
        run.setAgentRunId(runId);
        run.setOwnerUserId(owner);
        run.setDeliveryBriefVersionId(101L);
        run.setAcceptanceProfileVersionId(301L);
        run.setContractRevision(1L);
        run.setRunStatus(status);
        run.setRowVersion(rowVersion);
        run.setLeaseGeneration(generation);
        run.setRetryCount(0L);
        run.setQualityRepairCount(0L);
        run.setApprovalRevision(0L);
        run.setStateChangedAt(DATABASE_NOW);
        return run;
    }

    private AgentRunEvaluation evaluation(long evaluationId, long runId, long owner, long candidateNo,
                                          long taskId, long assetId, long projectId,
                                          String decision, String repairScope) {
        AgentRunEvaluation row = new AgentRunEvaluation();
        row.setEvaluationId(evaluationId);
        row.setAgentRunId(runId);
        row.setOwnerUserId(owner);
        row.setCandidateNo(candidateNo);
        row.setRenderTaskId(taskId);
        row.setResultAssetId(assetId);
        row.setProjectId(projectId);
        row.setRuleSetVersion("quality-rules-1");
        row.setQualityJson("{\"criteria\":[]}");
        row.setQualityDigest("c".repeat(64));
        row.setDecision(decision);
        row.setRepairScope(repairScope);
        return row;
    }

    private String updateSql(String methodName) {
        Method method = List.of(AgentRunMapper.class.getDeclaredMethods()).stream()
            .filter(candidate -> candidate.getName().equals(methodName))
            .findFirst()
            .orElseThrow();
        return String.join(" ", method.getAnnotation(Update.class).value());
    }

    private String insertSql(Class<?> mapperType, String methodName) {
        Method method = List.of(mapperType.getDeclaredMethods()).stream()
            .filter(candidate -> candidate.getName().equals(methodName))
            .findFirst()
            .orElseThrow();
        return String.join(" ", method.getAnnotation(org.apache.ibatis.annotations.Insert.class).value());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private ArgumentCaptor<Wrapper<DeliveryBriefVersion>> wrapperCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(Wrapper.class);
    }

    private static void initialize(Class<?> type) {
        if (TableInfoHelper.getTableInfo(type) == null) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), type);
        }
    }
}
