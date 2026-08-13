package org.dromara.aivideo.user.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that creator API credentials are rejected before a handler can inspect identity state.
 */
@Tag("dev")
class StrictCredentialIngressTest {

    @ParameterizedTest
    @MethodSource("invalidCredentials")
    void rejectsAmbiguousCredentialsBeforeTheRequestReachesApplication(
        MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean();

        invoke(request, response, chainCalled);

        assertThat(chainCalled).isFalse();
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).contains("\"code\":46132");
        assertThat(request.getAttribute(StrictCredentialHeaders.REQUEST_ATTRIBUTE)).isNull();
    }

    @Test
    void rejectsProtectedCreatorRequestWithoutExactlyOneAuthorizationAndClientId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/me");
        request.addHeader("clientid", "desktop");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean();

        invoke(request, response, chainCalled);

        assertThat(chainCalled).isFalse();
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).contains("\"code\":46132");
    }

    @Test
    void rejectsAnUnregisteredCreatorPathBeforeItCanReachDispatcherWithoutAuthorization() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/not-registered-yet");
        request.addHeader("clientid", "desktop");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean();

        invoke(request, response, chainCalled);

        assertThat(chainCalled).isFalse();
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).contains("\"code\":46132");
    }

    @ParameterizedTest
    @MethodSource("encodedCreatorPaths")
    void treatsMatrixAndPercentEncodedCreatorPathsAsProtectedApiRoutes(String path) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean();

        invoke(request, response, chainCalled);

        assertThat(chainCalled).isFalse();
        assertThat(response.getStatus()).isEqualTo(400);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "/api/auth/login",
        "/api/auth/sms-logins",
        "/api/auth/email-logins",
        "/api/auth/social-logins",
        "/api/auth/mini-program-logins",
        "/api/auth/verification-codes",
        "/api/auth/password-resets"
    })
    void acceptsOnlyAHeaderBasedCredentialForPublicCreatorAuthentication(String path) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.addHeader("clientid", "desktop");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean();

        invoke(request, response, chainCalled);

        assertThat(chainCalled).isTrue();
        assertThat(request.getAttribute(StrictCredentialHeaders.REQUEST_ATTRIBUTE))
            .isInstanceOf(StrictCredentialHeaders.class);
    }

    @Test
    void keepsUnimplementedPublicAuthenticationEndpointsBehindTheCredentialGate() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/register");
        request.addHeader("clientid", "desktop");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean();

        invoke(request, response, chainCalled);

        assertThat(chainCalled).isFalse();
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).contains("\"code\":46132");
    }

    @Test
    void letsAStandardCorsPreflightReachTheDownstreamCorsFilterWithoutCredentials() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/auth/me");
        request.addHeader("Origin", "https://studio.example.test");
        request.addHeader("Access-Control-Request-Method", "GET");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean();

        invoke(request, response, chainCalled);

        assertThat(chainCalled).isTrue();
        assertThat(request.getAttribute(StrictCredentialHeaders.REQUEST_ATTRIBUTE)).isNull();
    }

    @ParameterizedTest
    @MethodSource("nonPreflightOptionsAndBusinessRequests")
    void keepsNonPreflightOptionsAndBusinessRequestsBehindTheCredentialGate(
        MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean();

        invoke(request, response, chainCalled);

        assertThat(chainCalled).isFalse();
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).contains("\"code\":46132");
    }

    private static Stream<Arguments> invalidCredentials() {
        return Stream.of(
            Arguments.of(repeatedAuthorizationRequest()),
            Arguments.of(combinedAuthorizationRequest()),
            Arguments.of(cookieCredentialRequest()),
            Arguments.of(queryCredentialRequest()),
            Arguments.of(repeatedClientIdRequest())
        );
    }

    private static Stream<String> encodedCreatorPaths() {
        return Stream.of("/api;v=1/auth/me", "/%61pi/auth/me");
    }

    private static Stream<Arguments> nonPreflightOptionsAndBusinessRequests() {
        MockHttpServletRequest normalOptions = new MockHttpServletRequest("OPTIONS", "/api/auth/me");

        MockHttpServletRequest originOnlyOptions = new MockHttpServletRequest("OPTIONS", "/api/auth/me");
        originOnlyOptions.addHeader("Origin", "https://studio.example.test");

        MockHttpServletRequest requestedMethodOnlyOptions = new MockHttpServletRequest("OPTIONS", "/api/auth/me");
        requestedMethodOnlyOptions.addHeader("Access-Control-Request-Method", "GET");

        MockHttpServletRequest businessPost = new MockHttpServletRequest("POST", "/api/auth/me");
        businessPost.addHeader("Origin", "https://studio.example.test");
        businessPost.addHeader("Access-Control-Request-Method", "GET");

        MockHttpServletRequest credentialBearingOptions = new MockHttpServletRequest("OPTIONS", "/api/auth/me");
        credentialBearingOptions.addHeader("Origin", "https://studio.example.test");
        credentialBearingOptions.addHeader("Access-Control-Request-Method", "GET");
        credentialBearingOptions.addHeader("Authorization", "Bearer app-token");

        MockHttpServletRequest clientBoundOptions = new MockHttpServletRequest("OPTIONS", "/api/auth/me");
        clientBoundOptions.addHeader("Origin", "https://studio.example.test");
        clientBoundOptions.addHeader("Access-Control-Request-Method", "GET");
        clientBoundOptions.addHeader("clientid", "desktop");

        MockHttpServletRequest queryCredentialOptions = new MockHttpServletRequest("OPTIONS", "/api/auth/me");
        queryCredentialOptions.addHeader("Origin", "https://studio.example.test");
        queryCredentialOptions.addHeader("Access-Control-Request-Method", "GET");
        queryCredentialOptions.setParameter("token", "app-token");

        return Stream.of(
            Arguments.of(normalOptions),
            Arguments.of(originOnlyOptions),
            Arguments.of(requestedMethodOnlyOptions),
            Arguments.of(businessPost),
            Arguments.of(credentialBearingOptions),
            Arguments.of(clientBoundOptions),
            Arguments.of(queryCredentialOptions)
        );
    }

    private static MockHttpServletRequest repeatedAuthorizationRequest() {
        MockHttpServletRequest request = protectedRequest();
        request.addHeader("Authorization", "Bearer sys-token");
        return request;
    }

    private static MockHttpServletRequest combinedAuthorizationRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/me");
        request.addHeader("Authorization", "Bearer app-token,Bearer sys-token");
        request.addHeader("clientid", "desktop");
        return request;
    }

    private static MockHttpServletRequest cookieCredentialRequest() {
        MockHttpServletRequest request = protectedRequest();
        request.setCookies(new Cookie("Authorization", "sys-token"));
        return request;
    }

    private static MockHttpServletRequest queryCredentialRequest() {
        MockHttpServletRequest request = protectedRequest();
        request.setParameter("Authorization", "Bearer sys-token");
        return request;
    }

    private static MockHttpServletRequest repeatedClientIdRequest() {
        MockHttpServletRequest request = protectedRequest();
        request.addHeader("clientid", "admin-client");
        return request;
    }

    private static MockHttpServletRequest protectedRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/me");
        request.addHeader("Authorization", "Bearer app-token");
        request.addHeader("clientid", "desktop");
        return request;
    }

    private static void invoke(MockHttpServletRequest request, MockHttpServletResponse response,
                               AtomicBoolean chainCalled) throws Exception {
        FilterChain chain = (ignoredRequest, ignoredResponse) -> chainCalled.set(true);
        new AppCredentialIngressFilter().doFilter(request, response, chain);
    }
}
