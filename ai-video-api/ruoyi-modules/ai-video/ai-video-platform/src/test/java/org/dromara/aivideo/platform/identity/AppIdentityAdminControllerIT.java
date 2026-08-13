package org.dromara.aivideo.platform.identity;

import cn.dev33.satoken.annotation.SaCheckPermission;
import org.dromara.aivideo.platform.identity.domain.vo.AppIdentityAdminVos.AppUserAdminVo;
import org.dromara.aivideo.platform.identity.domain.vo.AppIdentityAdminVos.AppUserDetailAdminVo;
import org.dromara.common.core.domain.R;
import org.dromara.common.log.annotation.Log;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 运营端创作身份管理 Controller 的静态 HTTP 契约。
 *
 * <p>该测试不启动用户端上下文，确保平台适配层只声明运营端管理入口，
 * 并且每个入口都使用默认 sys 权限体系而非 app 权限体系。</p>
 */
@Tag("dev")
class AppIdentityAdminControllerIT {

    @Test
    void exposesOnlyApprovedAdminRoutesWithDefaultSysPermissions() throws Exception {
        Set<Route> actualRoutes = new LinkedHashSet<>();
        for (String controllerClassName : List.of(
            "org.dromara.aivideo.platform.identity.controller.AppUserAdminController",
            "org.dromara.aivideo.platform.identity.controller.AppRoleAdminController",
            "org.dromara.aivideo.platform.identity.controller.AppAuthClientAdminController",
            "org.dromara.aivideo.platform.identity.controller.AppSessionAdminController",
            "org.dromara.aivideo.platform.identity.controller.AppSecurityLogAdminController")) {
            actualRoutes.addAll(routesOf(Class.forName(controllerClassName)));
        }

        assertThat(actualRoutes).containsExactlyInAnyOrderElementsOf(Set.of(
            route(RequestMethod.GET, "/api/admin/app-users", "aivideo:app-user:query"),
            route(RequestMethod.POST, "/api/admin/app-users", "aivideo:app-user:add"),
            route(RequestMethod.GET, "/api/admin/app-users/{id}", "aivideo:app-user:query"),
            route(RequestMethod.PUT, "/api/admin/app-users/{id}", "aivideo:app-user:edit"),
            route(RequestMethod.POST, "/api/admin/app-users/{id}/status-changes", "aivideo:app-user:edit"),
            route(RequestMethod.POST, "/api/admin/app-users/{id}/password-resets", "aivideo:app-user:reset-password"),
            route(RequestMethod.POST, "/api/admin/app-users/{id}/kickouts", "aivideo:app-user:kickout"),
            route(RequestMethod.PUT, "/api/admin/app-users/{id}/roles", "aivideo:app-user:assign-role"),
            route(RequestMethod.GET, "/api/admin/app-roles", "aivideo:app-role:query"),
            route(RequestMethod.POST, "/api/admin/app-roles", "aivideo:app-role:edit"),
            route(RequestMethod.PUT, "/api/admin/app-roles/{id}", "aivideo:app-role:edit"),
            route(RequestMethod.PUT, "/api/admin/app-roles/{id}/permissions", "aivideo:app-role:assign-permission"),
            route(RequestMethod.GET, "/api/admin/app-permissions", "aivideo:app-role:query"),
            route(RequestMethod.GET, "/api/admin/app-auth-clients", "aivideo:app-auth-client:query"),
            route(RequestMethod.POST, "/api/admin/app-auth-clients", "aivideo:app-auth-client:edit"),
            route(RequestMethod.PUT, "/api/admin/app-auth-clients/{id}", "aivideo:app-auth-client:edit"),
            route(RequestMethod.POST, "/api/admin/app-auth-clients/{id}/secret-rotations", "aivideo:app-auth-client:rotate-secret"),
            route(RequestMethod.GET, "/api/admin/app-sessions", "aivideo:app-session:query"),
            route(RequestMethod.DELETE, "/api/admin/app-sessions/{id}", "aivideo:app-session:kickout"),
            route(RequestMethod.GET, "/api/admin/app-login-logs", "aivideo:app-login-log:query"),
            route(RequestMethod.GET, "/api/admin/app-security-audits", "aivideo:app-security-audit:query")
        ));

        assertThat(actualRoutes)
            .noneMatch(route -> route.path().contains("impersonation") || route.path().contains("token"));
    }

    @Test
    void doesNotPersistPayloadsForOneTimePasswordsOrClientSecrets() throws Exception {
        assertSensitiveHandlerDoesNotSavePayload(
            "org.dromara.aivideo.platform.identity.controller.AppUserAdminController", RequestMethod.POST,
            "/api/admin/app-users");
        assertSensitiveHandlerDoesNotSavePayload(
            "org.dromara.aivideo.platform.identity.controller.AppUserAdminController", RequestMethod.PUT,
            "/api/admin/app-users/{id}");
        assertSensitiveHandlerDoesNotSavePayload(
            "org.dromara.aivideo.platform.identity.controller.AppUserAdminController", RequestMethod.POST,
            "/api/admin/app-users/{id}/password-resets");
        assertSensitiveHandlerDoesNotSavePayload(
            "org.dromara.aivideo.platform.identity.controller.AppAuthClientAdminController", RequestMethod.POST,
            "/api/admin/app-auth-clients");
        assertSensitiveHandlerDoesNotSavePayload(
            "org.dromara.aivideo.platform.identity.controller.AppAuthClientAdminController", RequestMethod.POST,
            "/api/admin/app-auth-clients/{id}/secret-rotations");
    }

    @Test
    void serializesGetUserDetailWithMaskedContactsOnly() {
        AppUserAdminVo user = new AppUserAdminVo(
            "1001", "creator", "创作者", "138****00", "c***@example.com", "active", false,
            "2", "5", "3", LocalDateTime.of(2026, 7, 30, 12, 0),
            LocalDateTime.of(2026, 7, 30, 12, 1));
        String json = JsonMapper.builder().build().writeValueAsString(
            R.ok(new AppUserDetailAdminVo(user, List.of(), List.of())));

        assertThat(json)
            .contains("\"maskedPhone\":\"138****00\"", "\"maskedEmail\":\"c***@example.com\"")
            .doesNotContain("\"phoneNormalized\"", "\"emailNormalized\"", "\"phone\"", "\"email\"",
                "13800000000", "creator@example.com");
    }

    private static Set<Route> routesOf(Class<?> controllerClass) {
        Set<Route> routes = new LinkedHashSet<>();
        for (Method method : controllerClass.getDeclaredMethods()) {
            RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
            if (mapping == null) {
                continue;
            }
            SaCheckPermission permission = AnnotatedElementUtils.findMergedAnnotation(method, SaCheckPermission.class);
            assertThat(permission)
                .as("%s 必须声明运营端权限", method)
                .isNotNull();
            assertThat(permission.type())
                .as("%s 不得切换到 app 权限体系", method)
                .isEmpty();
            assertThat(permission.value())
                .as("%s 必须只声明一个精确权限", method)
                .hasSize(1);
            for (RequestMethod requestMethod : mapping.method()) {
                for (String path : mapping.path()) {
                    routes.add(route(requestMethod, path, permission.value()[0]));
                }
            }
        }
        return routes;
    }

    private static Route route(RequestMethod method, String path, String permission) {
        return new Route(method, path, permission);
    }

    private static void assertSensitiveHandlerDoesNotSavePayload(String controllerClassName, RequestMethod requestMethod,
                                                                 String path)
        throws ClassNotFoundException {
        Class<?> controller = Class.forName(controllerClassName);
        Method method = java.util.Arrays.stream(controller.getDeclaredMethods())
            .filter(candidate -> {
                RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(candidate, RequestMapping.class);
                return mapping != null
                    && java.util.Arrays.asList(mapping.path()).contains(path)
                    && java.util.Arrays.asList(mapping.method()).contains(requestMethod);
            })
            .findFirst()
            .orElseThrow();
        Log log = AnnotatedElementUtils.findMergedAnnotation(method, Log.class);
        assertThat(log).as("%s 必须关闭请求和响应正文日志", method).isNotNull();
        assertThat(log.isSaveRequestData()).isFalse();
        assertThat(log.isSaveResponseData()).isFalse();
    }

    private record Route(RequestMethod method, String path, String permission) {
    }
}
