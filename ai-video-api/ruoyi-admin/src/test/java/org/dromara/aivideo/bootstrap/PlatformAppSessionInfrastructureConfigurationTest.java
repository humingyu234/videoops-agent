package org.dromara.aivideo.bootstrap;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The operating starter needs the app Sa-Token namespace only to revoke app sessions.
 * It must not enable the creator-login switch or load the unrelated workspace HMAC secret.
 */
@Tag("dev")
class PlatformAppSessionInfrastructureConfigurationTest {

    @Test
    void enablesOnlyTheAppSessionRevocationRuntimeWithItsExternalJwtSecret() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"));
        Properties properties = yaml.getObject();

        assertThat(properties).isNotNull();
        assertThat(properties.getProperty("app.security.session-revocation.enabled"))
            .isEqualTo("true");
        assertThat(properties.getProperty("app.security.token.enabled"))
            .isEqualTo("false");
        assertThat(properties.getProperty("app.security.token.jwt-secret"))
            .isEqualTo("${APP_SA_TOKEN_JWT_SECRET}");
        assertThat(properties.getProperty("app.security.token.workspace-key-secret"))
            .isNull();
    }

    @Test
    void usesPortableDevelopmentPaths() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application-dev.yml"));
        Properties properties = yaml.getObject();

        assertThat(properties).isNotNull();
        assertThat(properties.getProperty("digital-human.media-root"))
            .isEqualTo("${AI_VIDEO_DH_MEDIA_ROOT:${user.dir}/.runtime/videoops-agent/digital-human-media}");
        assertThat(properties.getProperty("digital-human.index-tts2.ca-certificate"))
            .isEqualTo("${DEMO_INDEXTTS_CA_CERTIFICATE:}");
    }

    @Test
    void usesTheIndependentDevelopmentDataAndStorageNamespaces() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application-dev.yml"));
        Properties properties = yaml.getObject();

        assertThat(properties).isNotNull();
        assertThat(properties).doesNotContainKey("spring.config.import");
        assertThat(properties.getProperty("spring.datasource.dynamic.datasource.master.url"))
            .contains("/videoops_agent_dev?")
            .doesNotContain("/ai_video?");
        assertThat(properties.getProperty("spring.datasource.dynamic.datasource.master.username"))
            .isEqualTo("videoops_agent");
        assertThat(properties.getProperty("spring.data.redis.database")).isEqualTo("14");
        assertThat(properties.getProperty("redisson.keyPrefix")).isEqualTo("videoops-agent:dev");
        assertThat(properties.getProperty("redisson.singleServerConfig.clientName"))
            .isEqualTo("VideoOps-Agent-Dev");
        assertThat(properties.getProperty("sa-token.redis-key-prefix")).isEqualTo("videoops-agent:dev:");
        assertThat(properties.getProperty("aivideo.oss.enabled"))
            .isEqualTo("${VIDEOOPS_AIVIDEO_OSS_ENABLED:false}");
        assertThat(properties.getProperty("aivideo.oss.prefix")).isEqualTo("videoops-agent/dev");
        assertThat(properties.getProperty("mybatis-plus.sql-log.enabled")).isEqualTo("true");
    }
}
