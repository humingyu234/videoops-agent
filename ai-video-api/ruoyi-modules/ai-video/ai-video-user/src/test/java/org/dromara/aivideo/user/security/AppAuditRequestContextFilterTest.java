package org.dromara.aivideo.user.security;

import org.dromara.aivideo.identity.security.AppAuditRequestContext;
import org.dromara.aivideo.identity.security.AppAuditRequestContextHolder;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("dev")
class AppAuditRequestContextFilterTest {

    @Test
    void bindsAServerGeneratedTraceIdAndDirectPeerAddressOnlyForCreatorRequests() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setRemoteAddr("203.0.113.10");
        request.addHeader("X-Forwarded-For", "198.51.100.1");
        AtomicReference<AppAuditRequestContext> captured = new AtomicReference<>();

        new AppAuditRequestContextFilter().doFilter(request, new MockHttpServletResponse(),
            (ignoredRequest, ignoredResponse) -> captured.set(AppAuditRequestContextHolder.current()));

        assertThat(captured.get().requestId()).matches("[0-9a-f]{32}");
        assertThat(captured.get().ipAddress()).isEqualTo("203.0.113.10");
        assertThatThrownBy(AppAuditRequestContextHolder::current).isInstanceOf(ServiceException.class);
    }
}
