package org.dromara.aivideo.task;

import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.session.SqlSessionFactory;
import org.dromara.aivideo.creation.mapper.CreationProjectMapper;
import org.dromara.aivideo.task.dto.AiTaskLeaseDTO;
import org.dromara.aivideo.task.mapper.AiTaskAttemptMapper;
import org.dromara.aivideo.task.mapper.AiTaskExecutionMapper;
import org.dromara.aivideo.task.mapper.AiTaskMapper;
import org.dromara.aivideo.task.service.IAiTaskTransactionService;
import org.dromara.aivideo.task.service.IFreeAiTaskQuotaPolicyService;
import org.dromara.aivideo.task.service.impl.AiTaskTransactionServiceImpl;
import org.dromara.aivideo.testsupport.LocalIntegrationEnvironment;
import org.dromara.aivideo.timeline.mapper.TimelineAssetRefMapper;
import org.dromara.aivideo.timeline.mapper.TimelineDraftMapper;
import org.dromara.aivideo.timeline.mapper.TimelineVersionMapper;
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
import tools.jackson.databind.json.JsonMapper;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies that the local integration schema retains the task runtime's durable concurrency primitives. */
@Tag("dev")
class AiTaskRuntimeIT {

    private static final LocalIntegrationEnvironment ENV = LocalIntegrationEnvironment.requireFromEnvironment();
    private static final AtomicLong FIXTURE_SEQUENCE = new AtomicLong(8_000_000_000_000_000_000L);

    private AnnotationConfigApplicationContext applicationContext;
    private IAiTaskTransactionService transactionService;
    private long fixtureBase;

    @BeforeEach
    void setUp() {
        applicationContext = new AnnotationConfigApplicationContext();
        applicationContext.getEnvironment().getPropertySources().addFirst(new MapPropertySource("ai-task-runtime-it",
            Map.of(
                "ai-task.jdbc-url", ENV.jdbcUrl(),
                "ai-task.username", ENV.mysqlUsername(),
                "ai-task.password", ENV.mysqlPassword()
            )));
        applicationContext.register(AiTaskRuntimeConfiguration.class);
        applicationContext.refresh();
        transactionService = applicationContext.getBean(IAiTaskTransactionService.class);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (applicationContext != null) {
            applicationContext.close();
            applicationContext = null;
        }
        if (fixtureBase > 0) {
            deleteFixture(fixtureBase);
            fixtureBase = 0;
        }
    }

    @Test
    void taskRuntimeTablesExposeTheFrozenUniqueKeysAndRemainFreeOfPhysicalForeignKeys() throws Exception {
        List<String> tables = List.of("av_ai_task", "av_ai_task_execution", "av_ai_task_attempt");
        try (Connection connection = ENV.openMySqlConnection()) {
            assertThat(tableCount(connection, tables)).isEqualTo(3L);
            assertThat(foreignKeyCount(connection, tables)).isZero();
            assertThat(indexColumns(connection, "av_ai_task", "uk_av_ai_task_actor_idempotency"))
                .containsExactly("actor_type", "actor_id", "idempotency_key");
            assertThat(indexExists(connection, "av_ai_task_execution", "uk_av_ai_task_execution_no")).isTrue();
            assertThat(indexExists(connection, "av_ai_task_attempt", "uk_av_ai_task_attempt_no")).isTrue();
        }
    }

    @Test
    void twoSchedulersDoNotExceedTheSameOwnersPerUserLimit() throws Exception {
        fixtureBase = FIXTURE_SEQUENCE.getAndAdd(1_000L);
        long ownerUserId = fixtureBase + 500L;
        insertQueuedTasks(fixtureBase, List.of(ownerUserId), 32);

        List<AiTaskLeaseDTO> leases = claimConcurrently(1, 100);

        assertThat(leases.stream().filter(Objects::nonNull)).hasSize(1);
        assertThat(countRunningExecutions(fixtureBase)).isEqualTo(1L);
        assertThat(countRunningExecutions(ownerUserId, fixtureBase)).isEqualTo(1L);
    }

    @Test
    void twoSchedulersDoNotExceedTheClusterLimitAcrossDifferentOwners() throws Exception {
        fixtureBase = FIXTURE_SEQUENCE.getAndAdd(1_000L);
        long firstOwner = fixtureBase + 500L;
        long secondOwner = fixtureBase + 501L;
        insertQueuedTasks(fixtureBase, List.of(firstOwner, secondOwner), 32);

        List<AiTaskLeaseDTO> leases = claimConcurrently(1, 1);

        assertThat(leases.stream().filter(Objects::nonNull)).hasSize(1);
        assertThat(countRunningExecutions(fixtureBase)).isEqualTo(1L);
        assertThat(countRunningExecutions(firstOwner, fixtureBase)
            + countRunningExecutions(secondOwner, fixtureBase)).isEqualTo(1L);
    }

    private List<AiTaskLeaseDTO> claimConcurrently(int perUserLimit, int systemLimit) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<AiTaskLeaseDTO> first = executor.submit(() -> {
                ready.countDown();
                assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
                return transactionService.claimNext("timeline-runtime-worker-a", perUserLimit, systemLimit);
            });
            Future<AiTaskLeaseDTO> second = executor.submit(() -> {
                ready.countDown();
                assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
                return transactionService.claimNext("timeline-runtime-worker-b", perUserLimit, systemLimit);
            });
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return java.util.Arrays.asList(
                first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private void insertQueuedTasks(long base, List<Long> owners, int count) throws Exception {
        try (Connection connection = ENV.openMySqlConnection();
             PreparedStatement task = connection.prepareStatement("""
                 INSERT INTO av_ai_task (
                     task_id, owner_user_id, task_type, resource_type, resource_id, input_version_id,
                     idempotency_key, request_digest, request_schema_version, request_payload_json,
                     task_status, stage, progress_percent, row_version, cancel_requested, active_execution_id,
                     quota_policy_version, estimated_usage, actor_type, actor_id, create_by, update_by
                 ) VALUES (?, ?, 'timeline_fancy_text_suggest', 'draft', ?, NULL, ?, ?, '1', JSON_OBJECT(),
                     'queued', 'queued', 0, 0, 0, ?, 'timeline-free-1', 0, 'app_user', ?, ?, ?)
                 """);
             PreparedStatement execution = connection.prepareStatement("""
                 INSERT INTO av_ai_task_execution (
                     task_execution_id, owner_user_id, task_id, resource_id, execution_no,
                     execution_status, stage, progress_percent, row_version, next_run_at,
                     cancel_requested_snapshot, actor_type, actor_id, create_by, update_by
                 ) VALUES (?, ?, ?, ?, 1, 'queued', 'queued', 0, 0, ?, 0, 'app_user', ?, ?, ?)
                 """)) {
            for (int index = 0; index < count; index++) {
                long owner = owners.get(Math.min(index / 16, owners.size() - 1));
                long taskId = base + index;
                long executionId = base + 100L + index;
                long resourceId = base + 200L + index;
                task.setLong(1, taskId);
                task.setLong(2, owner);
                task.setLong(3, resourceId);
                task.setString(4, "ai-task-runtime-it-" + taskId);
                task.setString(5, "a".repeat(64));
                task.setLong(6, executionId);
                task.setLong(7, owner);
                task.setLong(8, owner);
                task.setLong(9, owner);
                task.addBatch();

                execution.setLong(1, executionId);
                execution.setLong(2, owner);
                execution.setLong(3, taskId);
                execution.setLong(4, resourceId);
                execution.setTimestamp(5, Timestamp.valueOf("2000-01-01 00:00:00"));
                execution.setLong(6, owner);
                execution.setLong(7, owner);
                execution.setLong(8, owner);
                execution.addBatch();
            }
            assertBatchSucceeded(task.executeBatch(), count);
            assertBatchSucceeded(execution.executeBatch(), count);
        }
    }

    private void assertBatchSucceeded(int[] results, int expectedCount) {
        assertThat(results).hasSize(expectedCount);
        for (int result : results) {
            assertThat(result).isIn(1, java.sql.Statement.SUCCESS_NO_INFO);
        }
    }

    private long countRunningExecutions(long base) throws Exception {
        return countRunningExecutions(null, base);
    }

    private long countRunningExecutions(Long ownerUserId, long base) throws Exception {
        String ownerPredicate = ownerUserId == null ? "" : " AND owner_user_id = ?";
        try (Connection connection = ENV.openMySqlConnection();
             PreparedStatement statement = connection.prepareStatement("""
                 SELECT COUNT(*)
                 FROM av_ai_task_execution
                 WHERE task_id BETWEEN ? AND ?
                   AND execution_status = 'running'
                 """ + ownerPredicate)) {
            statement.setLong(1, base);
            statement.setLong(2, base + 31L);
            if (ownerUserId != null) {
                statement.setLong(3, ownerUserId);
            }
            return singleLong(statement);
        }
    }

    private void deleteFixture(long base) throws Exception {
        try (Connection connection = ENV.openMySqlConnection()) {
            deleteByTaskRange(connection, "av_ai_task_attempt", base);
            deleteByTaskRange(connection, "av_ai_task_execution", base);
            deleteByTaskRange(connection, "av_ai_task", base);
        }
    }

    private void deleteByTaskRange(Connection connection, String table, long base) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
            "DELETE FROM " + table + " WHERE task_id BETWEEN ? AND ?")) {
            statement.setLong(1, base);
            statement.setLong(2, base + 31L);
            statement.executeUpdate();
        }
    }

    private long tableCount(Connection connection, List<String> tables) throws Exception {
        String placeholders = String.join(", ", java.util.Collections.nCopies(tables.size(), "?"));
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT COUNT(*)
            FROM information_schema.tables
            WHERE table_schema = DATABASE() AND table_name IN (%s)
            """.formatted(placeholders))) {
            for (int index = 0; index < tables.size(); index++) {
                statement.setString(index + 1, tables.get(index));
            }
            return singleLong(statement);
        }
    }

    private long foreignKeyCount(Connection connection, List<String> tables) throws Exception {
        String placeholders = String.join(", ", java.util.Collections.nCopies(tables.size(), "?"));
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT COUNT(*)
            FROM information_schema.key_column_usage
            WHERE constraint_schema = DATABASE()
              AND table_name IN (%s)
              AND referenced_table_name IS NOT NULL
            """.formatted(placeholders))) {
            for (int index = 0; index < tables.size(); index++) {
                statement.setString(index + 1, tables.get(index));
            }
            return singleLong(statement);
        }
    }

    private boolean indexExists(Connection connection, String table, String index) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT COUNT(*)
            FROM information_schema.statistics
            WHERE table_schema = DATABASE() AND table_name = ? AND index_name = ?
            """)) {
            statement.setString(1, table);
            statement.setString(2, index);
            return singleLong(statement) > 0;
        }
    }

    private List<String> indexColumns(Connection connection, String table, String index) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT column_name
            FROM information_schema.statistics
            WHERE table_schema = DATABASE() AND table_name = ? AND index_name = ?
            ORDER BY seq_in_index
            """)) {
            statement.setString(1, table);
            statement.setString(2, index);
            try (ResultSet result = statement.executeQuery()) {
                List<String> columns = new java.util.ArrayList<>();
                while (result.next()) {
                    columns.add(result.getString(1));
                }
                return columns;
            }
        }
    }

    private long singleLong(PreparedStatement statement) throws Exception {
        try (ResultSet result = statement.executeQuery()) {
            assertThat(result.next()).isTrue();
            return result.getLong(1);
        }
    }
}

@Configuration(proxyBeanMethods = false)
@MapperScan(basePackages = {
    "org.dromara.aivideo.task.mapper",
    "org.dromara.aivideo.creation.mapper",
    "org.dromara.aivideo.timeline.mapper"
})
class AiTaskRuntimeConfiguration {

    @Bean
    DataSource dataSource(@Value("${ai-task.jdbc-url}") String jdbcUrl,
                          @Value("${ai-task.username}") String username,
                          @Value("${ai-task.password}") String password) {
        return new org.apache.ibatis.datasource.unpooled.UnpooledDataSource(
            "com.mysql.cj.jdbc.Driver", jdbcUrl, username, password);
    }

    @Bean
    SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
        MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        GlobalConfig globalConfig = new GlobalConfig();
        globalConfig.setMetaObjectHandler(new InjectionMetaObjectHandler());
        factoryBean.setGlobalConfig(globalConfig);
        return factoryBean.getObject();
    }

    @Bean
    PlatformTransactionManager transactionManager(DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    @Bean
    JsonMapper jsonMapper() {
        return JsonMapper.builder().build();
    }

    @Bean
    IFreeAiTaskQuotaPolicyService freeAiTaskQuotaPolicyService() {
        return (taskType, requestedPolicyVersion, requestedEstimatedUsage) ->
            new IFreeAiTaskQuotaPolicyService.FrozenQuota("timeline-free-1", 0L);
    }

    @Bean
    IAiTaskTransactionService aiTaskTransactionService(AiTaskMapper taskMapper,
                                                        AiTaskExecutionMapper executionMapper,
                                                        AiTaskAttemptMapper attemptMapper,
                                                        CreationProjectMapper projectMapper,
                                                        TimelineDraftMapper draftMapper,
                                                        TimelineVersionMapper versionMapper,
                                                        TimelineAssetRefMapper assetRefMapper,
                                                        JsonMapper jsonMapper,
                                                        IFreeAiTaskQuotaPolicyService quotaPolicyService,
                                                        PlatformTransactionManager transactionManager) {
        return new AiTaskTransactionServiceImpl(taskMapper, executionMapper, attemptMapper, projectMapper,
            draftMapper, versionMapper, assetRefMapper, jsonMapper, quotaPolicyService, transactionManager);
    }
}
