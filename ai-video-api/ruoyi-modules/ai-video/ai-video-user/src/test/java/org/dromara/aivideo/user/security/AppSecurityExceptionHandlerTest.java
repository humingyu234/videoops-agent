package org.dromara.aivideo.user.security;

import cn.dev33.satoken.exception.NotLoginException;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies creator authentication failures use stable status codes without leaking policy details.
 */
@Tag("dev")
class AppSecurityExceptionHandlerTest {

    private final AppSecurityExceptionHandler handler = new AppSecurityExceptionHandler();

    @Test
    void doesNotInterceptEveryServiceExceptionFromFutureCreatorBusinessEndpoints() {
        assertThat(Arrays.stream(AppSecurityExceptionHandler.class.getDeclaredMethods())
            .flatMap(method -> Arrays.stream(method.getParameterTypes()))
            .anyMatch(ServiceException.class::equals))
            .isFalse();
    }

    @Test
    void mapsMissingAppLoginToUnauthorizedWithoutTokenDetails() {
        ResponseEntity<R<Void>> response = handler.handleNotLoginException(
            NotLoginException.newInstance("app", NotLoginException.NOT_TOKEN, "internal token detail", "secret"));

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(response.getBody()).extracting(R::getCode, R::getMsg)
            .containsExactly(401, "登录状态异常，请重新登录");
    }

    @Test
    void mapsClientPolicyFailureToStableUnauthorizedEnvelope() {
        ResponseEntity<R<Void>> response = handler.handleAppSecurityException(
            new AppSecurityException("IP whitelist does not match", AppAuthErrorCodes.APP_AUTH_CLIENT_UNAVAILABLE));

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(response.getBody()).extracting(R::getCode, R::getMsg)
            .containsExactly(AppAuthErrorCodes.APP_AUTH_CLIENT_UNAVAILABLE, "创作端认证客户端不可用");
    }

    @Test
    void mapsStaleSessionAndMalformedCredentialToTheirExplicitHttpStatus() {
        ResponseEntity<R<Void>> stale = handler.handleAppSecurityException(
            new AppSecurityException("stale", AppAuthErrorCodes.APP_SESSION_REVISION_STALE));
        ResponseEntity<R<Void>> malformed = handler.handleAppSecurityException(
            new AppSecurityException("malformed", AppAuthErrorCodes.MULTIPLE_AUTH_CREDENTIALS_REJECTED));

        assertThat(stale.getStatusCode().value()).isEqualTo(401);
        assertThat(stale.getBody()).extracting(R::getCode, R::getMsg)
            .containsExactly(AppAuthErrorCodes.APP_SESSION_REVISION_STALE, "登录状态异常，请重新登录");
        assertThat(malformed.getStatusCode().value()).isEqualTo(400);
        assertThat(malformed.getBody()).extracting(R::getCode, R::getMsg)
            .containsExactly(AppAuthErrorCodes.MULTIPLE_AUTH_CREDENTIALS_REJECTED, "认证凭据格式不合法");
    }
}
