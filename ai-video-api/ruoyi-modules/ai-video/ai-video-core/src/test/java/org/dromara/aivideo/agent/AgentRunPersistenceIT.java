package org.dromara.aivideo.agent;

import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.DynamicTableNameInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.session.SqlSessionFactory;
import org.dromara.aivideo.agent.mapper.AcceptanceProfileVersionMapper;
import org.dromara.aivideo.agent.mapper.AgentRunMapper;
import org.dromara.aivideo.agent.mapper.DeliveryBriefVersionMapper;
import org.dromara.aivideo.agent.service.IAgentRunService;
import org.dromara.aivideo.agent.service.impl.AgentRunServiceImpl;
import org.dromara.aivideo.identity.dto.AppPrincipalSnapshotDTO;
import org.dromara.aivideo.testsupport.LocalIntegrationEnvironment;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.mybatis.handler.InjectionMetaObjectHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import tools.jackson.databind.json.JsonMapper;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Crosses the real MySQL boundary for the frozen T2 AgentRun persistence contract.
 *
 * <p>The production migration is never executed against {@code ai_video_test}. Its three
 * {@code CREATE TABLE} statements are rewritten to random names, while three minimal random fact
 * tables expose only the columns queried by AgentRun fencing predicates. Cleanup stays scoped to
 * this test run.</p>
 */
@Tag("dev")
class AgentRunPersistenceIT {

    private static final LocalIntegrationEnvironment ENV = LocalIntegrationEnvironment.requireFromEnvironment();
    private static final String BRIEF = "brief";
    private static final String PROFILE = "profile";
    private static final String RUN = "run";
    private static final String AI_TASK = "aiTask";
    private static final String DH_JOB = "digitalHumanJob";
    private static final String CREATION_ASSET = "creationAsset";
    private static final Pattern SAFE_TABLE = Pattern.compile("a(?:db|ap|run|at|dh|ca)_it_[a-f0-9]{32}");
    private static final Pattern CHECK_NAME = Pattern.compile("CONSTRAINT\\s+[A-Za-z0-9_]+\\s+CHECK");
    private static final Map<String, String> SOURCE_TABLES = Map.of(
        BRIEF, "av_delivery_brief_version",
        PROFILE, "av_acceptance_profile_version",
        RUN, "av_agent_run",
        AI_TASK, "av_ai_task",
        DH_JOB, "av_dh_generation_job",
        CREATION_ASSET, "av_creation_asset"
    );

    private final Map<String, String> tables = new LinkedHashMap<>();

    @BeforeEach
    void createFrozenContractTables() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        tables.put(BRIEF, "adb_it_" + suffix);
        tables.put(PROFILE, "aap_it_" + suffix);
        tables.put(RUN, "arun_it_" + suffix);
        tables.put(AI_TASK, "aat_it_" + suffix);
        tables.put(DH_JOB, "adh_it_" + suffix);
        tables.put(CREATION_ASSET, "aca_it_" + suffix);

        String migration = Files.readString(findRepositoryRoot().resolve(
            "docs/sql/videoops-agent/mysql/100_agent_run_schema.sql"), StandardCharsets.UTF_8);
        try (Connection connection = ENV.openMySqlConnection(); Statement statement = connection.createStatement()) {
            for (String key : List.of(BRIEF, PROFILE, RUN)) {
                statement.execute(extractAndRewriteDdl(migration, SOURCE_TABLES.get(key), table(key)));
            }
            statement.execute(minimalAiTaskDdl());
            statement.execute(minimalDigitalHumanJobDdl());
            statement.execute(minimalCreationAssetDdl());
        }
    }

    @AfterEach
    void dropFrozenContractTables() throws SQLException {
        if (tables.isEmpty()) {
            return;
        }
        try (Connection connection = ENV.openMySqlConnection(); Statement statement = connection.createStatement()) {
            for (String key : List.of(RUN, CREATION_ASSET, AI_TASK, DH_JOB, PROFILE, BRIEF)) {
                String table = table(key);
                statement.execute("DROP TABLE IF EXISTS `" + table + "`");
            }
        }
    }

    @Test
    void frozenSchemaEnforcesOwnerScopedIdempotencyAndRunStateChecks() throws Exception {
        try (Connection connection = ENV.openMySqlConnection()) {
            assertThat(databaseName(connection)).isEqualTo("ai_video_test");
            assertThat(foreignKeyCount(connection)).isZero();
            assertThat(checkConstraintCount(connection)).isEqualTo(25L);
            assertThat(temporalPrecision(connection, "lease_expires_at")).isEqualTo(6L);
            assertThat(temporalPrecision(connection, "resume_after")).isEqualTo(6L);
        }

        AppPrincipalSnapshotDTO firstOwner = principal(301L);
        AppPrincipalSnapshotDTO secondOwner = principal(302L);
        try (AnnotationConfigApplicationContext context = openServiceContext()) {
            IAgentRunService service = context.getBean(IAgentRunService.class);
            var firstBriefCommand = new IAgentRunService.AppendDeliveryBriefCommand(
                null, null, "brief-idem", "{\"copy\":\"first\"}");
            var firstBrief = service.appendDeliveryBrief(firstOwner, firstBriefCommand);
            assertThat(service.appendDeliveryBrief(firstOwner, firstBriefCommand).deliveryBriefVersionId())
                .isEqualTo(firstBrief.deliveryBriefVersionId());
            assertThatThrownBy(() -> service.appendDeliveryBrief(firstOwner,
                new IAgentRunService.AppendDeliveryBriefCommand(
                    null, null, "brief-idem", "{\"copy\":\"different\"}")))
                .isInstanceOf(ServiceException.class);
            var secondBriefVersion = service.appendDeliveryBrief(firstOwner,
                new IAgentRunService.AppendDeliveryBriefCommand(firstBrief.briefId(),
                    firstBrief.deliveryBriefVersionId(), "brief-v2", "{\"copy\":\"second\"}"));
            assertThat(secondBriefVersion.versionNo()).isEqualTo(2L);

            var otherOwnerBrief = service.appendDeliveryBrief(secondOwner, firstBriefCommand);
            assertThat(otherOwnerBrief.deliveryBriefVersionId()).isNotEqualTo(firstBrief.deliveryBriefVersionId());

            var firstProfileCommand = new IAgentRunService.AppendAcceptanceProfileCommand(
                null, null, firstBrief.deliveryBriefVersionId(), "profile-idem", "{\"subtitles\":true}");
            var firstProfile = service.appendAcceptanceProfile(firstOwner, firstProfileCommand);
            assertThat(service.appendAcceptanceProfile(firstOwner, firstProfileCommand)
                .acceptanceProfileVersionId()).isEqualTo(firstProfile.acceptanceProfileVersionId());
            var secondProfileVersion = service.appendAcceptanceProfile(firstOwner,
                new IAgentRunService.AppendAcceptanceProfileCommand(firstProfile.acceptanceProfileId(),
                    firstProfile.acceptanceProfileVersionId(), secondBriefVersion.deliveryBriefVersionId(),
                    "profile-v2", "{\"subtitles\":true,\"outline\":true}"));
            assertThat(secondProfileVersion.versionNo()).isEqualTo(2L);

            var otherOwnerProfile = service.appendAcceptanceProfile(secondOwner,
                new IAgentRunService.AppendAcceptanceProfileCommand(null, null,
                    otherOwnerBrief.deliveryBriefVersionId(), "profile-idem", "{\"subtitles\":true}"));

            var firstRunCommand = new IAgentRunService.CreateAgentRunCommand(
                secondBriefVersion.deliveryBriefVersionId(), secondProfileVersion.acceptanceProfileVersionId(),
                "run-idem");
            var firstRun = service.createRun(firstOwner, firstRunCommand);
            assertThat(service.createRun(firstOwner, firstRunCommand).agentRunId()).isEqualTo(firstRun.agentRunId());
            var firstRunLease = service.claim(firstOwner, new IAgentRunService.ClaimAgentRunCommand(
                firstRun.agentRunId(), firstRun.rowVersion(), firstRun.contractRevision(),
                "agent-it-dh-worker", 300L));
            assertThat(firstRunLease).isNotNull();
            try (Connection connection = ENV.openMySqlConnection()) {
                insertDigitalHumanJob(connection, 900L, secondOwner.appUserId(), "video_generate", "running");
                insertDigitalHumanJob(connection, 901L, firstOwner.appUserId(), "video_generate", "running");
            }
            assertThat(service.waitForExternalTask(firstOwner,
                new IAgentRunService.WaitForExternalTaskCommand(firstRunLease.proof(),
                    "digital_human_generation", 900L, Instant.now().plusSeconds(120L)))).isNull();
            try (Connection connection = ENV.openMySqlConnection()) {
                updateDigitalHumanJob(connection, 900L, firstOwner.appUserId(), "voice_generate", "running");
            }
            assertThat(service.waitForExternalTask(firstOwner,
                new IAgentRunService.WaitForExternalTaskCommand(firstRunLease.proof(),
                    "digital_human_generation", 900L, Instant.now().plusSeconds(120L)))).isNull();
            try (Connection connection = ENV.openMySqlConnection()) {
                updateDigitalHumanJob(connection, 900L, firstOwner.appUserId(), "video_generate", "failed");
            }
            assertThat(service.waitForExternalTask(firstOwner,
                new IAgentRunService.WaitForExternalTaskCommand(firstRunLease.proof(),
                    "digital_human_generation", 900L, Instant.now().plusSeconds(120L)))).isNull();

            var dhWaiting = service.waitForExternalTask(firstOwner,
                new IAgentRunService.WaitForExternalTaskCommand(firstRunLease.proof(),
                    "digital_human_generation", 901L, Instant.now().plusSeconds(120L)));
            assertThat(dhWaiting).isNotNull();
            assertThat(service.completeExternalTask(firstOwner,
                new IAgentRunService.CompleteExternalTaskCommand(dhWaiting.lease(),
                    "digital_human_generation", 901L, 911L, "{\"assetId\":911}"))).isFalse();
            try (Connection connection = ENV.openMySqlConnection()) {
                updateDigitalHumanJob(connection, 901L, firstOwner.appUserId(), "video_generate", "succeeded");
            }
            assertThat(service.completeExternalTask(firstOwner,
                new IAgentRunService.CompleteExternalTaskCommand(dhWaiting.lease(),
                    "digital_human_generation", 901L, 911L, "{\"assetId\":911}"))).isFalse();
            try (Connection connection = ENV.openMySqlConnection()) {
                insertCreationAsset(connection, 911L, firstOwner.appUserId(), "digital_human_output", 901L,
                    "ready");
            }
            assertThat(service.completeExternalTask(firstOwner,
                new IAgentRunService.CompleteExternalTaskCommand(dhWaiting.lease(),
                    "digital_human_generation", 901L, 911L, "{\"assetId\":911}"))).isTrue();

            var otherOwnerRun = service.createRun(secondOwner, new IAgentRunService.CreateAgentRunCommand(
                otherOwnerBrief.deliveryBriefVersionId(), otherOwnerProfile.acceptanceProfileVersionId(),
                "run-idem"));
            assertThat(otherOwnerRun.agentRunId()).isNotEqualTo(firstRun.agentRunId());
            assertThatThrownBy(() -> service.getOwnedRun(secondOwner, firstRun.agentRunId()))
                .isInstanceOf(ServiceException.class);

            var failedLease = service.claim(secondOwner, new IAgentRunService.ClaimAgentRunCommand(
                otherOwnerRun.agentRunId(), otherOwnerRun.rowVersion(), otherOwnerRun.contractRevision(),
                "agent-it-failed-worker", 300L));
            assertThat(failedLease).isNotNull();
            try (Connection connection = ENV.openMySqlConnection()) {
                insertAiTask(connection, 902L, secondOwner.appUserId(), "timeline_render", "running", null);
            }
            var failedWaiting = service.waitForExternalTask(secondOwner,
                new IAgentRunService.WaitForExternalTaskCommand(failedLease.proof(),
                    "ai_task", 902L, Instant.now().plusSeconds(120L)));
            assertThat(failedWaiting).isNotNull();
            var failedFinish = new IAgentRunService.FinishAgentRunCommand(
                failedWaiting.lease(), "failed", null, null, "EXTERNAL_TASK_FAILED", "External task failed");
            assertThat(service.finishLease(secondOwner, failedFinish)).isFalse();
            try (Connection connection = ENV.openMySqlConnection()) {
                updateAiTask(connection, 902L, secondOwner.appUserId(), "timeline_render", "failed", null);
            }
            var staleProof = new IAgentRunService.LeaseProof(otherOwnerRun.agentRunId(),
                failedWaiting.lease().rowVersion(), failedWaiting.lease().contractRevision(),
                failedWaiting.lease().leaseGeneration(), "stale-token");
            assertThat(service.finishLease(secondOwner, new IAgentRunService.FinishAgentRunCommand(
                staleProof, "failed", null, null, "EXTERNAL_TASK_FAILED", "External task failed"))).isFalse();
            assertThat(service.finishLease(secondOwner, new IAgentRunService.FinishAgentRunCommand(
                failedWaiting.lease(), "cancelled", null, null, "EXTERNAL_TASK_CANCELLED",
                "External task cancelled"))).isFalse();
            assertThat(service.finishLease(secondOwner, failedFinish)).isTrue();
            assertThat(service.finishLease(secondOwner, failedFinish)).isFalse();

            var failedRun = service.getOwnedRun(secondOwner, otherOwnerRun.agentRunId());
            assertThat(failedRun.runStatus()).isEqualTo("failed");
            assertThat(failedRun.waitingTaskSource()).isNull();
            assertThat(failedRun.waitingTaskId()).isNull();
        }

        try (Connection connection = ENV.openMySqlConnection()) {
            assertThatThrownBy(() -> insertInvalidRunningRun(connection, 124L, 301L, 101L, 111L))
                .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> insertRunningRunWithNullToken(
                connection, 125L, 301L, 101L, 111L)).isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> insertWaitingRunWithNullSource(
                connection, 126L, 301L, 101L, 111L)).isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> insertCompletedRunWithNullDigest(
                connection, 127L, 301L, 101L, 111L)).isInstanceOf(SQLException.class);
            assertThat(rowCount(connection, table(BRIEF))).isEqualTo(3L);
            assertThat(rowCount(connection, table(PROFILE))).isEqualTo(3L);
            assertThat(rowCount(connection, table(RUN))).isEqualTo(2L);
        }
    }

    @Test
    void freshConnectionsRecoverTheSameExpiredRunAndRejectEveryStaleResult() throws Exception {
        long ownerUserId = 401L;
        AppPrincipalSnapshotDTO principal = principal(ownerUserId);
        long agentRunId;
        IAgentRunService.AgentRunLease firstLease;

        try (AnnotationConfigApplicationContext initialContext = openServiceContext()) {
            IAgentRunService service = initialContext.getBean(IAgentRunService.class);
            var brief = service.appendDeliveryBrief(principal,
                new IAgentRunService.AppendDeliveryBriefCommand(
                    null, null, "recovery-brief", "{\"copy\":\"recover me\"}"));
            var profile = service.appendAcceptanceProfile(principal,
                new IAgentRunService.AppendAcceptanceProfileCommand(null, null,
                    brief.deliveryBriefVersionId(), "recovery-profile", "{\"subtitles\":true}"));
            var run = service.createRun(principal, new IAgentRunService.CreateAgentRunCommand(
                brief.deliveryBriefVersionId(), profile.acceptanceProfileVersionId(), "run-recovery"));
            agentRunId = run.agentRunId();
            firstLease = service.claim(principal, new IAgentRunService.ClaimAgentRunCommand(
                agentRunId, run.rowVersion(), run.contractRevision(), "agent-it-worker-a", 300L));
            assertThat(firstLease).isNotNull();
            assertThat(firstLease.leaseGeneration()).isEqualTo(1L);
        }

        try (Connection connection = ENV.openMySqlConnection()) {
            insertAiTask(connection, 700L, ownerUserId + 1L, "timeline_render", "running", null);
            insertAiTask(connection, 701L, ownerUserId, "timeline_render", "running", null);
        }

        IAgentRunService.WaitingReceipt waiting;
        try (AnnotationConfigApplicationContext waitingContext = openServiceContext()) {
            IAgentRunService service = waitingContext.getBean(IAgentRunService.class);
            assertThat(service.waitForExternalTask(principal, new IAgentRunService.WaitForExternalTaskCommand(
                firstLease.proof(), "ai_task", 700L, Instant.now().plusSeconds(120L)))).isNull();
            try (Connection connection = ENV.openMySqlConnection()) {
                updateAiTask(connection, 700L, ownerUserId, "timeline_subtitle_align", "running", null);
            }
            assertThat(service.waitForExternalTask(principal, new IAgentRunService.WaitForExternalTaskCommand(
                firstLease.proof(), "ai_task", 700L, Instant.now().plusSeconds(120L)))).isNull();
            try (Connection connection = ENV.openMySqlConnection()) {
                updateAiTask(connection, 700L, ownerUserId, "timeline_render", "failed", null);
            }
            assertThat(service.waitForExternalTask(principal, new IAgentRunService.WaitForExternalTaskCommand(
                firstLease.proof(), "ai_task", 700L, Instant.now().plusSeconds(120L)))).isNull();
            waiting = service.waitForExternalTask(principal, new IAgentRunService.WaitForExternalTaskCommand(
                firstLease.proof(), "ai_task", 701L, Instant.now().plusSeconds(120L)));
            assertThat(waiting).isNotNull();
            assertThat(waiting.lease().rowVersion()).isEqualTo(2L);
        }

        try (Connection connection = ENV.openMySqlConnection()) {
            assertThat(waitingDeadlinesMatch(connection, ownerUserId, agentRunId)).isTrue();
            assertThat(advanceContractAndExpireOnlyLease(connection, ownerUserId, agentRunId,
                waiting.lease().rowVersion(), waiting.lease().leaseGeneration(),
                sha256(waiting.lease().leaseToken()))).isEqualTo(1);
        }

        try (AnnotationConfigApplicationContext earlyRecoveryContext = openServiceContext()) {
            IAgentRunService service = earlyRecoveryContext.getBean(IAgentRunService.class);
            assertThat(service.claim(principal, new IAgentRunService.ClaimAgentRunCommand(
                agentRunId, waiting.lease().rowVersion(), 2L, "agent-it-worker-b", 300L))).isNull();
        }

        try (Connection connection = ENV.openMySqlConnection()) {
            assertThat(makeResumeDue(connection, ownerUserId, agentRunId, waiting.lease().rowVersion(),
                waiting.lease().leaseGeneration(), sha256(waiting.lease().leaseToken()))).isEqualTo(1);
        }

        try (AnnotationConfigApplicationContext restartedContext = openServiceContext()) {
            IAgentRunService service = restartedContext.getBean(IAgentRunService.class);
            var recovered = service.claim(principal, new IAgentRunService.ClaimAgentRunCommand(
                agentRunId, waiting.lease().rowVersion(), 2L, "agent-it-worker-b", 300L));
            assertThat(recovered).isNotNull();
            assertThat(recovered.agentRunId()).isEqualTo(agentRunId);
            assertThat(recovered.leaseGeneration()).isEqualTo(2L);
            assertThat(recovered.waitingTaskSource()).isEqualTo("ai_task");
            assertThat(recovered.waitingTaskId()).isEqualTo(701L);

            assertThat(service.completeExternalTask(principal,
                new IAgentRunService.CompleteExternalTaskCommand(
                    recovered.proof(), "ai_task", 701L, 801L, "{\"assetId\":801}"))).isFalse();
            try (Connection connection = ENV.openMySqlConnection()) {
                updateAiTask(connection, 701L, ownerUserId, "timeline_render", "success", 801L);
            }
            assertThat(service.completeExternalTask(principal,
                new IAgentRunService.CompleteExternalTaskCommand(
                    recovered.proof(), "ai_task", 701L, 801L, "{\"assetId\":801}"))).isFalse();
            try (Connection connection = ENV.openMySqlConnection()) {
                insertCreationAsset(connection, 801L, ownerUserId,
                    "timeline_render_output", 701L, "ready");
            }

            var oldTokenProof = new IAgentRunService.LeaseProof(agentRunId, recovered.rowVersion(),
                recovered.contractRevision(), recovered.leaseGeneration(), waiting.lease().leaseToken());
            assertThat(service.completeExternalTask(principal,
                new IAgentRunService.CompleteExternalTaskCommand(
                    oldTokenProof, "ai_task", 701L, 801L, "{\"assetId\":801}"))).isFalse();

            var oldRevisionProof = new IAgentRunService.LeaseProof(agentRunId, recovered.rowVersion(),
                1L, recovered.leaseGeneration(), recovered.leaseToken());
            assertThat(service.completeExternalTask(principal,
                new IAgentRunService.CompleteExternalTaskCommand(
                    oldRevisionProof, "ai_task", 701L, 801L, "{\"assetId\":801}"))).isFalse();
            assertThat(service.completeExternalTask(principal,
                new IAgentRunService.CompleteExternalTaskCommand(
                    recovered.proof(), "ai_task", 700L, 801L, "{\"assetId\":801}"))).isFalse();
            assertThat(service.completeExternalTask(principal,
                new IAgentRunService.CompleteExternalTaskCommand(
                    recovered.proof(), "ai_task", 701L, 801L, "{\"assetId\":801}"))).isTrue();
            assertThat(service.completeExternalTask(principal,
                new IAgentRunService.CompleteExternalTaskCommand(
                    recovered.proof(), "ai_task", 701L, 802L, "{\"assetId\":802}"))).isFalse();

            var completed = service.getOwnedRun(principal, agentRunId);
            assertThat(completed.runStatus()).isEqualTo("completed");
            assertThat(completed.rowVersion()).isEqualTo(4L);
            assertThat(completed.leaseGeneration()).isEqualTo(2L);
            assertThat(completed.candidateAssetId()).isEqualTo(801L);
        }

        try (Connection verificationConnection = ENV.openMySqlConnection()) {
            assertThat(rowCount(verificationConnection, table(RUN))).isEqualTo(1L);
        }
    }

    @Test
    void finishCompletedLeaseRequiresOwnedReadyNonDeletedOutputAsset() throws Exception {
        long ownerUserId = 501L;
        AppPrincipalSnapshotDTO principal = principal(ownerUserId);

        try (AnnotationConfigApplicationContext context = openServiceContext()) {
            IAgentRunService service = context.getBean(IAgentRunService.class);
            var brief = service.appendDeliveryBrief(principal,
                new IAgentRunService.AppendDeliveryBriefCommand(
                    null, null, "finish-brief", "{\"copy\":\"finish me\"}"));
            var profile = service.appendAcceptanceProfile(principal,
                new IAgentRunService.AppendAcceptanceProfileCommand(null, null,
                    brief.deliveryBriefVersionId(), "finish-profile", "{\"subtitles\":true}"));
            var run = service.createRun(principal, new IAgentRunService.CreateAgentRunCommand(
                brief.deliveryBriefVersionId(), profile.acceptanceProfileVersionId(), "finish-run"));
            var lease = service.claim(principal, new IAgentRunService.ClaimAgentRunCommand(
                run.agentRunId(), run.rowVersion(), run.contractRevision(), "agent-it-finish-worker", 300L));
            assertThat(lease).isNotNull();

            try (Connection connection = ENV.openMySqlConnection()) {
                insertCreationAsset(connection, 1202L, ownerUserId + 1L,
                    "timeline_render_output", 702L, "ready");
                insertCreationAsset(connection, 1203L, ownerUserId,
                    "timeline_render_output", 703L, "pending");
                insertCreationAsset(connection, 1204L, ownerUserId,
                    "timeline_render_output", 704L, "ready", "1");
                insertCreationAsset(connection, 1205L, ownerUserId,
                    "digital_human_output", 705L, "ready");
            }

            for (long rejectedAssetId : List.of(1201L, 1202L, 1203L, 1204L)) {
                assertThat(service.finishLease(principal, new IAgentRunService.FinishAgentRunCommand(
                    lease.proof(), "completed", rejectedAssetId,
                    "{\"assetId\":" + rejectedAssetId + "}", null, null))).isFalse();
            }
            var validFinish = new IAgentRunService.FinishAgentRunCommand(
                lease.proof(), "completed", 1205L, "{\"assetId\":1205}", null, null);
            assertThat(service.finishLease(principal, validFinish)).isTrue();
            assertThat(service.finishLease(principal, validFinish)).isFalse();

            var completed = service.getOwnedRun(principal, run.agentRunId());
            assertThat(completed.runStatus()).isEqualTo("completed");
            assertThat(completed.candidateAssetId()).isEqualTo(1205L);
        }
    }

    private boolean waitingDeadlinesMatch(Connection connection, long ownerUserId, long runId)
        throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT resume_after = lease_expires_at
            FROM `%s`
            WHERE owner_user_id = ? AND agent_run_id = ? AND run_status = 'waiting_external_task'
            """.formatted(table(RUN)))) {
            statement.setLong(1, ownerUserId);
            statement.setLong(2, runId);
            return singleLong(statement) == 1L;
        }
    }

    private int advanceContractAndExpireOnlyLease(Connection connection, long ownerUserId, long runId,
                                                  long rowVersion, long generation, String tokenDigest)
        throws SQLException {
        return update(connection, """
            UPDATE `%s`
            SET contract_revision = 2, waiting_contract_revision = 2,
                lease_expires_at = DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 1 SECOND)
            WHERE owner_user_id = ? AND agent_run_id = ? AND run_status = 'waiting_external_task'
              AND contract_revision = 1 AND waiting_contract_revision = 1
              AND row_version = ? AND lease_generation = ? AND lease_token_digest = ?
              AND waiting_task_source = 'ai_task' AND waiting_task_id = 701
            """.formatted(table(RUN)), ownerUserId, runId, rowVersion, generation, tokenDigest);
    }

    private int makeResumeDue(Connection connection, long ownerUserId, long runId,
                              long rowVersion, long generation, String tokenDigest) throws SQLException {
        return update(connection, """
            UPDATE `%s`
            SET resume_after = DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 1 SECOND)
            WHERE owner_user_id = ? AND agent_run_id = ? AND run_status = 'waiting_external_task'
              AND contract_revision = 2 AND waiting_contract_revision = 2
              AND row_version = ? AND lease_generation = ? AND lease_token_digest = ?
              AND lease_expires_at <= UTC_TIMESTAMP(6)
              AND waiting_task_source = 'ai_task' AND waiting_task_id = 701
            """.formatted(table(RUN)), ownerUserId, runId, rowVersion, generation, tokenDigest);
    }

    private void insertAiTask(Connection connection, long taskId, long ownerUserId, String taskType,
                              String taskStatus, Long resultAssetId) throws SQLException {
        update(connection, """
            INSERT INTO `%s` (task_id, owner_user_id, task_type, task_status, result_asset_id)
            VALUES (?, ?, ?, ?, ?)
            """.formatted(table(AI_TASK)), taskId, ownerUserId, taskType, taskStatus, resultAssetId);
    }

    private void updateAiTask(Connection connection, long taskId, long ownerUserId, String taskType,
                              String taskStatus, Long resultAssetId) throws SQLException {
        update(connection, """
            UPDATE `%s`
            SET owner_user_id = ?, task_type = ?, task_status = ?, result_asset_id = ?
            WHERE task_id = ?
            """.formatted(table(AI_TASK)), ownerUserId, taskType, taskStatus, resultAssetId, taskId);
    }

    private void insertDigitalHumanJob(Connection connection, long jobId, long ownerUserId,
                                       String jobType, String status) throws SQLException {
        update(connection, """
            INSERT INTO `%s` (id, owner_user_id, job_type, status)
            VALUES (?, ?, ?, ?)
            """.formatted(table(DH_JOB)), jobId, ownerUserId, jobType, status);
    }

    private void updateDigitalHumanJob(Connection connection, long jobId, long ownerUserId,
                                       String jobType, String status) throws SQLException {
        update(connection, """
            UPDATE `%s`
            SET owner_user_id = ?, job_type = ?, status = ?
            WHERE id = ?
            """.formatted(table(DH_JOB)), ownerUserId, jobType, status, jobId);
    }

    private void insertCreationAsset(Connection connection, long assetId, long ownerUserId,
                                     String usageOrigin, long sourceRefId, String assetStatus) throws SQLException {
        insertCreationAsset(connection, assetId, ownerUserId, usageOrigin, sourceRefId, assetStatus, "0");
    }

    private void insertCreationAsset(Connection connection, long assetId, long ownerUserId,
                                     String usageOrigin, long sourceRefId, String assetStatus,
                                     String delFlag) throws SQLException {
        update(connection, """
            INSERT INTO `%s` (
                asset_id, owner_user_id, asset_type, usage_origin, source_ref_id, asset_status, del_flag
            ) VALUES (?, ?, 'video', ?, ?, ?, ?)
            """.formatted(table(CREATION_ASSET)),
            assetId, ownerUserId, usageOrigin, sourceRefId, assetStatus, delFlag);
    }

    private void insertInvalidRunningRun(Connection connection, long runId, long ownerUserId,
                                         long briefVersionId, long profileVersionId) throws SQLException {
        update(connection, """
            INSERT INTO `%s` (
                agent_run_id, owner_user_id, delivery_brief_version_id, acceptance_profile_version_id,
                contract_revision, schema_version, idempotency_key, request_digest, run_status,
                actor_type, actor_id, create_by, update_by
            ) VALUES (?, ?, ?, ?, 1, 'agent-run-1', 'invalid-running', ?, 'running',
                      'app_user', ?, ?, ?)
            """.formatted(table(RUN)), runId, ownerUserId, briefVersionId, profileVersionId,
            hash('f'), ownerUserId, ownerUserId, ownerUserId);
    }

    private void insertRunningRunWithNullToken(Connection connection, long runId, long ownerUserId,
                                               long briefVersionId, long profileVersionId) throws SQLException {
        update(connection, """
            INSERT INTO `%s` (
                agent_run_id, owner_user_id, delivery_brief_version_id, acceptance_profile_version_id,
                contract_revision, schema_version, idempotency_key, request_digest, run_status,
                lease_generation, lease_owner, lease_expires_at,
                actor_type, actor_id, create_by, update_by
            ) VALUES (?, ?, ?, ?, 1, 'agent-run-1', 'invalid-null-token', ?, 'running',
                      1, 'agent-it-worker', DATE_ADD(UTC_TIMESTAMP(6), INTERVAL 1 MINUTE),
                      'app_user', ?, ?, ?)
            """.formatted(table(RUN)), runId, ownerUserId, briefVersionId, profileVersionId,
            hash('e'), ownerUserId, ownerUserId, ownerUserId);
    }

    private void insertWaitingRunWithNullSource(Connection connection, long runId, long ownerUserId,
                                                long briefVersionId, long profileVersionId) throws SQLException {
        update(connection, """
            INSERT INTO `%s` (
                agent_run_id, owner_user_id, delivery_brief_version_id, acceptance_profile_version_id,
                contract_revision, schema_version, idempotency_key, request_digest, run_status,
                lease_generation, lease_owner, lease_token_digest, lease_expires_at, resume_after,
                waiting_task_id, waiting_contract_revision,
                actor_type, actor_id, create_by, update_by
            ) VALUES (?, ?, ?, ?, 1, 'agent-run-1', 'invalid-null-task-source', ?, 'waiting_external_task',
                      1, 'agent-it-worker', ?, DATE_ADD(UTC_TIMESTAMP(6), INTERVAL 1 MINUTE),
                      DATE_ADD(UTC_TIMESTAMP(6), INTERVAL 1 MINUTE), 701, 1,
                      'app_user', ?, ?, ?)
            """.formatted(table(RUN)), runId, ownerUserId, briefVersionId, profileVersionId,
            hash('d'), hash('c'), ownerUserId, ownerUserId, ownerUserId);
    }

    private void insertCompletedRunWithNullDigest(Connection connection, long runId, long ownerUserId,
                                                  long briefVersionId, long profileVersionId) throws SQLException {
        update(connection, """
            INSERT INTO `%s` (
                agent_run_id, owner_user_id, delivery_brief_version_id, acceptance_profile_version_id,
                contract_revision, schema_version, idempotency_key, request_digest, run_status,
                candidate_asset_id, result_summary_json, finished_at,
                actor_type, actor_id, create_by, update_by
            ) VALUES (?, ?, ?, ?, 1, 'agent-run-1', 'invalid-null-result-digest', ?, 'completed',
                      801, JSON_OBJECT('assetId', 801), UTC_TIMESTAMP(6),
                      'app_user', ?, ?, ?)
            """.formatted(table(RUN)), runId, ownerUserId, briefVersionId, profileVersionId,
            hash('b'), ownerUserId, ownerUserId, ownerUserId);
    }

    private int update(Connection connection, String sql, Object... values) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < values.length; index++) {
                statement.setObject(index + 1, values[index]);
            }
            return statement.executeUpdate();
        }
    }

    private long rowCount(Connection connection, String table) throws SQLException {
        assertSafeTable(table);
        try (PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM `" + table + "`")) {
            return singleLong(statement);
        }
    }

    private long foreignKeyCount(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT COUNT(*) FROM information_schema.key_column_usage
            WHERE constraint_schema = DATABASE()
              AND table_name IN (?, ?, ?)
              AND referenced_table_name IS NOT NULL
            """)) {
            bindTables(statement);
            return singleLong(statement);
        }
    }

    private long checkConstraintCount(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT COUNT(*) FROM information_schema.table_constraints
            WHERE constraint_schema = DATABASE()
              AND table_name IN (?, ?, ?)
              AND constraint_type = 'CHECK'
            """)) {
            bindTables(statement);
            return singleLong(statement);
        }
    }

    private long temporalPrecision(Connection connection, String column) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT datetime_precision
            FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?
            """)) {
            statement.setString(1, table(RUN));
            statement.setString(2, column);
            return singleLong(statement);
        }
    }

    private void bindTables(PreparedStatement statement) throws SQLException {
        statement.setString(1, table(BRIEF));
        statement.setString(2, table(PROFILE));
        statement.setString(3, table(RUN));
    }

    private String databaseName(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT DATABASE()");
             ResultSet result = statement.executeQuery()) {
            assertThat(result.next()).isTrue();
            return result.getString(1);
        }
    }

    private long singleLong(PreparedStatement statement) throws SQLException {
        try (ResultSet result = statement.executeQuery()) {
            assertThat(result.next()).isTrue();
            return result.getLong(1);
        }
    }

    private AnnotationConfigApplicationContext openServiceContext() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("agent-run-persistence-it",
            Map.of(
                "agent-run.jdbc-url", ENV.jdbcUrl(),
                "agent-run.username", ENV.mysqlUsername(),
                "agent-run.password", ENV.mysqlPassword()
            )));
        context.getBeanFactory().registerSingleton("agentRunTestTableNames",
            new AgentRunTestTableNames(Map.of(
                SOURCE_TABLES.get(BRIEF), table(BRIEF),
                SOURCE_TABLES.get(PROFILE), table(PROFILE),
                SOURCE_TABLES.get(RUN), table(RUN),
                SOURCE_TABLES.get(AI_TASK), table(AI_TASK),
                SOURCE_TABLES.get(DH_JOB), table(DH_JOB),
                SOURCE_TABLES.get(CREATION_ASSET), table(CREATION_ASSET)
            )));
        context.register(AgentRunPersistenceConfiguration.class);
        context.refresh();
        return context;
    }

    private AppPrincipalSnapshotDTO principal(long ownerUserId) {
        return new AppPrincipalSnapshotDTO(ownerUserId, "agent-it-user", "agent-it-client",
            1L, 1L, 1L, 1L, null);
    }

    private String minimalAiTaskDdl() {
        return """
            CREATE TABLE `%s` (
                task_id BIGINT NOT NULL,
                owner_user_id BIGINT NOT NULL,
                task_type VARCHAR(64) NOT NULL,
                task_status VARCHAR(16) NOT NULL,
                result_asset_id BIGINT NULL,
                PRIMARY KEY (task_id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """.formatted(table(AI_TASK));
    }

    private String minimalDigitalHumanJobDdl() {
        return """
            CREATE TABLE `%s` (
                id BIGINT NOT NULL,
                owner_user_id BIGINT NOT NULL,
                job_type VARCHAR(32) NOT NULL,
                status VARCHAR(16) NOT NULL,
                PRIMARY KEY (id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """.formatted(table(DH_JOB));
    }

    private String minimalCreationAssetDdl() {
        return """
            CREATE TABLE `%s` (
                asset_id BIGINT NOT NULL,
                owner_user_id BIGINT NOT NULL,
                asset_type VARCHAR(16) NOT NULL,
                usage_origin VARCHAR(32) NOT NULL,
                source_ref_id BIGINT NULL,
                asset_status VARCHAR(16) NOT NULL,
                del_flag CHAR(1) NOT NULL,
                PRIMARY KEY (asset_id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """.formatted(table(CREATION_ASSET));
    }

    private String extractAndRewriteDdl(String migration, String sourceTable, String targetTable) {
        Pattern ddlPattern = Pattern.compile("(?s)CREATE TABLE IF NOT EXISTS " + Pattern.quote(sourceTable)
            + " \\(.*?\\) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT=.*?;");
        Matcher ddlMatcher = ddlPattern.matcher(migration);
        if (!ddlMatcher.find()) {
            throw new IllegalStateException("Frozen migration does not contain DDL for " + sourceTable);
        }
        assertSafeTable(targetTable);
        String rewritten = ddlMatcher.group().replace(sourceTable, targetTable);
        Matcher constraintMatcher = CHECK_NAME.matcher(rewritten);
        StringBuffer result = new StringBuffer();
        int constraintIndex = 0;
        while (constraintMatcher.find()) {
            String replacement = "CONSTRAINT " + targetTable + "_ck_" + (++constraintIndex) + " CHECK";
            constraintMatcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        constraintMatcher.appendTail(result);
        return result.toString();
    }

    private String table(String key) {
        String table = tables.get(key);
        if (table == null) {
            throw new IllegalStateException("Missing temporary table: " + key);
        }
        assertSafeTable(table);
        return table;
    }

    private void assertSafeTable(String table) {
        if (!SAFE_TABLE.matcher(table).matches()) {
            throw new IllegalStateException("Unsafe temporary table name: " + table);
        }
    }

    private Path findRepositoryRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("docs/sql/videoops-agent/mysql/100_agent_run_schema.sql"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Unable to locate VideoOps Agent repository root");
    }

    private static String hash(char character) {
        return String.valueOf(character).repeat(64);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}

record AgentRunTestTableNames(Map<String, String> values) {
}

@Configuration(proxyBeanMethods = false)
@MapperScan(basePackages = "org.dromara.aivideo.agent.mapper")
class AgentRunPersistenceConfiguration {

    @Bean
    DataSource dataSource(@Value("${agent-run.jdbc-url}") String jdbcUrl,
                          @Value("${agent-run.username}") String username,
                          @Value("${agent-run.password}") String password) {
        return new org.apache.ibatis.datasource.unpooled.UnpooledDataSource(
            "com.mysql.cj.jdbc.Driver", jdbcUrl, username, password);
    }

    @Bean
    SqlSessionFactory sqlSessionFactory(DataSource dataSource, AgentRunTestTableNames tableNames) throws Exception {
        MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        GlobalConfig globalConfig = new GlobalConfig();
        globalConfig.setMetaObjectHandler(new InjectionMetaObjectHandler());
        factoryBean.setGlobalConfig(globalConfig);

        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new DynamicTableNameInnerInterceptor(
            (sql, tableName) -> tableNames.values().getOrDefault(tableName, tableName)));
        factoryBean.setPlugins(interceptor);
        return factoryBean.getObject();
    }

    @Bean
    JsonMapper jsonMapper() {
        return JsonMapper.builder().build();
    }

    @Bean
    IAgentRunService agentRunService(DeliveryBriefVersionMapper briefMapper,
                                     AcceptanceProfileVersionMapper profileMapper,
                                     AgentRunMapper runMapper,
                                     JsonMapper jsonMapper) {
        return new AgentRunServiceImpl(briefMapper, profileMapper, runMapper, jsonMapper);
    }
}
