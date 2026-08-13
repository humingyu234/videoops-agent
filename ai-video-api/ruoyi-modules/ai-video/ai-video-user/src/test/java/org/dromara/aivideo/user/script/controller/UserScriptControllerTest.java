package org.dromara.aivideo.user.script.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import org.dromara.aivideo.identity.dto.AppPrincipalSnapshotDTO;
import org.dromara.aivideo.identity.dto.AppWorkspaceSessionSnapshotDTO;
import org.dromara.aivideo.identity.security.AppLoginHelper;
import org.dromara.aivideo.script.dto.ScriptVersionDTO;
import org.dromara.aivideo.script.dto.ScriptVersionSummaryDTO;
import org.dromara.aivideo.script.dto.UserScriptDetailDTO;
import org.dromara.aivideo.script.dto.UserScriptListDTO;
import org.dromara.aivideo.script.dto.UserScriptSaveResultDTO;
import org.dromara.aivideo.script.service.IUserScriptService;
import org.dromara.aivideo.user.script.domain.bo.CreateUserScriptBo;
import org.dromara.aivideo.user.script.domain.bo.EditUserScriptBo;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.redis.annotation.RepeatSubmit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("dev")
class UserScriptControllerTest {

    @Test
    void exposesSixAppPermissionProtectedRoutesWithoutGenericRepeatSubmit() throws Exception {
        assertPermission("list", "aivideo:script:query", String.class, String.class, String.class, PageQuery.class);
        assertPermission("detail", "aivideo:script:query", String.class);
        assertPermission("version", "aivideo:script:query", String.class, String.class);
        assertPermission("create", "aivideo:script:edit", CreateUserScriptBo.class);
        assertPermission("createVersion", "aivideo:script:edit", String.class, EditUserScriptBo.class);
        assertPermission("delete", "aivideo:script:remove", String.class);

        assertThat(UserScriptController.class.getMethod("create", CreateUserScriptBo.class)
            .isAnnotationPresent(RepeatSubmit.class)).isFalse();
        assertThat(UserScriptController.class.getMethod("createVersion", String.class, EditUserScriptBo.class)
            .isAnnotationPresent(RepeatSubmit.class)).isFalse();
    }

    @Test
    void mapsAllRoutesAndAlwaysUsesCurrentAppPrincipal() throws Exception {
        IUserScriptService service = mock(IUserScriptService.class);
        AppLoginHelper loginHelper = mock(AppLoginHelper.class);
        AppPrincipalSnapshotDTO principal = principal();
        LocalDateTime now = LocalDateTime.of(2026, 8, 5, 12, 0);
        when(loginHelper.getPrincipal()).thenReturn(principal);
        when(service.queryPage(any(), eq(principal), any())).thenReturn(PageResult.build(List.of(
            new UserScriptListDTO("s1", "开场", "v1", 1, 1L, "manual_input", 4, 2,
                "大家好", now, now)), 1));
        ScriptVersionDTO version = new ScriptVersionDTO(
            "s1", "v1", null, 1, "manual_input", "大家好", 4, 2, now);
        when(service.queryById("s1", principal)).thenReturn(new UserScriptDetailDTO(
            "s1", "开场", "1", "v1", now, now, version,
            List.of(new ScriptVersionSummaryDTO("v1", null, 1, "manual_input", 4, 2, "大家好", now))));
        when(service.queryVersion("s1", "v1", principal)).thenReturn(version);
        when(service.create(any(), eq(principal))).thenReturn(saveResult(now, false));
        when(service.createVersion(any(), eq(principal))).thenReturn(saveResult(now, false));
        MockMvc mvc = mvc(service, loginHelper);

        mvc.perform(get("/api/studio/scripts").param("keyword", "开场"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.rows[0].scriptId").value("s1"))
            .andExpect(jsonPath("$.data.total").value(1));
        mvc.perform(get("/api/studio/scripts/s1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.currentVersion.scriptText").value("大家好"));
        mvc.perform(get("/api/studio/scripts/s1/versions/v1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.versionId").value("v1"));
        mvc.perform(post("/api/studio/scripts").contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayTitle\":\"开场\",\"scriptText\":\"大家好\",\"idempotencyKey\":\"create-1\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.scriptId").value("s1"));
        mvc.perform(post("/api/studio/scripts/s1/versions").contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayTitle\":\"新开场\",\"scriptText\":\"大家好呀\","
                    + "\"parentVersionId\":\"v1\",\"expectedScriptRevision\":\"1\","
                    + "\"idempotencyKey\":\"edit-1\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.currentVersionId").value("v1"));
        mvc.perform(delete("/api/studio/scripts/s1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));

        verify(service).queryPage(any(), eq(principal), any());
        verify(service).queryById("s1", principal);
        verify(service).queryVersion("s1", "v1", principal);
        verify(service).create(any(), eq(principal));
        verify(service).createVersion(any(), eq(principal));
        verify(service).delete("s1", principal);
    }

    @Test
    void rejectsCallerControlledOwnershipFieldsBeforeCallingService() throws Exception {
        IUserScriptService service = mock(IUserScriptService.class);
        AppLoginHelper loginHelper = mock(AppLoginHelper.class);
        when(loginHelper.getPrincipal()).thenReturn(principal());
        MockMvc mvc = mvc(service, loginHelper);

        for (String forbidden : List.of("ownerId", "tenantId", "workspaceId", "appUserId")) {
            mvc.perform(post("/api/studio/scripts").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"displayTitle\":\"开场\",\"scriptText\":\"大家好\","
                        + "\"idempotencyKey\":\"create-1\",\"" + forbidden + "\":\"9999\"}"))
                .andExpect(status().isBadRequest());
        }

        verify(service, never()).create(any(), any());
    }

    @Test
    void validatesRequiredFieldsBeforeCallingService() throws Exception {
        IUserScriptService service = mock(IUserScriptService.class);
        AppLoginHelper loginHelper = mock(AppLoginHelper.class);
        MockMvc mvc = mvc(service, loginHelper);

        mvc.perform(post("/api/studio/scripts").contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayTitle\":\"\",\"scriptText\":\"\",\"idempotencyKey\":\"\"}"))
            .andExpect(status().isBadRequest());

        verify(service, never()).create(any(), any());
    }

    private void assertPermission(String methodName, String permission, Class<?>... parameterTypes)
        throws NoSuchMethodException {
        Method method = UserScriptController.class.getMethod(methodName, parameterTypes);
        SaCheckPermission annotation = method.getAnnotation(SaCheckPermission.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.type()).isEqualTo("app");
        assertThat(annotation.value()).containsExactly(permission);
    }

    private MockMvc mvc(IUserScriptService service, AppLoginHelper loginHelper) {
        return MockMvcBuilders.standaloneSetup(new UserScriptController(service, loginHelper)).build();
    }

    private UserScriptSaveResultDTO saveResult(LocalDateTime now, boolean reused) {
        return new UserScriptSaveResultDTO("s1", "v1", "1", 1, "开场", 4, 2, now, reused);
    }

    private AppPrincipalSnapshotDTO principal() {
        AppWorkspaceSessionSnapshotDTO workspace = new AppWorkspaceSessionSnapshotDTO(
            "personal-1001", "personal", 2001L, "app_user", 1001L,
            "app_user", 1001L, "personal_creator",
            Set.of("aivideo:script:query", "aivideo:script:edit", "aivideo:script:remove"), 1L, null);
        return new AppPrincipalSnapshotDTO(1001L, "creator", "creator-web", 1L, 1L, 1L, 1L, workspace);
    }
}
