package org.dromara.test;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ensures the operating-side JWT signing key cannot fall back to a public repository value.
 */
@Tag("dev")
class SaTokenJwtSecretConfigurationTest {

    @Test
    void requiresAnExternalJwtSigningSecret() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"));
        Properties properties = yaml.getObject();

        assertThat(properties).isNotNull();
        assertThat(properties.getProperty("sa-token.jwt-secret-key")).isEqualTo("${SYS_SA_TOKEN_JWT_SECRET}");
    }
}
