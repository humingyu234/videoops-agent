package org.dromara.common.satoken.config;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the default Sa-Token JWT secret cannot be reduced to a short repository value.
 */
@Tag("dev")
class SaTokenSecretPropertiesTest {

    @Test
    void rejectsBlankAndShortSigningSecrets() {
        SaTokenSecretProperties properties = new SaTokenSecretProperties();
        properties.setJwtSecretKey("abcdefghijklmnopqrstuvwxyz");

        try (var validatorFactory = Validation.buildDefaultValidatorFactory()) {
            assertThat(validatorFactory.getValidator().validate(properties))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("jwtSecretKeyStrong");
        }
    }

    @Test
    void acceptsADeploymentSecretWithAtLeastThirtyTwoUtf8Bytes() {
        SaTokenSecretProperties properties = new SaTokenSecretProperties();
        properties.setJwtSecretKey("operator-jwt-signing-secret-with-at-least-thirty-two-bytes");

        try (var validatorFactory = Validation.buildDefaultValidatorFactory()) {
            assertThat(validatorFactory.getValidator().validate(properties)).isEmpty();
        }
    }
}
