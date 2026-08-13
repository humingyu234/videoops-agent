package org.dromara.aivideo.identity.security;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Rejects insecurely short secrets before the creator token namespace is initialized.
 */
@Tag("dev")
class AppSaTokenPropertiesValidationTest {

    @Test
    void rejectsSecretsShorterThanThirtyTwoUtf8Bytes() {
        AppSaTokenProperties properties = new AppSaTokenProperties();
        properties.setJwtSecret("abcdefghijklmnopqrstuvwxyz");
        properties.setWorkspaceKeySecret("workspace-key-secret-for-unit-test-32-bytes");

        try (var validatorFactory = Validation.buildDefaultValidatorFactory()) {
            assertThat(validatorFactory.getValidator().validate(properties))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("jwtSecretStrong");
        }
    }

    @Test
    void acceptsDeploymentSecretsWithAtLeastThirtyTwoUtf8Bytes() {
        AppSaTokenProperties properties = new AppSaTokenProperties();
        properties.setJwtSecret("creator-jwt-signing-secret-with-at-least-thirty-two-bytes");
        properties.setWorkspaceKeySecret("creator-workspace-secret-with-at-least-thirty-two-bytes");

        try (var validatorFactory = Validation.buildDefaultValidatorFactory()) {
            assertThat(validatorFactory.getValidator().validate(properties)).isEmpty();
        }
    }

    @Test
    void acceptsJwtOnlyConfigurationForTheOperatingSessionRevocationRuntime() {
        AppSaTokenProperties properties = new AppSaTokenProperties();
        properties.setEnabled(false);
        properties.setJwtSecret("operating-session-revocation-jwt-secret-at-least-32-bytes");

        try (var validatorFactory = Validation.buildDefaultValidatorFactory()) {
            assertThat(validatorFactory.getValidator().validate(properties)).isEmpty();
        }
    }
}
