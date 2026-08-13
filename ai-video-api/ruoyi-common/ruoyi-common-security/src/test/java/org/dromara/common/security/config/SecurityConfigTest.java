package org.dromara.common.security.config;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.util.SaResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies default client IP policy cannot trust request-supplied forwarding headers.
 */
@Tag("dev")
class SecurityConfigTest {

    @Test
    void resolvesClientWhitelistIpFromTheDirectPeerInsteadOfForwardedHeaders() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.99");
        request.addHeader("X-Forwarded-For", "127.0.0.1");

        assertThat(SecurityConfig.directPeerIp(request)).isEqualTo("203.0.113.99");
    }

    @Test
    void rejectsBlankOrUnresolvedActuatorBasicCredentials() {
        assertThatThrownBy(() -> SecurityConfig.requiredActuatorBasicCredential(
            "spring.boot.admin.client.username", null))
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> SecurityConfig.requiredActuatorBasicCredential(
            "spring.boot.admin.client.password", "${ACTUATOR_BASIC_PASSWORD}"))
            .isInstanceOf(IllegalStateException.class);
        assertThat(SecurityConfig.requiredActuatorBasicCredential(
            "spring.boot.admin.client.username", "deployment-only-username"))
            .isEqualTo("deployment-only-username");
    }

    @Test
    void createsClientIdMismatchExceptionsWithoutAttachingTheRawToken() {
        NotLoginException exception = SecurityConfig.clientIdMismatchException("login");

        assertThat(exception.getMessage()).isEqualTo("客户端ID与Token不匹配");
    }

    @Test
    void returnsAStableActuatorAuthenticationFailureEnvelope() {
        SaResult response = SecurityConfig.actuatorUnauthorizedResult();

        assertThat(response.getCode()).isEqualTo(401);
        assertThat(response.getMsg()).isEqualTo("未授权访问");
    }
}
