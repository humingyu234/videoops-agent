package org.dromara.common.redis.config;

import org.dromara.common.redis.config.properties.RedissonProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.redisson.config.Config;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("dev")
class RedisConfigTest {

    private RedisConfig redisConfig;

    @BeforeEach
    void setUp() {
        redisConfig = new RedisConfig();
        ReflectionTestUtils.setField(redisConfig, "redissonProperties", new RedissonProperties());
    }

    @Test
    void normalizesOnlyExplicitEmptyCredentials() {
        Config config = credentials("", "");

        redisConfig.redissonCustomizer().customize(config);

        assertThat(config.getUsername()).isNull();
        assertThat(config.getPassword()).isNull();
    }

    @Test
    void preservesWhitespaceOnlyCredentials() {
        Config config = credentials(" ", "  ");

        redisConfig.redissonCustomizer().customize(config);

        assertThat(config.getUsername()).isEqualTo(" ");
        assertThat(config.getPassword()).isEqualTo("  ");
    }

    @Test
    void preservesNonEmptyCredentials() {
        Config config = credentials("redis-user", "redis-password");

        redisConfig.redissonCustomizer().customize(config);

        assertThat(config.getUsername()).isEqualTo("redis-user");
        assertThat(config.getPassword()).isEqualTo("redis-password");
    }

    private static Config credentials(String username, String password) {
        Config config = new Config();
        config.setUsername(username);
        config.setPassword(password);
        return config;
    }
}
