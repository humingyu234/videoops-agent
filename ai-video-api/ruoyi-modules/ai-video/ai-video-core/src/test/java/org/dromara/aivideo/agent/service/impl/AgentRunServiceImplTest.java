package org.dromara.aivideo.agent.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.aivideo.agent.domain.AcceptanceProfileVersion;
import org.dromara.aivideo.agent.domain.AgentRun;
import org.dromara.aivideo.agent.domain.DeliveryBriefVersion;
import org.dromara.aivideo.agent.mapper.AcceptanceProfileVersionMapper;
import org.dromara.aivideo.agent.mapper.AgentRunMapper;
import org.dromara.aivideo.agent.mapper.DeliveryBriefVersionMapper;
import org.dromara.aivideo.agent.service.IAgentRunService;
import org.dromara.aivideo.identity.dto.AppPrincipalSnapshotDTO;
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

    @Mock
    private DeliveryBriefVersionMapper briefMapper;
    @Mock
    private AcceptanceProfileVersionMapper profileMapper;
    @Mock
    private AgentRunMapper runMapper;

    @BeforeAll
    static void initializeMybatisMetadata() {
        initialize(DeliveryBriefVersion.class);
        initialize(AcceptanceProfileVersion.class);
        initialize(AgentRun.class);
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
            "job.job_type = 'video_generate'", "job.status IN ('queued', 'running', 'succeeded')",
            "#{taskSource} = 'ai_task'", "#{taskSource} = 'digital_human_generation'");
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
            "job.owner_user_id = #{ownerUserId}", "job.job_type = 'video_generate'",
            "job.status = #{terminalStatus}",
            "#{terminalStatus} <> 'completed'", "FROM av_creation_asset asset",
            "asset.asset_id = #{candidateAssetId}", "asset.owner_user_id = #{ownerUserId}",
            "asset.asset_status = 'ready'", "asset.asset_type = 'video'", "asset.del_flag = '0'",
            "asset.usage_origin IN ('digital_human_output', 'timeline_render_output')");
        String waitingRecovery = updateSql("recoverWaitingLease");
        assertThat(waitingRecovery).contains("resume_after <= #{databaseNow}",
            "lease_expires_at <= #{databaseNow}", "waiting_task_source", "waiting_task_id");
        Method databaseClock = List.of(AgentRunMapper.class.getDeclaredMethods()).stream()
            .filter(candidate -> candidate.getName().equals("selectDatabaseNow"))
            .findFirst()
            .orElseThrow();
        assertThat(databaseClock.getAnnotation(Select.class).value()).containsExactly("SELECT UTC_TIMESTAMP(6)");
    }

    private AgentRunServiceImpl service() {
        return new AgentRunServiceImpl(briefMapper, profileMapper, runMapper, JsonMapper.builder().build());
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
        run.setStateChangedAt(DATABASE_NOW);
        return run;
    }

    private String updateSql(String methodName) {
        Method method = List.of(AgentRunMapper.class.getDeclaredMethods()).stream()
            .filter(candidate -> candidate.getName().equals(methodName))
            .findFirst()
            .orElseThrow();
        return String.join(" ", method.getAnnotation(Update.class).value());
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
