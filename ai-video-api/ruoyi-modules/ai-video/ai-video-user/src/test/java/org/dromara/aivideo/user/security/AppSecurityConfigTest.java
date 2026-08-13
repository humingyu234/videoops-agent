package org.dromara.aivideo.user.security;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.SaTokenException;
import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpLogic;
import jakarta.servlet.DispatcherType;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.handler.MappedInterceptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies creator routes cannot accidentally inherit the operating-side Sa-Token namespace.
 */
@Tag("dev")
@ResourceLock("sa-token-manager")
class AppSecurityConfigTest {

    @Test
    void skipsPermissionAnnotationsForServletAsyncRedispatch() throws Exception {
        InspectableInterceptorRegistry registry = new InspectableInterceptorRegistry();
        new AppSecurityConfig(mock(AppAuthenticationInterceptor.class)).addInterceptors(registry);
        MappedInterceptor permissionMapping = (MappedInterceptor) registry.interceptors().get(1);
        StpLogic appLogic = mock(StpLogic.class);
        when(appLogic.getLoginType()).thenReturn("app");
        doThrow(new NotPermissionException("aivideo:studio:generate", "app"))
            .when(appLogic).checkPermissionAnd("aivideo:studio:generate");
        StpLogic previous = installAppLogic(appLogic);
        try {
            MockHttpServletRequest request = new MockHttpServletRequest(
                "GET", "/api/studio/creation-assets/88/content");
            request.setDispatcherType(DispatcherType.ASYNC);
            HandlerMethod handler = new HandlerMethod(new SecuredHandler(),
                SecuredHandler.class.getDeclaredMethod("generate"));

            assertThat(permissionMapping.getInterceptor().preHandle(
                request, new MockHttpServletResponse(), handler)).isTrue();
            verify(appLogic, never()).checkPermissionAnd("aivideo:studio:generate");
        } finally {
            restoreAppLogic(previous);
        }
    }

    @Test
    void registersCreatorAuthenticationBeforeAndExecutesAppPermissionAnnotations() throws Exception {
        AppAuthenticationInterceptor creatorInterceptor = mock(AppAuthenticationInterceptor.class);
        InspectableInterceptorRegistry registry = new InspectableInterceptorRegistry();

        new AppSecurityConfig(creatorInterceptor).addInterceptors(registry);

        assertThat(registry.interceptors()).hasSize(2);
        assertThat(registry.interceptors().getFirst())
            .isInstanceOfSatisfying(MappedInterceptor.class,
                mappedInterceptor -> assertThat(mappedInterceptor.getInterceptor()).isSameAs(creatorInterceptor));
        assertThat(registry.interceptors().get(1))
            .isInstanceOfSatisfying(MappedInterceptor.class,
                mappedInterceptor -> assertThat(mappedInterceptor.getInterceptor())
                    .isInstanceOfSatisfying(SaInterceptor.class,
                        interceptor -> assertThat(interceptor.isAnnotation).isTrue()));

        MappedInterceptor permissionMapping = (MappedInterceptor) registry.interceptors().get(1);
        StpLogic appLogic = mock(StpLogic.class);
        when(appLogic.getLoginType()).thenReturn("app");
        doThrow(new NotPermissionException("aivideo:studio:generate", "app"))
            .when(appLogic).checkPermissionAnd("aivideo:studio:generate");
        StpLogic previous = installAppLogic(appLogic);
        try {
            HandlerMethod handler = new HandlerMethod(new SecuredHandler(),
                SecuredHandler.class.getDeclaredMethod("generate"));

            assertThatThrownBy(() -> permissionMapping.getInterceptor().preHandle(
                new MockHttpServletRequest("POST", "/api/studio/scripts/generate"),
                new MockHttpServletResponse(), handler))
                .isInstanceOf(NotPermissionException.class);
            verify(appLogic).checkPermissionAnd("aivideo:studio:generate");
        } finally {
            restoreAppLogic(previous);
        }
    }

    private static StpLogic installAppLogic(StpLogic logic) {
        StpLogic previous = null;
        try {
            previous = SaManager.getStpLogic("app", false);
        } catch (SaTokenException ignored) {
            // 当前定向测试进程尚未注册 app logic。
        }
        SaManager.putStpLogic(logic);
        return previous;
    }

    private static void restoreAppLogic(StpLogic previous) {
        SaManager.removeStpLogic("app");
        if (previous != null) {
            SaManager.putStpLogic(previous);
        }
    }

    private static final class SecuredHandler {

        @SaCheckPermission(value = "aivideo:studio:generate", type = "app")
        public void generate() {
        }
    }

    private static final class InspectableInterceptorRegistry extends InterceptorRegistry {

        private List<Object> interceptors() {
            return super.getInterceptors();
        }
    }
}
