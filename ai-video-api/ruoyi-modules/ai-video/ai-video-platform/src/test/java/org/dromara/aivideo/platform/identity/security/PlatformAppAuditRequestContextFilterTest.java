package org.dromara.aivideo.platform.identity.security;

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

/**
 * 运营端创作身份管理请求的审计上下文边界。
 */
@Tag("dev")
class PlatformAppAuditRequestContextFilterTest {

    private final PlatformAppAuditRequestContextFilter filter = new PlatformAppAuditRequestContextFilter();

    @Test
    void bindsTrustedContextOnlyForAppManagementRoutesAndCleansItAfterRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/admin/app-users/100/password-resets");
        request.setRemoteAddr("[127.0.0.1]");
        AtomicReference<AppAuditRequestContext> seen = new AtomicReference<>();

        filter.doFilter(request, new MockHttpServletResponse(), (ignoredRequest, ignoredResponse) ->
            seen.set(AppAuditRequestContextHolder.current()));

        assertThat(seen.get()).isNotNull();
        assertThat(seen.get().requestId()).matches("[a-f0-9]{32}");
        assertThat(seen.get().ipAddress()).isEqualTo("127.0.0.1");
        assertThatThrownBy(AppAuditRequestContextHolder::current).isInstanceOf(ServiceException.class);
    }

    @Test
    void doesNotBindContextForNonAppManagementRoutes() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/admin/users");

        filter.doFilter(request, new MockHttpServletResponse(), (ignoredRequest, ignoredResponse) ->
            assertThatThrownBy(AppAuditRequestContextHolder::current).isInstanceOf(ServiceException.class));
    }
}
