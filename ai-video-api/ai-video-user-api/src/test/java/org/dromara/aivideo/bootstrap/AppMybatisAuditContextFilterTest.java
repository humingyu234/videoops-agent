package org.dromara.aivideo.bootstrap;

import cn.dev33.satoken.filter.SaTokenContextFilterForJakartaServlet;
import jakarta.servlet.FilterChain;
import org.dromara.aivideo.identity.security.AppLoginHelper;
import org.dromara.aivideo.identity.security.AppLoginUser;
import org.dromara.common.mybatis.audit.AuditFillContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.springframework.core.annotation.Order;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("dev")
class AppMybatisAuditContextFilterTest {

    @Test
    void runsAfterTheSaTokenContextFilter() {
        Order contextOrder = SaTokenContextFilterForJakartaServlet.class.getAnnotation(Order.class);
        Order auditOrder = AppMybatisAuditContextFilter.class.getAnnotation(Order.class);

        assertThat(contextOrder).isNotNull();
        assertThat(auditOrder).isNotNull();
        assertThat(auditOrder.value()).isEqualTo(contextOrder.value() + 1);
    }

    @Test
    void authenticatedCreatorRequestBindsAndCleansActor() throws Exception {
        AppLoginHelper loginHelper = mock(AppLoginHelper.class);
        AppLoginUser loginUser = mock(AppLoginUser.class);
        when(loginHelper.isLogin()).thenReturn(true);
        when(loginHelper.getLoginUser()).thenReturn(loginUser);
        when(loginUser.userId()).thenReturn(73L);

        AppMybatisAuditContextFilter filter = new AppMybatisAuditContextFilter(loginHelper);
        AtomicReference<Long> actor = new AtomicReference<>();
        FilterChain chain = (request, response) -> actor.set(AuditFillContext.currentActorId());
        filter.doFilter(new MockHttpServletRequest("GET", "/api/studio/projects"),
            new MockHttpServletResponse(), chain);

        assertThat(actor).hasValue(73L);
        assertThat(AuditFillContext.isBound()).isFalse();
    }
}
