package org.dromara.aivideo.workflow;

import org.dromara.aivideo.task.dto.AiTaskAccessScopeDTO;
import org.dromara.aivideo.task.dto.AiTaskActorDTO;
import org.dromara.aivideo.task.dto.AiTaskDTO;
import org.dromara.aivideo.task.dto.AiTaskLeaseDTO;
import org.dromara.aivideo.task.dto.CreateWorkflowAiTaskDTO;
import org.dromara.aivideo.task.dto.WorkflowAiTaskPayloadDTO;
import org.dromara.aivideo.task.enums.AiTaskResourceType;
import org.dromara.aivideo.task.enums.AiTaskStage;
import org.dromara.aivideo.task.enums.AiTaskType;
import org.dromara.aivideo.task.service.IAiTaskTransactionService;
import org.dromara.aivideo.testsupport.LocalIntegrationEnvironment;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import tools.jackson.databind.json.JsonMapper;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Minimal real-MySQL proof for the C0 dual-actor task boundary; it never calls RunningHub. */
@Tag("dev")
class WorkflowRunningHubTaskRuntimeIT {
    private static final LocalIntegrationEnvironment ENV = LocalIntegrationEnvironment.requireFromEnvironment();

    private final List<Long> taskIds = new ArrayList<>();
    private AnnotationConfigApplicationContext context;
    private IAiTaskTransactionService tasks;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("workflow-task-runtime-it",
            Map.of("ai-task.jdbc-url", ENV.jdbcUrl(), "ai-task.username", ENV.mysqlUsername(),
                "ai-task.password", ENV.mysqlPassword())));
        context.register((Class<Object>) Class.forName("org.dromara.aivideo.task.AiTaskRuntimeConfiguration"));
        context.refresh();
        tasks = context.getBean(IAiTaskTransactionService.class);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (context != null) {
            context.close();
        }
        try (Connection connection = ENV.openMySqlConnection()) {
            for (Long taskId : taskIds) {
                execute(connection, "DELETE FROM av_workflow_order WHERE task_id = ?", taskId);
                execute(connection, "DELETE FROM av_ai_task_attempt WHERE task_id = ?", taskId);
                execute(connection, "DELETE FROM av_ai_task_execution WHERE task_id = ?", taskId);
                execute(connection, "DELETE FROM av_ai_task WHERE task_id = ?", taskId);
            }
        }
    }

    @Test
    void appAndSysActorsAreIdempotentlyIsolatedAndSysPreparingInputsCanBeRecovered() throws Exception {
        JsonMapper mapper = JsonMapper.builder().build();
        String key = "workflow-runtime-" + System.nanoTime();
        long orderId = 8_400_000_000_000_000_001L;
        long templateId = 8_400_000_000_000_000_002L;
        WorkflowAiTaskPayloadDTO appPayload = new WorkflowAiTaskPayloadDTO(Long.toString(orderId),
            Long.toString(templateId), "sha256:" + "a".repeat(64),
            Map.of("prompt", mapper.readTree("\"hello\"")));
        WorkflowAiTaskPayloadDTO sysPayload = new WorkflowAiTaskPayloadDTO(null, Long.toString(templateId),
            "sha256:" + "a".repeat(64), Map.of("prompt", mapper.readTree("\"hello\"")));
        CreateWorkflowAiTaskDTO appCommand = new CreateWorkflowAiTaskDTO(AiTaskType.WORKFLOW_TEMPLATE_GENERATE,
            AiTaskResourceType.WORKFLOW_ORDER, Long.toString(orderId), key, "b".repeat(64), appPayload);
        CreateWorkflowAiTaskDTO sysCommand = new CreateWorkflowAiTaskDTO(AiTaskType.WORKFLOW_TEMPLATE_TEST,
            AiTaskResourceType.WORKFLOW_TEMPLATE, Long.toString(templateId), key, "c".repeat(64), sysPayload);

        AiTaskDTO app = tasks.createWorkflowTask(new AiTaskActorDTO("app_user", 7L, 7L), appCommand);
        AiTaskDTO replay = tasks.createWorkflowTask(new AiTaskActorDTO("app_user", 7L, 7L), appCommand);
        AiTaskDTO sys = tasks.createWorkflowTask(new AiTaskActorDTO("sys_user", 9L, null), sysCommand);
        taskIds.add(Long.valueOf(app.taskId()));
        taskIds.add(Long.valueOf(sys.taskId()));
        assertThat(replay.taskId()).isEqualTo(app.taskId());
        assertThat(sys.taskId()).isNotEqualTo(app.taskId());

        insertOrder(orderId, templateId, Long.parseLong(app.taskId()), key);
        assertThat(tasks.pageOwned(new AiTaskAccessScopeDTO(11L, 7L, "personal-23"), null, new PageQuery()).getRows())
            .extracting("taskId").contains(app.taskId());
        assertThat(tasks.pageOwned(new AiTaskAccessScopeDTO(11L, 7L, "personal-24"), null, new PageQuery()).getRows())
            .extracting("taskId").doesNotContain(app.taskId());

        AiTaskLeaseDTO first = tasks.claimNext("workflow-runtime-a", 1, 4);
        AiTaskLeaseDTO second = tasks.claimNext("workflow-runtime-b", 1, 4);
        AiTaskLeaseDTO sysLease = "sys_user".equals(first.getActorType()) ? first : second;
        assertThat(sysLease.getActorType()).isEqualTo("sys_user");
        stageAndExpire(sysLease, AiTaskStage.PREPARING_INPUTS.value());
        assertThat(tasks.recoverExpired(Instant.now(), 1)).isEqualTo(1);
    }

    @Test
    void providerInFlightWorkflowIsNotRequeuedByGenericLeaseRecovery() throws Exception {
        JsonMapper mapper = JsonMapper.builder().build();
        String key = "workflow-provider-in-flight-" + System.nanoTime();
        long templateId = 8_400_000_000_000_000_003L;
        WorkflowAiTaskPayloadDTO payload = new WorkflowAiTaskPayloadDTO(null, Long.toString(templateId),
            "sha256:" + "a".repeat(64), Map.of("prompt", mapper.readTree("\"hello\"")));
        CreateWorkflowAiTaskDTO command = new CreateWorkflowAiTaskDTO(AiTaskType.WORKFLOW_TEMPLATE_TEST,
            AiTaskResourceType.WORKFLOW_TEMPLATE, Long.toString(templateId), key, "e".repeat(64), payload);
        AiTaskDTO task = tasks.createWorkflowTask(new AiTaskActorDTO("sys_user", 9L, null), command);
        taskIds.add(Long.valueOf(task.taskId()));

        AiTaskLeaseDTO lease = tasks.claimNext("workflow-provider-in-flight", 1, 4);
        assertThat(lease).isNotNull();
        assertThat(lease.getTaskId()).isEqualTo(task.taskId());
        stageAndExpire(lease, AiTaskStage.SUBMITTING_TO_PROVIDER.value());

        assertThat(tasks.recoverExpired(Instant.now(), 1)).isZero();
        assertThat(readState("SELECT task_status,stage FROM av_ai_task WHERE task_id=?",
            Long.parseLong(task.taskId()))).isEqualTo(new PersistedState("running", "submitting_to_provider"));
        assertThat(readState("SELECT execution_status,stage FROM av_ai_task_execution WHERE task_execution_id=?",
            Long.parseLong(lease.getExecutionId())))
            .isEqualTo(new PersistedState("running", "submitting_to_provider"));
    }

    private void insertOrder(long orderId, long templateId, long taskId, String key) throws Exception {
        try (Connection connection = ENV.openMySqlConnection(); PreparedStatement statement = connection.prepareStatement("""
            INSERT INTO av_workflow_order (order_id,tenant_id,order_no,workspace_id,owner_user_id,template_id,
                task_id,idempotency_key,schema_hash,input_payload_json,request_hash,billing_mode,
                template_title_snapshot,input_display_snapshot_json)
            VALUES (?,11,?,23,7,?,?,?,'sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
                JSON_OBJECT(),'dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd','free','test',JSON_OBJECT())
            """)) {
            statement.setLong(1, orderId);
            statement.setString(2, "WO-" + orderId);
            statement.setLong(3, templateId);
            statement.setLong(4, taskId);
            statement.setString(5, key);
            assertThat(statement.executeUpdate()).isEqualTo(1);
        }
    }

    private void stageAndExpire(AiTaskLeaseDTO lease, String stage) throws Exception {
        try (Connection connection = ENV.openMySqlConnection();
             PreparedStatement root = connection.prepareStatement("UPDATE av_ai_task SET stage=? WHERE task_id=?");
             PreparedStatement execution = connection.prepareStatement(
                 "UPDATE av_ai_task_execution SET stage=?,lease_expires_at=? WHERE task_execution_id=?")) {
            root.setString(1, stage);
            root.setLong(2, Long.parseLong(lease.getTaskId()));
            assertThat(root.executeUpdate()).isEqualTo(1);
            execution.setString(1, stage);
            execution.setTimestamp(2, Timestamp.valueOf("2000-01-01 00:00:00"));
            execution.setLong(3, Long.parseLong(lease.getExecutionId()));
            assertThat(execution.executeUpdate()).isEqualTo(1);
        }
    }

    private PersistedState readState(String sql, long id) throws Exception {
        try (Connection connection = ENV.openMySqlConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return new PersistedState(result.getString(1), result.getString(2));
            }
        }
    }

    private void execute(Connection connection, String sql, long id) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            statement.executeUpdate();
        }
    }

    private record PersistedState(String status, String stage) {
    }
}
