package org.dromara.common.security.filter;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.dromara.common.security.config.properties.SecurityProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证歧义凭据会在认证链执行前被拒绝。
 */
@Tag("dev")
class StrictHeaderCredentialFilterTest {

    private final StrictHeaderCredentialFilter filter = new StrictHeaderCredentialFilter(new SecurityProperties());

    @Test
    void rejectsRepeatedAuthorizationHeaderBeforeFilterChain() throws Exception {
        MockHttpServletRequest request = request();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer first-token");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer second-token");

        assertRejectedBeforeChain(request);
    }

    @Test
    void rejectsCommaJoinedAuthorizationHeaderBeforeFilterChain() throws Exception {
        MockHttpServletRequest request = request();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer first-token, Bearer second-token");

        assertRejectedBeforeChain(request);
    }

    @Test
    void rejectsMalformedWhitespaceInAuthorizationAndClientId() throws Exception {
        MockHttpServletRequest malformedAuthorization = request();
        malformedAuthorization.addHeader(HttpHeaders.AUTHORIZATION, "Bearer  token");
        assertRejectedBeforeChain(malformedAuthorization);

        MockHttpServletRequest malformedClientId = request();
        malformedClientId.addHeader("clientid", "web client");
        assertRejectedBeforeChain(malformedClientId);
    }

    @Test
    void rejectsRepeatedOrCommaJoinedClientIdBeforeFilterChain() throws Exception {
        MockHttpServletRequest repeated = request();
        repeated.addHeader("clientid", "web-client");
        repeated.addHeader("clientid", "other-client");
        assertRejectedBeforeChain(repeated);

        MockHttpServletRequest commaJoined = request();
        commaJoined.addHeader("clientid", "web-client,other-client");
        assertRejectedBeforeChain(commaJoined);
    }

    @Test
    void rejectsCredentialsFromCookieQueryAndFormBeforeFilterChain() throws Exception {
        MockHttpServletRequest cookie = request();
        cookie.addHeader(HttpHeaders.COOKIE, "session=x; token=forbidden");
        assertRejectedBeforeChain(cookie);

        MockHttpServletRequest query = request();
        query.setQueryString("clientid=forbidden");
        query.addParameter("clientid", "forbidden");
        assertRejectedBeforeChain(query);

        MockHttpServletRequest form = request();
        form.setMethod("POST");
        form.setContentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE);
        form.addParameter("authorization", "Bearer forbidden");
        assertRejectedBeforeChain(form);
    }

    @Test
    void allowsAUniqueWellFormedHeaderCredentialToReachTheNextFilter() throws Exception {
        MockHttpServletRequest request = request();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer valid.token-value");
        request.addHeader("clientid", "web-client");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> chainCalled.set(true));

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chainCalled).isTrue();
    }

    @Test
    void skipsAPathExcludedFromTheDefaultSystemSecurityChain() throws Exception {
        SecurityProperties properties = new SecurityProperties();
        properties.setExcludes(new String[]{"/api/**"});
        StrictHeaderCredentialFilter excludedPathFilter = new StrictHeaderCredentialFilter(properties);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer one, Bearer two");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean();

        excludedPathFilter.doFilter(request, response, (servletRequest, servletResponse) -> chainCalled.set(true));

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chainCalled).isTrue();
    }

    @Test
    void allowsActuatorBasicCredentialsToReachTheDedicatedBasicAuthFilter() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Basic c3lzdGVtOnNlY3JldA==");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> chainCalled.set(true));

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chainCalled).isTrue();
    }

    private void assertRejectedBeforeChain(MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> chainCalled.set(true));

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString())
            .contains("\"code\":46132")
            .doesNotContain("first-token", "second-token", "forbidden");
        assertThat(chainCalled).isFalse();
    }

    private MockHttpServletRequest request() {
        return new MockHttpServletRequest("GET", "/system/users");
    }

}
