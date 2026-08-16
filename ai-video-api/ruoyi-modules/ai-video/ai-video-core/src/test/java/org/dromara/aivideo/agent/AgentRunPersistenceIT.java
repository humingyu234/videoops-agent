package org.dromara.aivideo.agent;

import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.DynamicTableNameInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.session.SqlSessionFactory;
import org.dromara.aivideo.agent.dto.AgentRunOrchestrationDTOs;
import org.dromara.aivideo.agent.dto.AgentToolDTOs;
import org.dromara.aivideo.agent.mapper.AcceptanceProfileVersionMapper;
import org.dromara.aivideo.agent.mapper.AgentRunMapper;
import org.dromara.aivideo.agent.mapper.AgentRunApprovalMapper;
import org.dromara.aivideo.agent.mapper.AgentRunEvaluationMapper;
import org.dromara.aivideo.agent.mapper.DeliveryBriefVersionMapper;
import org.dromara.aivideo.agent.service.IAgentRunOrchestrationService;
import org.dromara.aivideo.agent.service.IAgentRunService;
import org.dromara.aivideo.agent.service.IAgentToolService;
import org.dromara.aivideo.agent.service.impl.AgentRunOrchestrationServiceImpl;
import org.dromara.aivideo.agent.service.impl.AgentRunServiceImpl;
import org.dromara.aivideo.identity.dto.AppPrincipalSnapshotDTO;
import org.dromara.aivideo.identity.dto.AppWorkspaceSessionSnapshotDTO;
import org.dromara.aivideo.testsupport.LocalIntegrationEnvironment;
import org.dromara.aivideo.timeline.dto.TimelineOutputQualityDTO;
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
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
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
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Crosses the real MySQL boundary for the frozen T2 AgentRun persistence contract.
 *
 * <p>The production migration is never executed against {@code ai_video_test}. Its three
 * {@code CREATE TABLE} statements are rewritten to random names, while four minimal random fact
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
    private static final String CREATION_PROJECT = "creationProject";
    private static final String CREATION_ASSET = "creationAsset";
    private static final String EVALUATION = "evaluation";
    private static final String APPROVAL = "approval";
    private static final Pattern SAFE_TABLE = Pattern.compile("a(?:db|ap|run|at|dh|cp|ca|re|ra)_it_[a-f0-9]{32}");
    private static final Pattern CHECK_NAME = Pattern.compile("CONSTRAINT\\s+[A-Za-z0-9_]+\\s+CHECK");
    private static final List<String> QUALITY_CODES = List.of(
        "media.playable", "media.container_codec", "media.video_dimensions", "media.audio_present",
        "media.duration", "content.script_integrity", "content.must_include", "content.prohibited",
        "subtitle.text_integrity", "subtitle.safe_area", "subtitle.timing",
        "perceptual.identity_similarity", "perceptual.lip_sync", "perceptual.voice_consistency",
        "perceptual.visual_stability", "style.tone_match");
    private static final Map<String, String> SOURCE_TABLES = Map.of(
        BRIEF, "av_delivery_brief_version",
        PROFILE, "av_acceptance_profile_version",
        RUN, "av_agent_run",
        AI_TASK, "av_ai_task",
        DH_JOB, "av_dh_generation_job",
        CREATION_PROJECT, "av_creation_project",
        CREATION_ASSET, "av_creation_asset",
        EVALUATION, "av_agent_run_evaluation",
        APPROVAL, "av_agent_run_approval"
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
        tables.put(CREATION_PROJECT, "acp_it_" + suffix);
        tables.put(CREATION_ASSET, "aca_it_" + suffix);
        tables.put(EVALUATION, "are_it_" + suffix);
        tables.put(APPROVAL, "ara_it_" + suffix);

        String migration = Files.readString(findRepositoryRoot().resolve(
            "docs/sql/videoops-agent/mysql/100_agent_run_schema.sql"), StandardCharsets.UTF_8);
        String qualityMigration = Files.readString(findRepositoryRoot().resolve(
            "docs/sql/videoops-agent/mysql/120_agent_run_quality_control.sql"), StandardCharsets.UTF_8);
        try (Connection connection = ENV.openMySqlConnection(); Statement statement = connection.createStatement()) {
            for (String key : List.of(BRIEF, PROFILE, RUN)) {
                String ddl = extractAndRewriteDdl(migration, SOURCE_TABLES.get(key), table(key));
                statement.execute(RUN.equals(key) ? applyOrchestrationStateToCreateDdl(ddl) : ddl);
            }
            applyQualityControlState(statement);
            statement.execute(minimalAiTaskDdl());
            statement.execute(minimalDigitalHumanJobDdl());
            statement.execute(minimalCreationProjectDdl());
            statement.execute(minimalCreationAssetDdl());
            statement.execute(extractAndRewriteDdl(qualityMigration, SOURCE_TABLES.get(EVALUATION),
                table(EVALUATION)));
            statement.execute(extractAndRewriteDdl(qualityMigration, SOURCE_TABLES.get(APPROVAL), table(APPROVAL)));
        }
    }

    @AfterEach
    void dropFrozenContractTables() throws SQLException {
        if (tables.isEmpty()) {
            return;
        }
        try (Connection connection = ENV.openMySqlConnection(); Statement statement = connection.createStatement()) {
            for (String key : List.of(APPROVAL, EVALUATION, RUN, CREATION_ASSET, AI_TASK, CREATION_PROJECT,
                DH_JOB, PROFILE, BRIEF)) {
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
            assertThat(checkConstraintCount(connection)).isEqualTo(26L);
            assertThat(columnCount(connection, "retry_count")).isEqualTo(1L);
            assertThat(columnCount(connection, "quality_repair_count")).isEqualTo(1L);
            assertThat(columnCount(connection, "pending_approval_id")).isEqualTo(1L);
            assertThat(columnCount(connection, "approval_revision")).isEqualTo(1L);
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
                updateDigitalHumanJob(connection, 900L, firstOwner.appUserId(), "unsupported_generate", "running");
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
            assertThatThrownBy(() -> insertConditionalApprovalWithNullEvaluation(connection, 128L, 301L))
                .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> insertApprovedApprovalWithNullDecider(connection, 129L, 301L))
                .isInstanceOf(SQLException.class);
            assertThat(rowCount(connection, table(BRIEF))).isEqualTo(3L);
            assertThat(rowCount(connection, table(PROFILE))).isEqualTo(3L);
            assertThat(rowCount(connection, table(RUN))).isEqualTo(2L);
            assertThat(rowCount(connection, table(APPROVAL))).isZero();
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

    @Test
    void freshContextsCompleteTheSameVoiceVideoRenderChainWithoutDuplicateAcceptedSubmissions() throws Exception {
        long ownerUserId = 601L;
        long voiceJobId = 2101L;
        long videoJobId = 2102L;
        long projectId = 2103L;
        long renderTaskId = 2104L;
        long outputAssetId = 2105L;
        AppPrincipalSnapshotDTO principal = orchestrationPrincipal(ownerUserId);
        CountingGoldenToolService tools = new CountingGoldenToolService(
            voiceJobId, videoJobId, projectId, renderTaskId, outputAssetId);

        try (Connection connection = ENV.openMySqlConnection()) {
            insertDigitalHumanJob(connection, voiceJobId, ownerUserId,
                "voice_generate", "running", null);
            insertDigitalHumanJob(connection, videoJobId, ownerUserId,
                "video_generate", "queued", voiceJobId);
            insertCreationProject(connection, projectId, ownerUserId, videoJobId);
            insertAiTask(connection, renderTaskId, ownerUserId, "timeline_render", "running", null,
                "creation_project", projectId);
        }

        long agentRunId;
        try (AnnotationConfigApplicationContext context = openServiceContext(tools)) {
            IAgentRunService runService = context.getBean(IAgentRunService.class);
            IAgentRunOrchestrationService orchestration = context.getBean(IAgentRunOrchestrationService.class);
            var run = createGoldenRun(runService, principal, "golden-chain");
            agentRunId = run.agentRunId();

            var initialApproval = orchestration.advance(principal, advance(run, "agent-it-golden-approval"));
            assertThat(initialApproval.runStatus()).isEqualTo("waiting_approval");
            var pendingInitial = runService.getOwnedRun(principal, agentRunId);
            orchestration.decideApproval(principal, new AgentRunOrchestrationDTOs.ApprovalCommand(
                agentRunId, pendingInitial.rowVersion(), pendingInitial.contractRevision(),
                initialApproval.pendingApprovalId(), initialApproval.approvalRevision(), "initial", true));
            var approved = runService.getOwnedRun(principal, agentRunId);
            var waiting = orchestration.advance(principal, advance(approved, "agent-it-golden-a"));
            assertThat(waiting.runStatus()).isEqualTo("waiting_external_task");
            assertThat(waiting.waitingTaskSource()).isEqualTo("digital_human_generation");
            assertThat(waiting.waitingTaskId()).isEqualTo(voiceJobId);
        }
        assertThat(tools.acceptedSubmissions("submit_voice_generation")).isEqualTo(1);
        assertThat(tools.acceptedSubmissions("submit_digital_human_video")).isZero();
        assertThat(tools.acceptedSubmissions("render_timeline")).isZero();

        try (Connection connection = ENV.openMySqlConnection()) {
            assertThat(makeWaitingDue(connection, ownerUserId, agentRunId,
                "digital_human_generation", voiceJobId)).isEqualTo(1);
            updateDigitalHumanJob(connection, voiceJobId, ownerUserId,
                "voice_generate", "succeeded");
        }
        tools.completeVoice();

        try (AnnotationConfigApplicationContext context = openServiceContext(tools)) {
            IAgentRunService runService = context.getBean(IAgentRunService.class);
            IAgentRunOrchestrationService orchestration = context.getBean(IAgentRunOrchestrationService.class);
            var run = runService.getOwnedRun(principal, agentRunId);
            var waiting = orchestration.advance(principal, advance(run, "agent-it-golden-b"));
            assertThat(waiting.waitingTaskSource()).isEqualTo("digital_human_generation");
            assertThat(waiting.waitingTaskId()).isEqualTo(videoJobId);
        }
        assertThat(tools.submitInvocations("submit_voice_generation")).isEqualTo(1);
        assertThat(tools.acceptedSubmissions("submit_digital_human_video")).isEqualTo(1);

        try (Connection connection = ENV.openMySqlConnection()) {
            assertThat(makeWaitingDue(connection, ownerUserId, agentRunId,
                "digital_human_generation", videoJobId)).isEqualTo(1);
            updateDigitalHumanJob(connection, videoJobId, ownerUserId,
                "video_generate", "succeeded");
        }
        tools.completeVideo();

        try (AnnotationConfigApplicationContext context = openServiceContext(tools)) {
            IAgentRunService runService = context.getBean(IAgentRunService.class);
            IAgentRunOrchestrationService orchestration = context.getBean(IAgentRunOrchestrationService.class);
            var run = runService.getOwnedRun(principal, agentRunId);
            var waiting = orchestration.advance(principal, advance(run, "agent-it-golden-c"));
            assertThat(waiting.waitingTaskSource()).isEqualTo("ai_task");
            assertThat(waiting.waitingTaskId()).isEqualTo(renderTaskId);
        }
        assertThat(tools.submitInvocations("submit_digital_human_video")).isEqualTo(1);
        assertThat(tools.acceptedSubmissions("render_timeline")).isEqualTo(1);

        try (Connection connection = ENV.openMySqlConnection()) {
            assertThat(makeWaitingDue(connection, ownerUserId, agentRunId,
                "ai_task", renderTaskId)).isEqualTo(1);
            updateAiTask(connection, renderTaskId, ownerUserId,
                "timeline_render", "success", outputAssetId);
            insertCreationAsset(connection, outputAssetId, ownerUserId,
                "timeline_render_output", renderTaskId, "ready");
        }
        tools.completeRender();

        try (AnnotationConfigApplicationContext context = openServiceContext(tools)) {
            IAgentRunService runService = context.getBean(IAgentRunService.class);
            IAgentRunOrchestrationService orchestration = context.getBean(IAgentRunOrchestrationService.class);
            var run = runService.getOwnedRun(principal, agentRunId);
            var finalApproval = orchestration.advance(principal, advance(run, "agent-it-golden-d"));
            assertThat(finalApproval.runStatus()).isEqualTo("waiting_approval");
            var pendingFinal = runService.getOwnedRun(principal, agentRunId);
            var completed = orchestration.decideApproval(principal,
                new AgentRunOrchestrationDTOs.ApprovalCommand(agentRunId, pendingFinal.rowVersion(),
                    pendingFinal.contractRevision(), finalApproval.pendingApprovalId(),
                    finalApproval.approvalRevision(), "final", true));
            assertThat(completed.runStatus()).isEqualTo("completed");
            assertThat(completed.candidateAssetId()).isEqualTo(outputAssetId);

            var persisted = runService.getOwnedRun(principal, agentRunId);
            assertThat(persisted.runStatus()).isEqualTo("completed");
            assertThat(persisted.waitingTaskSource()).isNull();
            assertThat(persisted.waitingTaskId()).isNull();
            assertThat(persisted.candidateAssetId()).isEqualTo(outputAssetId);
        }
        assertThat(tools.submitInvocations("render_timeline")).isEqualTo(1);
        assertThat(tools.acceptedSubmissions("submit_voice_generation")).isEqualTo(1);
        assertThat(tools.acceptedSubmissions("submit_digital_human_video")).isEqualTo(1);
        assertThat(tools.acceptedSubmissions("render_timeline")).isEqualTo(1);
        try (Connection connection = ENV.openMySqlConnection()) {
            assertThat(rowCount(connection, table(RUN))).isEqualTo(1L);
        }
    }

    @Test
    void freshContextReplaysOneAcceptedQualityRepairAndKeepsApprovalFencesExact() throws Exception {
        long ownerUserId = 651L;
        long sourceVoiceJobId = 2500L;
        long sourceVideoJobId = 2501L;
        long firstProjectId = 2502L;
        long firstRenderTaskId = 2503L;
        long firstOutputAssetId = 2504L;
        long repairProjectId = 2505L;
        long repairRenderTaskId = 2506L;
        long repairOutputAssetId = 2507L;
        AppPrincipalSnapshotDTO principal = orchestrationPrincipal(ownerUserId);
        AppPrincipalSnapshotDTO otherOwner = orchestrationPrincipal(ownerUserId + 1L);

        try (Connection connection = ENV.openMySqlConnection()) {
            insertDigitalHumanJob(connection, sourceVoiceJobId, ownerUserId,
                "voice_generate", "succeeded", null);
            insertDigitalHumanJob(connection, sourceVideoJobId, ownerUserId,
                "video_generate", "succeeded", sourceVoiceJobId);
            insertCreationProject(connection, firstProjectId, ownerUserId, sourceVideoJobId);
            insertCreationProject(connection, repairProjectId, ownerUserId, sourceVideoJobId);
            insertAiTask(connection, firstRenderTaskId, ownerUserId, "timeline_render", "success",
                firstOutputAssetId, "creation_project", firstProjectId);
            insertAiTask(connection, repairRenderTaskId, ownerUserId, "timeline_render", "running",
                null, "creation_project", repairProjectId);
            insertCreationAsset(connection, firstOutputAssetId, ownerUserId,
                "timeline_render_output", firstRenderTaskId, "ready");
        }

        long agentRunId;
        try (AnnotationConfigApplicationContext context = openServiceContext()) {
            IAgentRunService service = context.getBean(IAgentRunService.class);
            var created = createGoldenRun(service, principal, "quality-repair-crash");
            agentRunId = created.agentRunId();
            var approval = service.requestInitialApproval(principal,
                new IAgentRunService.RequestInitialApprovalCommand(agentRunId, created.rowVersion(),
                    created.contractRevision(), "批准冻结合同"));
            var waitingApproval = service.getOwnedRun(principal, agentRunId);

            assertThatThrownBy(() -> service.decideApproval(otherOwner,
                new IAgentRunService.DecideApprovalCommand(agentRunId, waitingApproval.rowVersion(),
                    waitingApproval.contractRevision(), approval.approvalId(), approval.revision(),
                    "initial", "approved", "cross-owner")))
                .isInstanceOf(ServiceException.class);
            assertThatThrownBy(() -> service.decideApproval(principal,
                new IAgentRunService.DecideApprovalCommand(agentRunId, waitingApproval.rowVersion() - 1,
                    waitingApproval.contractRevision(), approval.approvalId(), approval.revision(),
                    "initial", "approved", "stale")))
                .isInstanceOf(ServiceException.class);
            assertThatThrownBy(() -> service.decideApproval(principal,
                new IAgentRunService.DecideApprovalCommand(agentRunId, waitingApproval.rowVersion(),
                    waitingApproval.contractRevision(), approval.approvalId(), approval.revision(),
                    "final", "approved", "wrong-type")))
                .isInstanceOf(ServiceException.class);

            service.decideApproval(principal, new IAgentRunService.DecideApprovalCommand(
                agentRunId, waitingApproval.rowVersion(), waitingApproval.contractRevision(),
                approval.approvalId(), approval.revision(), "initial", "approved", "批准"));
            var queued = service.getOwnedRun(principal, agentRunId);
            var lease = service.claim(principal, new IAgentRunService.ClaimAgentRunCommand(
                agentRunId, queued.rowVersion(), queued.contractRevision(), "agent-it-quality-a", 300));
            assertThat(lease).isNotNull();
            assertThat(service.waitForExternalTask(principal, new IAgentRunService.WaitForExternalTaskCommand(
                lease.proof(), "ai_task", firstRenderTaskId, Instant.now().plusSeconds(1)))).isNotNull();
        }
        try (Connection connection = ENV.openMySqlConnection()) {
            assertThat(makeWaitingDue(connection, ownerUserId, agentRunId,
                "ai_task", firstRenderTaskId)).isEqualTo(1);
        }

        CountingGoldenToolService tools = new CountingGoldenToolService(
            0L, sourceVideoJobId, firstProjectId, firstRenderTaskId, firstOutputAssetId,
            repairProjectId, repairRenderTaskId, repairOutputAssetId,
            quality(firstRenderTaskId, firstOutputAssetId, Set.of("subtitle.timing")),
            quality(repairRenderTaskId, repairOutputAssetId, Set.of("subtitle.timing")),
            () -> fenceAcceptedRepair(agentRunId, ownerUserId, firstRenderTaskId));
        tools.completeRender();

        try (AnnotationConfigApplicationContext context = openServiceContext(tools)) {
            IAgentRunService service = context.getBean(IAgentRunService.class);
            IAgentRunOrchestrationService orchestration = context.getBean(IAgentRunOrchestrationService.class);
            var run = service.getOwnedRun(principal, agentRunId);
            var conflicted = orchestration.advance(principal, advance(run, "agent-it-quality-b"));
            assertThat(conflicted.outcome()).isEqualTo("state_conflict");
        }

        try (AnnotationConfigApplicationContext context = openServiceContext(tools)) {
            IAgentRunService service = context.getBean(IAgentRunService.class);
            IAgentRunOrchestrationService orchestration = context.getBean(IAgentRunOrchestrationService.class);
            var run = service.getOwnedRun(principal, agentRunId);
            var waiting = orchestration.advance(principal, advance(run, "agent-it-quality-c"));
            assertThat(waiting.runStatus()).isEqualTo("waiting_external_task");
            assertThat(waiting.waitingTaskId()).isEqualTo(repairRenderTaskId);

            var persisted = service.getOwnedRun(principal, agentRunId);
            assertThat(persisted.qualityRepairCount()).isEqualTo(1L);
            assertThat(persisted.retryCount()).isZero();
            assertThat(service.getOwnedQualityEvaluation(principal, agentRunId, 0).renderTaskId())
                .isEqualTo(firstRenderTaskId);
        }

        try (Connection connection = ENV.openMySqlConnection()) {
            updateAiTask(connection, repairRenderTaskId, ownerUserId,
                "timeline_render", "success", repairOutputAssetId);
            insertCreationAsset(connection, repairOutputAssetId, ownerUserId,
                "timeline_render_output", repairRenderTaskId, "ready");
            assertThat(makeWaitingDue(connection, ownerUserId, agentRunId,
                "ai_task", repairRenderTaskId)).isEqualTo(1);
        }
        tools.completeRepairRender();

        try (AnnotationConfigApplicationContext context = openServiceContext(tools)) {
            IAgentRunService service = context.getBean(IAgentRunService.class);
            IAgentRunOrchestrationService orchestration = context.getBean(IAgentRunOrchestrationService.class);
            var run = service.getOwnedRun(principal, agentRunId);
            var approvalRequired = orchestration.advance(principal, advance(run, "agent-it-quality-d"));
            assertThat(approvalRequired.runStatus()).isEqualTo("waiting_approval");
            assertThat(approvalRequired.approvalType()).isEqualTo("conditional");
            assertThat(service.getOwnedQualityEvaluation(principal, agentRunId, 1).repairScope())
                .isEqualTo("timeline_render");
            var pending = service.getOwnedRun(principal, agentRunId);
            int callsBeforeDecisions = tools.totalInvocations();

            assertThatThrownBy(() -> service.decideApproval(otherOwner,
                new IAgentRunService.DecideApprovalCommand(agentRunId, pending.rowVersion(),
                    pending.contractRevision(), approvalRequired.pendingApprovalId(),
                    approvalRequired.approvalRevision(), "conditional", "approved", "cross-owner")))
                .isInstanceOf(ServiceException.class);
            assertThatThrownBy(() -> service.decideApproval(principal,
                new IAgentRunService.DecideApprovalCommand(agentRunId, pending.rowVersion(),
                    pending.contractRevision(), approvalRequired.pendingApprovalId(),
                    approvalRequired.approvalRevision(), "final", "approved", "wrong-type")))
                .isInstanceOf(ServiceException.class);
            assertThatThrownBy(() -> service.decideApproval(principal,
                new IAgentRunService.DecideApprovalCommand(agentRunId, pending.rowVersion() - 1,
                    pending.contractRevision(), approvalRequired.pendingApprovalId(),
                    approvalRequired.approvalRevision(), "conditional", "approved", "stale")))
                .isInstanceOf(ServiceException.class);

            var blocked = orchestration.decideApproval(principal,
                new AgentRunOrchestrationDTOs.ApprovalCommand(agentRunId, pending.rowVersion(),
                    pending.contractRevision(), approvalRequired.pendingApprovalId(),
                    approvalRequired.approvalRevision(), "conditional", true));
            assertThat(blocked.runStatus()).isEqualTo("waiting_input");
            assertThat(service.getOwnedRun(principal, agentRunId).errorCode())
                .isEqualTo("APPROVAL_INPUT_REQUIRED");
            assertThat(tools.totalInvocations()).isEqualTo(callsBeforeDecisions);
        }
        assertThat(tools.acceptedSubmissions("prepare_timeline_project")).isEqualTo(1);
        assertThat(tools.acceptedSubmissions("render_timeline")).isEqualTo(1);
        assertThat(tools.submitInvocations("prepare_timeline_project")).isEqualTo(2);
        assertThat(tools.submitInvocations("render_timeline")).isEqualTo(2);
        assertThat(tools.acceptedSubmissions("submit_voice_generation")).isZero();
        assertThat(tools.acceptedSubmissions("submit_digital_human_video")).isZero();
        try (Connection connection = ENV.openMySqlConnection()) {
            assertThat(rowCount(connection, table(EVALUATION))).isEqualTo(2L);
            assertThat(rowCount(connection, table(APPROVAL))).isEqualTo(2L);
            assertThat(rowCount(connection, table(AI_TASK))).isEqualTo(2L);
            assertThat(rowCount(connection, table(DH_JOB))).isEqualTo(2L);
            try (PreparedStatement statement = connection.prepareStatement("""
                SELECT COUNT(*) FROM `%s`
                WHERE owner_user_id = ?
                  AND ((id = ? AND job_type = 'voice_generate' AND parent_job_id IS NULL)
                    OR (id = ? AND job_type = 'video_generate' AND parent_job_id = ?))
                """.formatted(table(DH_JOB)))) {
                statement.setLong(1, ownerUserId);
                statement.setLong(2, sourceVoiceJobId);
                statement.setLong(3, sourceVideoJobId);
                statement.setLong(4, sourceVoiceJobId);
                assertThat(singleLong(statement)).isEqualTo(2L);
            }
        }
    }

    @Test
    void ownerCancellationSurvivesLateSuccessAndCrossOwnerCannotCancel() throws Exception {
        long ownerUserId = 701L;
        long voiceJobId = 3101L;
        AppPrincipalSnapshotDTO principal = orchestrationPrincipal(ownerUserId);
        AppPrincipalSnapshotDTO otherOwner = orchestrationPrincipal(ownerUserId + 1L);
        CountingGoldenToolService tools = new CountingGoldenToolService(
            voiceJobId, 3102L, 3103L, 3104L, 3105L);

        try (Connection connection = ENV.openMySqlConnection()) {
            insertDigitalHumanJob(connection, voiceJobId, ownerUserId,
                "voice_generate", "running", null);
        }

        long agentRunId;
        long waitingRowVersion;
        try (AnnotationConfigApplicationContext context = openServiceContext(tools)) {
            IAgentRunService runService = context.getBean(IAgentRunService.class);
            IAgentRunOrchestrationService orchestration = context.getBean(IAgentRunOrchestrationService.class);
            var created = createGoldenRun(runService, principal, "cancel-chain");
            agentRunId = created.agentRunId();
            var initialApproval = orchestration.advance(principal, advance(created, "agent-it-cancel-approval"));
            var pendingInitial = runService.getOwnedRun(principal, agentRunId);
            orchestration.decideApproval(principal, new AgentRunOrchestrationDTOs.ApprovalCommand(
                agentRunId, pendingInitial.rowVersion(), pendingInitial.contractRevision(),
                initialApproval.pendingApprovalId(), initialApproval.approvalRevision(), "initial", true));
            var approved = runService.getOwnedRun(principal, agentRunId);
            var waiting = orchestration.advance(principal, advance(approved, "agent-it-cancel"));
            assertThat(waiting.waitingTaskId()).isEqualTo(voiceJobId);

            var persistedWaiting = runService.getOwnedRun(principal, agentRunId);
            waitingRowVersion = persistedWaiting.rowVersion();
            int callsBeforeCrossOwner = tools.totalInvocations();
            assertThatThrownBy(() -> orchestration.cancel(otherOwner,
                new AgentRunOrchestrationDTOs.CancelCommand(agentRunId, persistedWaiting.rowVersion(),
                    persistedWaiting.contractRevision())))
                .isInstanceOf(ServiceException.class);
            assertThat(tools.totalInvocations()).isEqualTo(callsBeforeCrossOwner);
            assertThat(runService.getOwnedRun(principal, agentRunId).runStatus())
                .isEqualTo("waiting_external_task");

            var cancelled = orchestration.cancel(principal,
                new AgentRunOrchestrationDTOs.CancelCommand(agentRunId, persistedWaiting.rowVersion(),
                    persistedWaiting.contractRevision()));
            assertThat(cancelled.runStatus()).isEqualTo("cancelled");
            assertThat(cancelled.outcome()).isEqualTo("cancelled");
        }

        int callsAtCancellation = tools.totalInvocations();
        try (Connection connection = ENV.openMySqlConnection()) {
            updateDigitalHumanJob(connection, voiceJobId, ownerUserId,
                "voice_generate", "succeeded");
        }
        tools.completeVoice();

        try (AnnotationConfigApplicationContext context = openServiceContext(tools)) {
            IAgentRunService runService = context.getBean(IAgentRunService.class);
            IAgentRunOrchestrationService orchestration = context.getBean(IAgentRunOrchestrationService.class);
            var persisted = runService.getOwnedRun(principal, agentRunId);
            assertThat(persisted.rowVersion()).isEqualTo(waitingRowVersion + 1L);
            var lateAdvance = orchestration.advance(principal, advance(persisted, "agent-it-late"));
            assertThat(lateAdvance.runStatus()).isEqualTo("cancelled");
            assertThat(lateAdvance.outcome()).isEqualTo("terminal");
            assertThat(tools.totalInvocations()).isEqualTo(callsAtCancellation);

            var unchanged = runService.getOwnedRun(principal, agentRunId);
            assertThat(unchanged.rowVersion()).isEqualTo(persisted.rowVersion());
            assertThat(unchanged.waitingTaskSource()).isNull();
            assertThat(unchanged.waitingTaskId()).isNull();
            assertThat(unchanged.candidateAssetId()).isNull();
            assertThat(unchanged.errorCode()).isEqualTo("AGENT_RUN_CANCELLED");
        }
        assertThat(tools.acceptedSubmissions("submit_voice_generation")).isEqualTo(1);
        assertThat(tools.acceptedSubmissions("submit_digital_human_video")).isZero();
        assertThat(tools.acceptedSubmissions("render_timeline")).isZero();
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

    private int makeWaitingDue(Connection connection, long ownerUserId, long runId,
                               String taskSource, long taskId) throws SQLException {
        return update(connection, """
            UPDATE `%s`
            SET lease_expires_at = DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 1 SECOND),
                resume_after = DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 1 SECOND)
            WHERE owner_user_id = ? AND agent_run_id = ? AND run_status = 'waiting_external_task'
              AND waiting_contract_revision = contract_revision
              AND waiting_task_source = ? AND waiting_task_id = ?
              AND lease_token_digest IS NOT NULL
            """.formatted(table(RUN)), ownerUserId, runId, taskSource, taskId);
    }

    private void fenceAcceptedRepair(long runId, long ownerUserId, long waitingTaskId) {
        try (Connection connection = ENV.openMySqlConnection()) {
            int updated = update(connection, """
                UPDATE `%s`
                SET row_version = row_version + 1,
                    lease_expires_at = DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 1 SECOND),
                    resume_after = DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 1 SECOND)
                WHERE owner_user_id = ? AND agent_run_id = ? AND run_status = 'waiting_external_task'
                  AND waiting_task_source = 'ai_task' AND waiting_task_id = ?
                  AND lease_token_digest IS NOT NULL
                """.formatted(table(RUN)), ownerUserId, runId, waitingTaskId);
            if (updated != 1) {
                throw new IllegalStateException("expected one fenced waiting run");
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("unable to fence accepted repair", exception);
        }
    }

    private void insertAiTask(Connection connection, long taskId, long ownerUserId, String taskType,
                              String taskStatus, Long resultAssetId) throws SQLException {
        insertAiTask(connection, taskId, ownerUserId, taskType, taskStatus, resultAssetId, null, null);
    }

    private void insertAiTask(Connection connection, long taskId, long ownerUserId, String taskType,
                              String taskStatus, Long resultAssetId, String resourceType,
                              Long resourceId) throws SQLException {
        update(connection, """
            INSERT INTO `%s` (
                task_id, owner_user_id, task_type, task_status, result_asset_id, resource_type, resource_id
            ) VALUES (?, ?, ?, ?, ?, ?, ?)
            """.formatted(table(AI_TASK)), taskId, ownerUserId, taskType, taskStatus, resultAssetId,
            resourceType, resourceId);
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
        insertDigitalHumanJob(connection, jobId, ownerUserId, jobType, status, null);
    }

    private void insertDigitalHumanJob(Connection connection, long jobId, long ownerUserId,
                                       String jobType, String status, Long parentJobId) throws SQLException {
        update(connection, """
            INSERT INTO `%s` (
                id, tenant_id, owner_user_id, job_type, status, stage, progress, parent_job_id,
                idempotency_key, input_hash, input_media_key, provider
            ) VALUES (?, 1, ?, ?, ?, 'provider_processing', 30, ?, ?, REPEAT('a', 64), ?, ?)
            """.formatted(table(DH_JOB)), jobId, ownerUserId, jobType, status, parentJobId,
            "fixture-" + jobId, jobId + "/input/fixture.bin",
            "voice_generate".equals(jobType) ? "indextts2" : "comfyui");
    }

    private void updateDigitalHumanJob(Connection connection, long jobId, long ownerUserId,
                                       String jobType, String status) throws SQLException {
        update(connection, """
            UPDATE `%s`
            SET owner_user_id = ?, job_type = ?, status = ?
            WHERE id = ?
            """.formatted(table(DH_JOB)), ownerUserId, jobType, status, jobId);
    }

    int expireRunningLease(long ownerUserId, IAgentRunService.AgentRunLease lease) throws SQLException {
        try (Connection connection = ENV.openMySqlConnection()) {
            return update(connection, """
                UPDATE `%s`
                SET lease_expires_at = DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 1 SECOND)
                WHERE owner_user_id = ? AND agent_run_id = ? AND run_status = 'running'
                  AND row_version = ? AND lease_generation = ? AND lease_token_digest = ?
                  AND waiting_task_source IS NULL AND waiting_task_id IS NULL
                """.formatted(table(RUN)), ownerUserId, lease.agentRunId(), lease.rowVersion(),
                lease.leaseGeneration(), sha256(lease.leaseToken()));
        }
    }

    long agentRunRowCount() throws SQLException {
        return fixtureRowCount(RUN);
    }

    long digitalHumanJobRowCount() throws SQLException {
        return fixtureRowCount(DH_JOB);
    }

    private long fixtureRowCount(String key) throws SQLException {
        try (Connection connection = ENV.openMySqlConnection()) {
            return rowCount(connection, table(key));
        }
    }

    private void insertCreationProject(Connection connection, long projectId, long ownerUserId,
                                       long sourceJobId) throws SQLException {
        update(connection, """
            INSERT INTO `%s` (project_id, owner_user_id, source_type, source_ref_id, del_flag)
            VALUES (?, ?, 'digital_human_job', ?, '0')
            """.formatted(table(CREATION_PROJECT)), projectId, ownerUserId, sourceJobId);
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
                asset_id, owner_user_id, asset_type, usage_origin, source_ref_id, asset_status, sha256, del_flag
            ) VALUES (?, ?, 'video', ?, ?, ?, ?, ?)
            """.formatted(table(CREATION_ASSET)),
            assetId, ownerUserId, usageOrigin, sourceRefId, assetStatus, "a".repeat(64), delFlag);
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

    private void insertConditionalApprovalWithNullEvaluation(Connection connection, long approvalId,
                                                              long ownerUserId) throws SQLException {
        update(connection, """
            INSERT INTO `%s` (
                approval_id, agent_run_id, owner_user_id, evaluation_id, approval_type, approval_status,
                subject_digest, revision, request_summary, actor_type, actor_id, create_by, update_by
            ) VALUES (?, 901, ?, NULL, 'conditional', 'pending', ?, 1, 'invalid null evaluation',
                      'app_user', ?, ?, ?)
            """.formatted(table(APPROVAL)), approvalId, ownerUserId, hash('9'),
            ownerUserId, ownerUserId, ownerUserId);
    }

    private void insertApprovedApprovalWithNullDecider(Connection connection, long approvalId,
                                                       long ownerUserId) throws SQLException {
        update(connection, """
            INSERT INTO `%s` (
                approval_id, agent_run_id, owner_user_id, evaluation_id, approval_type, approval_status,
                subject_digest, revision, request_summary, decision_summary, decided_by, decided_at,
                actor_type, actor_id, create_by, update_by
            ) VALUES (?, 902, ?, NULL, 'initial', 'approved', ?, 1, 'invalid null decider',
                      'approved without actor', NULL, UTC_TIMESTAMP(6), 'app_user', ?, ?, ?)
            """.formatted(table(APPROVAL)), approvalId, ownerUserId, hash('8'),
            ownerUserId, ownerUserId, ownerUserId);
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

    private long columnCount(Connection connection, String column) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT COUNT(*)
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

    AnnotationConfigApplicationContext openServiceContext() {
        return openServiceContext(null);
    }

    AnnotationConfigApplicationContext openServiceContext(IAgentToolService toolService) {
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
                SOURCE_TABLES.get(CREATION_PROJECT), table(CREATION_PROJECT),
                SOURCE_TABLES.get(CREATION_ASSET), table(CREATION_ASSET),
                SOURCE_TABLES.get(EVALUATION), table(EVALUATION),
                SOURCE_TABLES.get(APPROVAL), table(APPROVAL)
            )));
        context.register(AgentRunPersistenceConfiguration.class);
        if (toolService != null) {
            context.getBeanFactory().registerSingleton("agentRunPersistenceToolService", toolService);
            context.register(AgentRunOrchestrationPersistenceConfiguration.class);
        }
        context.refresh();
        return context;
    }

    IAgentRunService.AgentRunView createGoldenRun(IAgentRunService service,
                                                   AppPrincipalSnapshotDTO principal,
                                                   String keyPrefix) {
        var brief = service.appendDeliveryBrief(principal, new IAgentRunService.AppendDeliveryBriefCommand(
            null, null, keyPrefix + "-brief", """
            {"startAt":"new","scriptText":"T4 persistence chain","referenceVoiceId":"501",
             "portraitId":"601","projectTitle":"T4 golden chain"}
            """));
        var profile = service.appendAcceptanceProfile(principal,
            new IAgentRunService.AppendAcceptanceProfileCommand(null, null, brief.deliveryBriefVersionId(),
                keyPrefix + "-profile", """
                {"maxRunSeconds":3600,"maxResumeAttempts":20,"maxProviderSubmissions":2,
                 "maxRenderRetries":0,"pollIntervalSeconds":1}
                """));
        return service.createRun(principal, new IAgentRunService.CreateAgentRunCommand(
            brief.deliveryBriefVersionId(), profile.acceptanceProfileVersionId(), keyPrefix + "-run"));
    }

    AgentRunOrchestrationDTOs.AdvanceCommand advance(IAgentRunService.AgentRunView run,
                                                      String workerId) {
        return new AgentRunOrchestrationDTOs.AdvanceCommand(run.agentRunId(), run.rowVersion(),
            run.contractRevision(), workerId);
    }

    private AppPrincipalSnapshotDTO principal(long ownerUserId) {
        return new AppPrincipalSnapshotDTO(ownerUserId, "agent-it-user", "agent-it-client",
            1L, 1L, 1L, 1L, null);
    }

    AppPrincipalSnapshotDTO orchestrationPrincipal(long ownerUserId) {
        Set<String> permissions = Set.of(
            "aivideo:studio:generate",
            "aivideo:studio:query",
            "aivideo:voice:query",
            "aivideo:portrait:query",
            "aivideo:creation:edit",
            "aivideo:creation:generate",
            "aivideo:task:query",
            "aivideo:creation-asset:query"
        );
        var workspace = new AppWorkspaceSessionSnapshotDTO("personal-" + ownerUserId, "personal", 1L,
            "app_user", ownerUserId, "app_user", ownerUserId, "creator", permissions, 1L, null);
        return new AppPrincipalSnapshotDTO(ownerUserId, "agent-it-user", "agent-it-client",
            1L, 1L, 1L, 1L, workspace);
    }

    private String minimalAiTaskDdl() {
        return """
            CREATE TABLE `%s` (
                task_id BIGINT NOT NULL,
                owner_user_id BIGINT NOT NULL,
                task_type VARCHAR(64) NOT NULL,
                task_status VARCHAR(16) NOT NULL,
                result_asset_id BIGINT NULL,
                resource_type VARCHAR(32) NULL,
                resource_id BIGINT NULL,
                input_version_id BIGINT NULL,
                PRIMARY KEY (task_id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """.formatted(table(AI_TASK));
    }

    private String minimalDigitalHumanJobDdl() {
        return """
            CREATE TABLE `%s` (
                id BIGINT NOT NULL,
                tenant_id BIGINT NOT NULL,
                owner_user_id BIGINT NOT NULL,
                job_type VARCHAR(32) NOT NULL,
                status VARCHAR(16) NOT NULL,
                stage VARCHAR(40) NOT NULL,
                progress INT NOT NULL DEFAULT 0,
                parent_job_id BIGINT NULL,
                idempotency_key VARCHAR(64) NOT NULL,
                input_hash CHAR(64) NOT NULL,
                script_text VARCHAR(1000) NULL,
                input_media_key VARCHAR(500) NOT NULL,
                output_media_key VARCHAR(500) NULL,
                output_media_type VARCHAR(100) NULL,
                output_media_size BIGINT NULL,
                output_media_sha256 CHAR(64) NULL,
                provider VARCHAR(32) NOT NULL,
                provider_job_id VARCHAR(128) NULL,
                poll_token VARCHAR(64) NULL,
                poll_lease_until DATETIME NULL,
                poll_error_count INT NOT NULL DEFAULT 0,
                voice_confirmed TINYINT NOT NULL DEFAULT 0,
                error_code VARCHAR(64) NULL,
                error_message VARCHAR(255) NULL,
                create_dept BIGINT NULL,
                create_by BIGINT NULL,
                create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                update_by BIGINT NULL,
                update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                PRIMARY KEY (id),
                UNIQUE KEY uk_%s_idempotency (tenant_id, owner_user_id, job_type, idempotency_key)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """.formatted(table(DH_JOB), table(DH_JOB));
    }

    private String minimalCreationProjectDdl() {
        return """
            CREATE TABLE `%s` (
                project_id BIGINT NOT NULL,
                owner_user_id BIGINT NOT NULL,
                source_type VARCHAR(32) NOT NULL,
                source_ref_id BIGINT NOT NULL,
                del_flag CHAR(1) NOT NULL,
                PRIMARY KEY (project_id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """.formatted(table(CREATION_PROJECT));
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
                sha256 CHAR(64) NOT NULL,
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

    private String applyOrchestrationStateToCreateDdl(String ddl) {
        String activeResultBranch = """
            OR
            (
                run_status IN ('queued', 'running', 'waiting_external_task')
                AND candidate_asset_id IS NULL
                AND result_summary_json IS NULL
                AND result_digest IS NULL
                AND error_code IS NULL
                AND error_summary IS NULL
            )
            """.indent(8);
        String waitingInputBranch = """
            OR
            (
                run_status = 'waiting_input'
                AND candidate_asset_id IS NULL
                AND result_summary_json IS NULL
                AND result_digest IS NULL
                AND error_code IS NOT NULL
                AND error_summary IS NOT NULL
            )
            """.indent(8);
        String upgraded = ddl.replace("\r\n", "\n")
            .replace("lease_generation BIGINT NOT NULL DEFAULT 0,",
                "lease_generation BIGINT NOT NULL DEFAULT 0,\n    retry_count BIGINT NOT NULL DEFAULT 0,")
            .replace("'queued', 'running', 'waiting_external_task',",
                "'queued', 'running', 'waiting_input', 'waiting_external_task',")
            .replace("row_version >= 0 AND lease_generation >= 0",
                "row_version >= 0 AND lease_generation >= 0 AND retry_count >= 0")
            .replace(activeResultBranch, waitingInputBranch + activeResultBranch);
        if (!upgraded.contains("retry_count BIGINT NOT NULL DEFAULT 0")
            || !upgraded.contains("run_status = 'waiting_input'")) {
            throw new IllegalStateException("Frozen AgentRun DDL could not be upgraded for T4");
        }
        return upgraded;
    }

    private void applyQualityControlState(Statement statement) throws SQLException {
        String run = table(RUN);
        statement.execute("""
            ALTER TABLE `%s`
                ADD COLUMN quality_repair_count BIGINT NOT NULL DEFAULT 0 AFTER retry_count,
                ADD COLUMN pending_approval_id BIGINT NULL AFTER quality_repair_count,
                ADD COLUMN approval_revision BIGINT NOT NULL DEFAULT 0 AFTER pending_approval_id,
                DROP CHECK %s_ck_3,
                DROP CHECK %s_ck_4,
                DROP CHECK %s_ck_7,
                DROP CHECK %s_ck_8,
                ADD CONSTRAINT %s_ck_3 CHECK (
                    run_status IN (
                        'queued', 'running', 'waiting_input', 'waiting_approval',
                        'waiting_external_task', 'completed', 'failed', 'cancelled'
                    )
                ),
                ADD CONSTRAINT %s_ck_4 CHECK (
                    row_version >= 0 AND lease_generation >= 0 AND retry_count >= 0
                    AND quality_repair_count BETWEEN 0 AND 2 AND approval_revision >= 0
                ),
                ADD CONSTRAINT %s_ck_7 CHECK (
                    (
                        run_status = 'waiting_external_task'
                        AND waiting_task_source IS NOT NULL
                        AND waiting_task_source IN ('digital_human_generation', 'ai_task')
                        AND waiting_task_id IS NOT NULL
                        AND waiting_task_id > 0
                        AND waiting_contract_revision IS NOT NULL
                        AND waiting_contract_revision = contract_revision
                        AND resume_after IS NOT NULL
                    )
                    OR
                    (
                        run_status = 'waiting_approval'
                        AND resume_after IS NULL
                        AND (
                            (waiting_task_source IS NULL AND waiting_task_id IS NULL
                                AND waiting_contract_revision IS NULL AND candidate_asset_id IS NULL)
                            OR
                            (waiting_task_source IS NOT NULL AND waiting_task_source = 'ai_task'
                                AND waiting_task_id IS NOT NULL AND waiting_task_id > 0
                                AND waiting_contract_revision IS NOT NULL
                                AND waiting_contract_revision = contract_revision
                                AND candidate_asset_id IS NOT NULL AND candidate_asset_id > 0)
                        )
                    )
                    OR
                    (
                        run_status NOT IN ('waiting_external_task', 'waiting_approval')
                        AND waiting_task_source IS NULL AND waiting_task_id IS NULL
                        AND waiting_contract_revision IS NULL
                    )
                ),
                ADD CONSTRAINT %s_ck_8 CHECK (
                    (run_status = 'completed' AND candidate_asset_id IS NOT NULL AND candidate_asset_id > 0
                        AND result_summary_json IS NOT NULL
                        AND result_digest IS NOT NULL
                        AND result_digest REGEXP '^[0-9a-f]{64}$'
                        AND error_code IS NULL AND error_summary IS NULL)
                    OR (run_status = 'failed' AND candidate_asset_id IS NULL
                        AND result_summary_json IS NULL AND result_digest IS NULL
                        AND error_code IS NOT NULL AND error_summary IS NOT NULL)
                    OR (run_status = 'cancelled' AND candidate_asset_id IS NULL
                        AND result_summary_json IS NULL AND result_digest IS NULL)
                    OR (run_status = 'waiting_input' AND candidate_asset_id IS NULL
                        AND result_summary_json IS NULL AND result_digest IS NULL
                        AND error_code IS NOT NULL AND error_summary IS NOT NULL)
                    OR (run_status = 'waiting_approval'
                        AND (candidate_asset_id IS NULL OR candidate_asset_id > 0)
                        AND result_summary_json IS NULL AND result_digest IS NULL
                        AND error_code IS NULL AND error_summary IS NULL)
                    OR (run_status IN ('queued', 'running', 'waiting_external_task')
                        AND candidate_asset_id IS NULL AND result_summary_json IS NULL
                        AND result_digest IS NULL AND error_code IS NULL AND error_summary IS NULL)
                ),
                ADD CONSTRAINT %s_ck_12 CHECK (
                    (run_status = 'waiting_approval' AND pending_approval_id IS NOT NULL
                        AND pending_approval_id > 0 AND approval_revision > 0)
                    OR (run_status <> 'waiting_approval' AND pending_approval_id IS NULL)
                )
            """.formatted(run, run, run, run, run, run, run, run, run, run));
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

    private static TimelineOutputQualityDTO quality(long taskId, long assetId, Set<String> failures) {
        List<TimelineOutputQualityDTO.Criterion> criteria = QUALITY_CODES.stream().map(code -> {
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
                Map.of());
        }).toList();
        return new TimelineOutputQualityDTO(Long.toString(taskId), Long.toString(assetId), "a".repeat(64),
            "1", "b".repeat(64), "t5-quality-1", criteria);
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

    private static final class CountingGoldenToolService implements IAgentToolService {

        private final long voiceJobId;
        private final long videoJobId;
        private final long projectId;
        private final long renderTaskId;
        private final long outputAssetId;
        private final long repairProjectId;
        private final long repairRenderTaskId;
        private final long repairOutputAssetId;
        private final TimelineOutputQualityDTO outputQuality;
        private final TimelineOutputQualityDTO repairOutputQuality;
        private final Runnable afterFirstRepairRenderAccepted;
        private final Map<String, Integer> invocations = new LinkedHashMap<>();
        private final Map<String, String> accepted = new LinkedHashMap<>();
        private boolean voiceCompleted;
        private boolean voiceConfirmed;
        private boolean videoCompleted;
        private boolean renderCompleted;
        private boolean repairRenderCompleted;
        private boolean repairFenceTriggered;

        private CountingGoldenToolService(long voiceJobId, long videoJobId, long projectId,
                                           long renderTaskId, long outputAssetId) {
            this(voiceJobId, videoJobId, projectId, renderTaskId, outputAssetId, projectId, renderTaskId,
                outputAssetId, quality(renderTaskId, outputAssetId, Set.of()),
                quality(renderTaskId, outputAssetId, Set.of()), null);
        }

        private CountingGoldenToolService(long voiceJobId, long videoJobId, long projectId,
                                          long renderTaskId, long outputAssetId, long repairProjectId,
                                          long repairRenderTaskId, long repairOutputAssetId,
                                          TimelineOutputQualityDTO outputQuality,
                                          TimelineOutputQualityDTO repairOutputQuality,
                                          Runnable afterFirstRepairRenderAccepted) {
            this.voiceJobId = voiceJobId;
            this.videoJobId = videoJobId;
            this.projectId = projectId;
            this.renderTaskId = renderTaskId;
            this.outputAssetId = outputAssetId;
            this.repairProjectId = repairProjectId;
            this.repairRenderTaskId = repairRenderTaskId;
            this.repairOutputAssetId = repairOutputAssetId;
            this.outputQuality = outputQuality;
            this.repairOutputQuality = repairOutputQuality;
            this.afterFirstRepairRenderAccepted = afterFirstRepairRenderAccepted;
        }

        @Override
        public synchronized AgentToolDTOs.Result execute(AppPrincipalSnapshotDTO principal,
                                                         AgentToolDTOs.Call call) {
            invocations.merge(call.toolName(), 1, Integer::sum);
            return switch (call.toolName()) {
                case "submit_voice_generation" -> {
                    accept(call);
                    yield generation(voiceJobId, null, "voice_generate", voiceCompleted, voiceConfirmed);
                }
                case "confirm_voice_generation" -> {
                    requireId(call, voiceJobId);
                    voiceConfirmed = true;
                    yield generation(voiceJobId, null, "voice_generate", true, true);
                }
                case "get_generation_status" -> generationStatus(call);
                case "submit_digital_human_video" -> {
                    accept(call);
                    requireId(call, "voiceJobId", voiceJobId);
                    yield generation(videoJobId, voiceJobId, "video_generate", videoCompleted, false);
                }
                case "prepare_timeline_project" -> {
                    requireId(call, "videoJobId", videoJobId);
                    accept(call);
                    long selectedProjectId = repairCall(call) ? repairProjectId : projectId;
                    yield new AgentToolDTOs.ProjectResult(Long.toString(selectedProjectId), "editing", "1",
                        1080, 1920, 30, 30_000L);
                }
                case "render_timeline" -> {
                    accept(call);
                    boolean repair = repairCall(call);
                    long selectedProjectId = repair ? repairProjectId : projectId;
                    long selectedTaskId = repair ? repairRenderTaskId : renderTaskId;
                    requireId(call, "projectId", selectedProjectId);
                    if (repair && !repairFenceTriggered && afterFirstRepairRenderAccepted != null) {
                        repairFenceTriggered = true;
                        afterFirstRepairRenderAccepted.run();
                    }
                    yield new AgentToolDTOs.RenderTaskResult(Long.toString(selectedTaskId), "running",
                        "rendering", Long.toString(selectedProjectId), "1");
                }
                case "get_timeline_render_status" -> renderStatus(call);
                case "inspect_timeline_output" -> output(call);
                default -> throw new IllegalArgumentException("unexpected tool: " + call.toolName());
            };
        }

        private AgentToolDTOs.GenerationJobResult generationStatus(AgentToolDTOs.Call call) {
            long jobId = Long.parseLong(text(call, "jobId"));
            if (jobId == voiceJobId) {
                return generation(voiceJobId, null, "voice_generate", voiceCompleted, voiceConfirmed);
            }
            if (jobId == videoJobId) {
                return generation(videoJobId, voiceJobId, "video_generate", videoCompleted, false);
            }
            throw new IllegalArgumentException("unexpected generation job");
        }

        private AgentToolDTOs.RenderStatusResult renderStatus(AgentToolDTOs.Call call) {
            long taskId = Long.parseLong(text(call, "taskId"));
            if (taskId == renderTaskId) {
                return new AgentToolDTOs.RenderStatusResult(Long.toString(renderTaskId),
                    renderCompleted ? "success" : "running", renderCompleted ? "completed" : "rendering",
                    renderCompleted ? 100 : 50, Long.toString(projectId), "1",
                    renderCompleted ? Long.toString(outputAssetId) : null, !renderCompleted, false, null, null,
                    "digital_human_job", Long.toString(videoJobId), "T6 quality repair");
            }
            if (taskId == repairRenderTaskId) {
                return new AgentToolDTOs.RenderStatusResult(Long.toString(repairRenderTaskId),
                    repairRenderCompleted ? "success" : "running",
                    repairRenderCompleted ? "completed" : "rendering", repairRenderCompleted ? 100 : 50,
                    Long.toString(repairProjectId), "1",
                    repairRenderCompleted ? Long.toString(repairOutputAssetId) : null,
                    !repairRenderCompleted, false, null, null,
                    "digital_human_job", Long.toString(videoJobId), "T6 quality repair");
            }
            throw new IllegalArgumentException("unexpected render task");
        }

        private AgentToolDTOs.OutputInspectionResult output(AgentToolDTOs.Call call) {
            long taskId = Long.parseLong(text(call, "taskId"));
            long assetId;
            TimelineOutputQualityDTO quality;
            if (taskId == renderTaskId && renderCompleted) {
                assetId = outputAssetId;
                quality = outputQuality;
            } else if (taskId == repairRenderTaskId && repairRenderCompleted) {
                assetId = repairOutputAssetId;
                quality = repairOutputQuality;
            } else {
                throw new IllegalStateException("render is not complete");
            }
            return new AgentToolDTOs.OutputInspectionResult(Long.toString(taskId), Long.toString(assetId),
                "ready", "video", "timeline_render_output", "video/mp4", "a".repeat(64), 1_024L,
                30_000L, 1080, 1920, true, true,
                "/api/studio/creation-assets/" + assetId + "/content", quality);
        }

        private AgentToolDTOs.GenerationJobResult generation(long jobId, Long parentJobId, String jobType,
                                                              boolean completed, boolean confirmed) {
            return new AgentToolDTOs.GenerationJobResult(Long.toString(jobId),
                parentJobId == null ? null : Long.toString(parentJobId), jobType,
                completed ? "succeeded" : "running", completed ? "completed" : "provider_processing",
                completed ? 100 : 50, confirmed, completed, null, null);
        }

        private void accept(AgentToolDTOs.Call call) {
            String key = text(call, "idempotencyKey");
            String identity = call.toolName() + ':' + key;
            String fingerprint = call.arguments().toString();
            String existing = accepted.putIfAbsent(identity, fingerprint);
            if (existing != null && !existing.equals(fingerprint)) {
                throw new IllegalStateException("idempotency key reused with different arguments");
            }
        }

        private void requireId(AgentToolDTOs.Call call, long expected) {
            requireId(call, call.toolName().contains("render") || call.toolName().contains("output")
                ? "taskId" : "jobId", expected);
        }

        private void requireId(AgentToolDTOs.Call call, String field, long expected) {
            if (!Long.toString(expected).equals(text(call, field))) {
                throw new IllegalArgumentException("unexpected " + field);
            }
        }

        private String text(AgentToolDTOs.Call call, String field) {
            var value = call.arguments().get(field);
            if (value == null || !value.isTextual() || value.textValue() == null) {
                throw new IllegalArgumentException("missing " + field);
            }
            return value.textValue();
        }

        private boolean repairCall(AgentToolDTOs.Call call) {
            return text(call, "idempotencyKey").contains("repair-");
        }

        private synchronized void completeVoice() {
            voiceCompleted = true;
        }

        private synchronized void completeVideo() {
            videoCompleted = true;
        }

        private synchronized void completeRender() {
            renderCompleted = true;
        }

        private synchronized void completeRepairRender() {
            repairRenderCompleted = true;
        }

        private synchronized int acceptedSubmissions(String toolName) {
            return (int) accepted.keySet().stream().filter(key -> key.startsWith(toolName + ':')).count();
        }

        private synchronized int submitInvocations(String toolName) {
            return invocations.getOrDefault(toolName, 0);
        }

        private synchronized int totalInvocations() {
            return invocations.values().stream().mapToInt(Integer::intValue).sum();
        }
    }
}

record AgentRunTestTableNames(Map<String, String> values) {
}

@Configuration(proxyBeanMethods = false)
@EnableTransactionManagement(proxyTargetClass = true)
@MapperScan(basePackages = {
    "org.dromara.aivideo.agent.mapper",
    "org.dromara.aivideo.digitalhuman.mapper"
})
class AgentRunPersistenceConfiguration {

    @Bean
    PlatformTransactionManager transactionManager(DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

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
                                     AgentRunEvaluationMapper evaluationMapper,
                                     AgentRunApprovalMapper approvalMapper,
                                     JsonMapper jsonMapper) {
        return new AgentRunServiceImpl(briefMapper, profileMapper, runMapper, evaluationMapper, approvalMapper,
            jsonMapper);
    }
}

@Configuration(proxyBeanMethods = false)
class AgentRunOrchestrationPersistenceConfiguration {

    @Bean
    IAgentRunOrchestrationService agentRunOrchestrationService(IAgentRunService runService,
                                                               IAgentToolService toolService,
                                                               JsonMapper jsonMapper) {
        return new AgentRunOrchestrationServiceImpl(runService, toolService, jsonMapper);
    }
}
