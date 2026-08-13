package org.dromara.aivideo.bootstrap;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.config.SaTokenConfig;
import cn.dev33.satoken.context.mock.SaTokenContextMockUtil;
import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.dao.SaTokenDaoDefaultImpl;
import cn.dev33.satoken.filter.SaTokenContextFilterForJakartaServlet;
import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.jwt.StpLogicJwtForSimple;
import cn.dev33.satoken.stp.StpInterface;
import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import org.dromara.aivideo.platform.identity.controller.AppUserAdminController;
import org.dromara.aivideo.platform.identity.service.IAppIdentityAdminService;
import org.dromara.aivideo.identity.security.AppSaTokenProperties;
import org.dromara.aivideo.identity.security.AppStpLogicRegistrar;
import org.dromara.common.security.config.properties.SecurityProperties;
import org.dromara.common.security.filter.StrictHeaderCredentialFilter;
import org.dromara.common.satoken.handler.SaTokenExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.mock;

/**
 * HTTP security matrix for the operating-side app identity management entry point.
 */
@Tag("dev")
@ResourceLock("sa-token-manager")
class PlatformAppIdentityHttpSecurityTest {

    private final Map<String, List<String>> permissionsByLoginId = new ConcurrentHashMap<>();

    private SaTokenDao previousSaTokenDao;
    private SaTokenConfig previousSaTokenConfig;
    private StpInterface previousStpInterface;
    private StpLogic previousSystemLogic;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        previousSaTokenDao = SaManager.getSaTokenDao();
        previousSaTokenConfig = SaManager.getConfig();
        previousStpInterface = SaManager.getStpInterface();
        previousSystemLogic = SaManager.getStpLogic("login");
        SaManager.setSaTokenDao(new SaTokenDaoDefaultImpl());
        SaManager.setStpInterface(new TestStpInterface(permissionsByLoginId));
        installSystemLoginRuntime();
        installAppSessionRevocationRuntime();
        SaTokenContextMockUtil.setMockContext();

        IAppIdentityAdminService adminService = mock(IAppIdentityAdminService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new AppUserAdminController(adminService))
            .addInterceptors(new SaInterceptor(handler -> StpUtil.checkLogin()))
            .addFilters(new StrictHeaderCredentialFilter(new SecurityProperties()),
                new SaTokenContextFilterForJakartaServlet())
            .setControllerAdvice(new SaTokenExceptionHandler())
            .build();
    }

    @AfterEach
    void tearDown() {
        SaTokenContextMockUtil.clearContext();
        SaManager.removeStpLogic("app");
        SaManager.removeStpLogic("login");
        if (previousSystemLogic != null) {
            SaManager.putStpLogic(previousSystemLogic);
        }
        SaManager.setStpInterface(previousStpInterface);
        SaManager.setSaTokenDao(previousSaTokenDao);
        SaManager.setConfig(previousSaTokenConfig);
        permissionsByLoginId.clear();
    }

    @Test
    void rejectsRequestsWithoutASystemToken() throws Exception {
        clearTokenCreationContext();

        mockMvc.perform(get("/api/admin/app-users"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void rejectsAnAppTokenAtTheOperatingEntryPoint() throws Exception {
        String appToken = issueAppToken("creator-1001");
        clearTokenCreationContext();

        mockMvc.perform(get("/api/admin/app-users").header("Authorization", "Bearer " + appToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void rejectsASystemTokenWithoutTheExactOperatingPermission() throws Exception {
        String systemToken = issueSystemToken("sys-1001", List.of());
        clearTokenCreationContext();

        mockMvc.perform(get("/api/admin/app-users").header("Authorization", "Bearer " + systemToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void acceptsOnlyASystemTokenWithTheExactOperatingPermission() throws Exception {
        String systemToken = issueSystemToken("sys-1002", List.of("aivideo:app-user:query"));
        clearTokenCreationContext();

        mockMvc.perform(get("/api/admin/app-users").header("Authorization", "Bearer " + systemToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));
    }

    private String issueSystemToken(String loginId, List<String> permissions) {
        permissionsByLoginId.put(loginId, List.copyOf(permissions));
        SaTokenContextMockUtil.setMockContext();
        StpUtil.login(loginId);
        return StpUtil.getTokenValue();
    }

    private String issueAppToken(String loginId) {
        SaTokenContextMockUtil.setMockContext();
        StpLogic appLogic = SaManager.getStpLogic("app", false);
        appLogic.login(loginId);
        return appLogic.getTokenValue();
    }

    private void clearTokenCreationContext() {
        SaTokenContextMockUtil.clearContext();
    }

    private void installSystemLoginRuntime() {
        SaTokenConfig systemTokenConfig = tokenConfig("system-login-test-jwt-secret-at-least-32-bytes");
        SaManager.setConfig(systemTokenConfig);
        StpLogicJwtForSimple systemLogic = new StpLogicJwtForSimple();
        systemLogic.setConfig(systemTokenConfig);
        SaManager.putStpLogic(systemLogic);
    }

    private void installAppSessionRevocationRuntime() {
        AppSaTokenProperties properties = new AppSaTokenProperties();
        properties.setEnabled(false);
        properties.setJwtSecret("app-session-test-jwt-secret-at-least-32-bytes");
        new AppStpLogicRegistrar(properties);
    }

    private SaTokenConfig tokenConfig(String jwtSecret) {
        SaTokenConfig config = new SaTokenConfig();
        config.setTokenName("Authorization");
        config.setTokenPrefix("Bearer");
        config.setIsReadHeader(true);
        config.setIsReadBody(false);
        config.setIsReadCookie(false);
        config.setJwtSecretKey(jwtSecret);
        return config;
    }

    private record TestStpInterface(Map<String, List<String>> permissions) implements StpInterface {

        @Override
        public List<String> getPermissionList(Object loginId, String loginType) {
            return permissions.getOrDefault(String.valueOf(loginId), List.of());
        }

        @Override
        public List<String> getRoleList(Object loginId, String loginType) {
            return List.of();
        }
    }
}
