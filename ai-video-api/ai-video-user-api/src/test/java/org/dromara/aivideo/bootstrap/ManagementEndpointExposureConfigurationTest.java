package org.dromara.aivideo.bootstrap;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ensures the creator-facing starter never publishes diagnostics that expose application internals.
 */
@Tag("dev")
class ManagementEndpointExposureConfigurationTest {

    @Test
    void exposesOnlyMinimalAnonymousManagementEndpoints() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"));
        Properties properties = yaml.getObject();

        assertThat(properties).isNotNull();
        assertThat(properties.getProperty("sa-token.jwt-secret-key")).isEqualTo("${SYS_SA_TOKEN_JWT_SECRET}");
        assertThat(properties.getProperty("management.endpoints.enabled-by-default")).isEqualTo("false");
        assertThat(properties.getProperty("management.endpoints.web.exposure.include")).isEqualTo("health,info");
        assertThat(properties.getProperty("management.endpoint.health.enabled")).isEqualTo("true");
        assertThat(properties.getProperty("management.endpoint.info.enabled")).isEqualTo("true");
        assertThat(properties.getProperty("management.endpoint.health.show-details")).isEqualTo("never");
        assertThat(properties.getProperty("spring.ai.mcp.server.enabled")).isEqualTo("false");
        assertThat(properties.getProperty("springdoc.api-docs.enabled")).isEqualTo("false");
        assertThat(properties.getProperty("springdoc.swagger-ui.enabled")).isEqualTo("false");
        assertThat(properties.getProperty("message.enabled")).isEqualTo("false");
        assertThat(properties.getProperty("web.cors.allow-credentials")).isEqualTo("false");
        assertThat(properties.getProperty("web.cors.allowed-origin-patterns[0]"))
            .contains("APP_WEB_CORS_ALLOWED_ORIGIN")
            .doesNotContain("*");
        assertThat(properties.getProperty("web.cors.allowed-headers[0]")).isEqualTo("Authorization");
        assertThat(properties.getProperty("web.cors.allowed-methods[0]")).isEqualTo("GET");
    }
}
