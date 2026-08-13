package org.dromara.aivideo.user.quota.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import org.dromara.aivideo.identity.dto.AppPrincipalSnapshotDTO;
import org.dromara.aivideo.identity.dto.AppWorkspaceSessionSnapshotDTO;
import org.dromara.aivideo.identity.security.AppLoginHelper;
import org.dromara.aivideo.identity.security.AppLoginUser;
import org.dromara.aivideo.quota.dto.QuotaAccountSnapshotDTO;
import org.dromara.aivideo.quota.service.IQuotaAccountService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.lang.reflect.Method;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("dev")
class QuotaControllerTest {

    @Test
    void queriesOnlyTheCurrentPersonalAccountAndReturnsStringBalances() throws Exception {
        AppLoginHelper loginHelper = mock(AppLoginHelper.class);
        IQuotaAccountService service = mock(IQuotaAccountService.class);
        AppWorkspaceSessionSnapshotDTO workspace = new AppWorkspaceSessionSnapshotDTO(
            "workspace-key", "personal", 2001L, "app_user", 1001L,
            "personal", 1001L, "personal_creator", Set.of("aivideo:quota:query"), 1L, null);
        AppPrincipalSnapshotDTO principal = new AppPrincipalSnapshotDTO(
            1001L, "creator", "creator-web", 1L, 1L, 1L, 1L, workspace);
        when(loginHelper.getLoginUser()).thenReturn(new AppLoginUser(principal, "session-id"));
        when(service.queryPersonalAccount(1001L)).thenReturn(
            new QuotaAccountSnapshotDTO("ai_text_credit", "8640", "160", "40", "8800"));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new QuotaController(loginHelper, service)).build();

        mockMvc.perform(get("/api/quota/account"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.quotaUnit").value("ai_text_credit"))
            .andExpect(jsonPath("$.data.availableBalance").value("8640"))
            .andExpect(jsonPath("$.data.lockedBalance").value("160"))
            .andExpect(jsonPath("$.data.usedBalance").value("40"))
            .andExpect(jsonPath("$.data.totalBalance").value("8800"));

        verify(service).queryPersonalAccount(1001L);
    }

    @Test
    void exposesAnArgumentFreeHandlerProtectedByTheAppPermissionNamespace() throws Exception {
        Method method = QuotaController.class.getDeclaredMethod("account");
        SaCheckPermission permission = method.getAnnotation(SaCheckPermission.class);

        assertThat(method.getParameterCount()).isZero();
        assertThat(permission.value()).containsExactly("aivideo:quota:query");
        assertThat(permission.type()).isEqualTo("app");
    }
}
