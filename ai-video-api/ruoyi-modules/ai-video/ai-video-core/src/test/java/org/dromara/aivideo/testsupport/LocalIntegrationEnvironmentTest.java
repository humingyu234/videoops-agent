package org.dromara.aivideo.testsupport;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("dev")
class LocalIntegrationEnvironmentTest {

    private static final String RUN_ID = "00000000-0000-4000-8000-000000000001";

    @TempDir
    Path tempDir;

    @Test
    void loadsCommittedDevelopmentConfigurationAndLetsEnvironmentOverrideIt() throws IOException {
        Path configPath = tempDir.resolve("application-dev.yml");
        Files.writeString(configPath, """
            spring:
              datasource:
                dynamic:
                  datasource:
                    master:
                      url: jdbc:mysql://127.0.0.1:3306/ai_video?useUnicode=true
                      username: committed-user
                      password: committed-password
              data:
                redis:
                  host: 127.0.0.1
                  port: 6379
                  database: 0
                  password: committed-redis-password
            """);
        Map<String, String> environment = new HashMap<>();
        environment.put("AI_VIDEO_IT_MYSQL_URL", "jdbc:mysql://localhost:3307/ai_video?useUnicode=true");
        environment.put("AI_VIDEO_IT_MYSQL_USERNAME", "environment-user");
        environment.put("AI_VIDEO_IT_REDIS_DATABASE", "3");

        LocalIntegrationEnvironment actual = LocalIntegrationEnvironment.from(
            environment, "true", RUN_ID, configPath);

        assertThat(actual.jdbcUrl()).isEqualTo("jdbc:mysql://localhost:3307/ai_video_test?useUnicode=true");
        assertThat(actual.mysqlUsername()).isEqualTo("environment-user");
        assertThat(actual.mysqlPassword()).isEqualTo("committed-password");
        assertThat(actual.redisDatabase()).isEqualTo(15);
        assertThat(actual.redisPassword()).isEqualTo("committed-redis-password");
    }

    @Test
    void rejectsMissingExplicitEnablementMarkerBeforeReadingConnectionValues() {
        assertThatThrownBy(() -> LocalIntegrationEnvironment.from(validEnvironment(), "false", RUN_ID))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("local-integration-test");
    }

    @Test
    void rejectsMissingRequiredConnectionValue() {
        Map<String, String> environment = validEnvironment();
        environment.remove("AI_VIDEO_IT_REDIS_DATABASE");

        assertThatThrownBy(() -> LocalIntegrationEnvironment.from(environment, "true", RUN_ID))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("AI_VIDEO_IT_REDIS_DATABASE");
    }

    @Test
    void rejectsNonLocalDatabaseAndRedisEndpoints() {
        Map<String, String> mysqlEnvironment = validEnvironment();
        mysqlEnvironment.put("AI_VIDEO_IT_MYSQL_URL", "jdbc:mysql://db.example.test:3306/ai_video_test");
        assertThatThrownBy(() -> LocalIntegrationEnvironment.from(mysqlEnvironment, "true", RUN_ID))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("MySQL").hasMessageContaining("本机");

        Map<String, String> redisEnvironment = validEnvironment();
        redisEnvironment.put("AI_VIDEO_IT_REDIS_HOST", "redis.example.test");
        assertThatThrownBy(() -> LocalIntegrationEnvironment.from(redisEnvironment, "true", RUN_ID))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Redis").hasMessageContaining("本机");
    }

    @Test
    void rejectsDatabaseOtherThanDedicatedTestDatabaseAndDefaultRedisDatabase() {
        Map<String, String> mysqlEnvironment = validEnvironment();
        mysqlEnvironment.put("AI_VIDEO_IT_MYSQL_URL", "jdbc:mysql://127.0.0.1:3306/ai_video_dev");
        assertThatThrownBy(() -> LocalIntegrationEnvironment.from(mysqlEnvironment, "true", RUN_ID))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("ai_video_test");

        Map<String, String> redisEnvironment = validEnvironment();
        redisEnvironment.put("AI_VIDEO_IT_REDIS_DATABASE", "0");
        assertThatThrownBy(() -> LocalIntegrationEnvironment.from(redisEnvironment, "true", RUN_ID))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Redis").hasMessageContaining("独立");
    }

    @Test
    void rejectsCredentialsEmbeddedInJdbcUrl() {
        Map<String, String> userInfoEnvironment = validEnvironment();
        userInfoEnvironment.put("AI_VIDEO_IT_MYSQL_URL",
            "jdbc:mysql://user:password@localhost:3306/ai_video_test");
        assertThatThrownBy(() -> LocalIntegrationEnvironment.from(userInfoEnvironment, "true", RUN_ID))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("凭据");

        Map<String, String> queryEnvironment = validEnvironment();
        queryEnvironment.put("AI_VIDEO_IT_MYSQL_URL",
            "jdbc:mysql://localhost:3306/ai_video_test?useUnicode=true&password=secret");
        assertThatThrownBy(() -> LocalIntegrationEnvironment.from(queryEnvironment, "true", RUN_ID))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("凭据");
    }

    @Test
    void rejectsUncontrolledKeysAlreadyPresentInDedicatedRedisDatabase() {
        LocalIntegrationEnvironment environment = LocalIntegrationEnvironment.from(validEnvironment(), "true", RUN_ID);

        assertThatThrownBy(() -> environment.assertRedisBaselineKeysControlled(Set.of(
            "aivideo:it:another-run:owned-key",
            "global:sys_oss:default_config"
        )))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Redis").hasMessageContaining("基线");

        environment.assertRedisBaselineKeysControlled(Set.of("aivideo:it:another-run:owned-key"));
    }

    @Test
    void recognizesOnlyNamespacedRedissonCompanionKeysAsControlled() {
        LocalIntegrationEnvironment environment = LocalIntegrationEnvironment.from(validEnvironment(), "true", RUN_ID);
        String currentPrefix = environment.redisKeyPrefix();

        environment.assertRedisBaselineKeysControlled(Set.of(
            "redisson__timeout__set:{aivideo:it:another-run:sys_client}",
            "{aivideo:it:another-run:sys_client}:redisson_options"
        ));
        assertThat(environment.isCurrentRunRedisKey("redisson__timeout__set:{" + currentPrefix + "sys_client}"))
            .isTrue();
        assertThat(environment.isCurrentRunRedisKey("redisson__idle__set:{" + currentPrefix + "sys_client}"))
            .isTrue();
        assertThat(environment.isCurrentRunRedisKey(
            "redisson__map_cache__last_access__set:{" + currentPrefix + "sys_client}"))
            .isTrue();
        assertThat(environment.isCurrentRunRedisKey(
            "redisson__execute_task_once_latch:{" + currentPrefix + "sys_client}"))
            .isTrue();
        assertThat(environment.isCurrentRunRedisKey("{" + currentPrefix + "sys_client}:redisson_options"))
            .isTrue();
        assertThat(environment.isCurrentRunRedisKey("foreign:{" + currentPrefix + "sys_client}"))
            .isFalse();
        assertThat(environment.isCurrentRunRedisKey("redissonFake:{" + currentPrefix + "sys_client}"))
            .isFalse();
        assertThat(environment.isCurrentRunRedisKey("redisson_evil:{" + currentPrefix + "sys_client}"))
            .isFalse();
        assertThat(environment.isCurrentRunRedisKey(
            "redisson__execute_task_once_latch_evil:{" + currentPrefix + "sys_client}"))
            .isFalse();
        assertThat(environment.isCurrentRunRedisKey("{" + currentPrefix + "sys_client}:redissonFake"))
            .isFalse();
        assertThat(environment.isCurrentRunRedisKey("redisson__timeout__set:{global:sys_client}"))
            .isFalse();
        assertThat(environment.isCurrentRunRedisKey(
            "redisson__execute_task_once_latch:{aivideo:it:another-run:sys_client}"))
            .isFalse();
    }

    @Test
    void acceptsLocalDedicatedEnvironmentAndGeneratesCurrentRunPrefix() {
        LocalIntegrationEnvironment environment = LocalIntegrationEnvironment.from(validEnvironment(), "true", RUN_ID);

        assertThat(environment.jdbcUrl()).isEqualTo("jdbc:mysql://localhost:3306/ai_video_test");
        assertThat(environment.redisDatabase()).isEqualTo(15);
        assertThat(environment.redisKeyPrefix()).isEqualTo("aivideo:it:" + RUN_ID + ':');
        assertThat(environment.redissonKeyPrefix()).isEqualTo("aivideo:it:" + RUN_ID);
        assertThat(environment.newNamespacedRedisConfig().getNameMapper().map("shared-cache"))
            .isEqualTo("aivideo:it:" + RUN_ID + ":shared-cache");
        assertThat(environment.newNamespacedRedisConfig().getNameMapper()
            .unmap("aivideo:it:" + RUN_ID + ":shared-cache"))
            .isEqualTo("shared-cache");
        assertThat(environment.newNamespacedRedisConfig().getNameMapper()
            .map(environment.redisKeyPrefix() + "Authorization:app:token:test"))
            .isEqualTo(environment.redisKeyPrefix() + "Authorization:app:token:test");
    }

    private static Map<String, String> validEnvironment() {
        Map<String, String> environment = new HashMap<>();
        environment.put("AI_VIDEO_IT_MYSQL_URL", "jdbc:mysql://localhost:3306/ai_video_test");
        environment.put("AI_VIDEO_IT_MYSQL_USERNAME", "ai_video_test");
        environment.put("AI_VIDEO_IT_MYSQL_PASSWORD", "");
        environment.put("AI_VIDEO_IT_REDIS_HOST", "127.0.0.1");
        environment.put("AI_VIDEO_IT_REDIS_PORT", "6379");
        environment.put("AI_VIDEO_IT_REDIS_DATABASE", "15");
        environment.put("AI_VIDEO_IT_REDIS_PASSWORD", "");
        return environment;
    }
}
