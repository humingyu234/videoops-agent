package org.dromara.aivideo.bootstrap;

import org.dromara.aivideo.infra.oss.AiVideoOssConfiguration;
import org.dromara.aivideo.infra.oss.AiVideoOssConfigurationInitializer;
import org.dromara.common.oss.client.OssClient;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.ArgumentMatchers.anyString;

@Tag("dev")
class CreatorOssConfigurationConditionTest {

    @Test
    void doesNotRegisterOrQueryTheDatabaseWhenOssIsDisabled() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        CreatorOssConfigurationCache cache = mock(CreatorOssConfigurationCache.class);

        new ApplicationContextRunner()
            .withBean(JdbcTemplate.class, () -> jdbcTemplate)
            .withBean(CreatorOssConfigurationCache.class, () -> cache)
            .withUserConfiguration(CreatorOssConfigurationInitializer.class, AiVideoOssConfiguration.class)
            .withPropertyValues("aivideo.oss.enabled=false")
            .run(context -> {
                assertThat(context)
                    .doesNotHaveBean(CreatorOssConfigurationInitializer.class)
                    .doesNotHaveBean(AiVideoOssConfigurationInitializer.class)
                    .doesNotHaveBean(OssClient.class);
                verify(jdbcTemplate, never()).queryForMap(anyString());
                verifyNoInteractions(cache);
            });
    }

    @Test
    void registersTheDatabaseInitializerWhenOssIsEnabled() {
        new ApplicationContextRunner()
            .withBean(JdbcTemplate.class, () -> mock(JdbcTemplate.class))
            .withBean(CreatorOssConfigurationCache.class, () -> mock(CreatorOssConfigurationCache.class))
            .withUserConfiguration(CreatorOssConfigurationInitializer.class, AiVideoOssConfiguration.class)
            .withPropertyValues(
                "aivideo.oss.enabled=true",
                "aivideo.oss.config-key=videoops-agent-dev",
                "aivideo.oss.endpoint=sentinel-endpoint",
                "aivideo.oss.access-key=sentinel-access-key",
                "aivideo.oss.secret-key=sentinel-secret-key",
                "aivideo.oss.bucket-name=sentinel-bucket",
                "aivideo.oss.region=cn-shanghai",
                "aivideo.oss.prefix=videoops-agent/dev",
                "aivideo.oss.access-policy=0")
            .run(context -> assertThat(context)
                .hasSingleBean(CreatorOssConfigurationInitializer.class)
                .hasSingleBean(AiVideoOssConfigurationInitializer.class)
                .hasSingleBean(OssClient.class));
    }
}
