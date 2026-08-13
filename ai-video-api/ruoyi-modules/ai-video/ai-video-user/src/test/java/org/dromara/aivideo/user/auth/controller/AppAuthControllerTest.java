package org.dromara.aivideo.user.auth.controller;

import org.dromara.aivideo.identity.dto.AppAuthClientSnapshotDTO;
import org.dromara.aivideo.identity.security.AppVerificationChannel;
import org.dromara.aivideo.identity.security.AppVerificationScenario;
import org.dromara.aivideo.user.auth.domain.bo.AppPasswordChangeBo;
import org.dromara.aivideo.user.auth.domain.bo.AppCodeLoginBo;
import org.dromara.aivideo.user.auth.domain.bo.AppPasswordResetBo;
import org.dromara.aivideo.user.auth.domain.bo.AppMiniProgramLoginBo;
import org.dromara.aivideo.user.auth.domain.bo.AppSocialBindingBo;
import org.dromara.aivideo.user.auth.domain.bo.AppSocialLoginBo;
import org.dromara.aivideo.user.auth.domain.bo.AppVerificationCodeBo;
import org.dromara.aivideo.user.auth.domain.vo.AppLoginVo;
import org.dromara.aivideo.user.auth.domain.vo.AppMeVo;
import org.dromara.aivideo.user.auth.domain.vo.AppSessionVo;
import org.dromara.aivideo.user.auth.domain.vo.AppVerificationChallengeVo;
import org.dromara.aivideo.user.auth.domain.vo.AppWorkspaceVo;
import org.dromara.aivideo.user.auth.service.IAppAuthApplicationService;
import org.dromara.aivideo.user.security.AppClientPolicyService;
import org.dromara.aivideo.user.security.AppSecurityExceptionHandler;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("dev")
class AppAuthControllerTest {

    @Test
    void mapsOnlyTheVerifiedClientSnapshotIntoPasswordLogin() throws Exception {
        IAppAuthApplicationService service = mock(IAppAuthApplicationService.class);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AppAuthController(service))
            .setControllerAdvice(new AppSecurityExceptionHandler()).build();
        AppAuthClientSnapshotDTO client = new AppAuthClientSnapshotDTO("creator-client", 7L);
        when(service.passwordLogin(any(), eq(client))).thenReturn(
            new AppLoginVo("app-token", "creator-client", 1800L,
                new AppWorkspaceVo("workspace-key", "个人工作区", "personal_creator")));

        mockMvc.perform(post("/api/auth/login")
                .requestAttr(AppClientPolicyService.VERIFIED_CLIENT_SNAPSHOT_REQUEST_ATTRIBUTE, client)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"identifier\":\"creator\",\"password\":\"correct-password\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.access_token").value("app-token"))
            .andExpect(jsonPath("$.data.client_id").value("creator-client"));

        verify(service).passwordLogin(any(), eq(client));
    }

    @Test
    void mapsOnlyTheVerifiedClientSnapshotIntoSmsAndEmailCodeLogins() throws Exception {
        IAppAuthApplicationService service = mock(IAppAuthApplicationService.class);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AppAuthController(service))
            .setControllerAdvice(new AppSecurityExceptionHandler()).build();
        AppAuthClientSnapshotDTO client = new AppAuthClientSnapshotDTO("creator-client", 7L);
        AppLoginVo login = new AppLoginVo("app-token", "creator-client", 1800L,
            new AppWorkspaceVo("workspace-key", "个人工作区", "personal_creator"));
        when(service.smsLogin(any(), eq(client))).thenReturn(login);
        when(service.emailLogin(any(), eq(client))).thenReturn(login);

        mockMvc.perform(post("/api/auth/sms-logins")
                .requestAttr(AppClientPolicyService.VERIFIED_CLIENT_SNAPSHOT_REQUEST_ATTRIBUTE, client)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"challengeId\":\"challenge-opaque-value\",\"verificationCode\":\"123456\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.access_token").value("app-token"));
        mockMvc.perform(post("/api/auth/email-logins")
                .requestAttr(AppClientPolicyService.VERIFIED_CLIENT_SNAPSHOT_REQUEST_ATTRIBUTE, client)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"challengeId\":\"challenge-opaque-value\",\"verificationCode\":\"123456\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.access_token").value("app-token"));

        AppCodeLoginBo request = new AppCodeLoginBo("challenge-opaque-value", "123456");
        verify(service).smsLogin(request, client);
        verify(service).emailLogin(request, client);
        assertThat(AppCodeLoginBo.class.getRecordComponents())
            .extracting(component -> component.getName())
            .containsExactly("challengeId", "verificationCode");
    }

    @Test
    void mapsOnlyTheVerifiedClientSnapshotIntoSocialAndMiniProgramLogins() throws Exception {
        IAppAuthApplicationService service = mock(IAppAuthApplicationService.class);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AppAuthController(service))
            .setControllerAdvice(new AppSecurityExceptionHandler()).build();
        AppAuthClientSnapshotDTO client = new AppAuthClientSnapshotDTO("creator-client", 7L);
        AppLoginVo login = new AppLoginVo("app-token", "creator-client", 1800L,
            new AppWorkspaceVo("workspace-key", "个人工作区", "personal_creator"));
        when(service.socialLogin(any(), eq(client))).thenReturn(login);
        when(service.miniProgramLogin(any(), eq(client))).thenReturn(login);

        mockMvc.perform(post("/api/auth/social-logins")
                .requestAttr(AppClientPolicyService.VERIFIED_CLIENT_SNAPSHOT_REQUEST_ATTRIBUTE, client)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"provider\":\"wechat_open\",\"authorizationCode\":\"one-time-code\","
                    + "\"state\":\"callback-state\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.access_token").value("app-token"));
        mockMvc.perform(post("/api/auth/mini-program-logins")
                .requestAttr(AppClientPolicyService.VERIFIED_CLIENT_SNAPSHOT_REQUEST_ATTRIBUTE, client)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"authorizationCode\":\"one-time-mini-program-code\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.access_token").value("app-token"));

        verify(service).socialLogin(new AppSocialLoginBo("wechat_open", "one-time-code", "callback-state"), client);
        verify(service).miniProgramLogin(new AppMiniProgramLoginBo("one-time-mini-program-code"), client);
    }

    @Test
    void mapsSocialBindingAndUnbindingWithoutAcceptingUserOrRevisionFields() throws Exception {
        IAppAuthApplicationService service = mock(IAppAuthApplicationService.class);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AppAuthController(service))
            .setControllerAdvice(new AppSecurityExceptionHandler()).build();

        mockMvc.perform(post("/api/auth/social-bindings")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"provider\":\"wechat_open\",\"authorizationCode\":\"one-time-code\","
                    + "\"state\":\"callback-state\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(delete("/api/auth/social-bindings/{socialIdentityId}", 9001L))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));

        verify(service).bindSocialIdentity(new AppSocialBindingBo(
            "wechat_open", "one-time-code", "callback-state"));
        verify(service).unbindSocialIdentity(9001L);
    }

    @Test
    void rejectsCallerControlledIdentityAndRevisionFieldsFromCodeAndExternalIdentityBodies() throws Exception {
        IAppAuthApplicationService service = mock(IAppAuthApplicationService.class);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AppAuthController(service))
            .setControllerAdvice(new AppSecurityExceptionHandler()).build();
        AppAuthClientSnapshotDTO client = new AppAuthClientSnapshotDTO("creator-client", 7L);

        mockMvc.perform(post("/api/auth/sms-logins")
                .requestAttr(AppClientPolicyService.VERIFIED_CLIENT_SNAPSHOT_REQUEST_ATTRIBUTE, client)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"challengeId\":\"challenge-opaque-value\",\"verificationCode\":\"123456\","
                    + "\"userId\":1001,\"identityRevision\":9}"))
            .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/auth/social-logins")
                .requestAttr(AppClientPolicyService.VERIFIED_CLIENT_SNAPSHOT_REQUEST_ATTRIBUTE, client)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"provider\":\"wechat_open\",\"authorizationCode\":\"one-time-code\","
                    + "\"state\":\"callback-state\",\"userId\":1001,\"clientId\":\"attacker-client\"}"))
            .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/auth/social-bindings")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"provider\":\"wechat_open\",\"authorizationCode\":\"one-time-code\","
                    + "\"state\":\"callback-state\",\"userId\":1001,\"identityRevision\":9}"))
            .andExpect(status().isBadRequest());

        org.mockito.Mockito.verifyNoInteractions(service);
    }

    @Test
    void rejectsCallerControlledIdentityAndRevisionFieldsFromPasswordLoginBodies() throws Exception {
        IAppAuthApplicationService service = mock(IAppAuthApplicationService.class);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AppAuthController(service))
            .setControllerAdvice(new AppSecurityExceptionHandler()).build();
        AppAuthClientSnapshotDTO client = new AppAuthClientSnapshotDTO("creator-client", 7L);

        mockMvc.perform(post("/api/auth/login")
                .requestAttr(AppClientPolicyService.VERIFIED_CLIENT_SNAPSHOT_REQUEST_ATTRIBUTE, client)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"identifier\":\"creator\",\"password\":\"correct-password\","
                    + "\"userId\":1001,\"tenantId\":2001,\"clientId\":\"attacker-client\","
                    + "\"credentialRevision\":9,\"identityRevision\":9,\"permissionRevision\":9}"))
            .andExpect(status().isBadRequest());

        org.mockito.Mockito.verifyNoInteractions(service);
    }

    @Test
    void mapsOnlyTheVerifiedClientSnapshotIntoVerificationCodeRequest() throws Exception {
        IAppAuthApplicationService service = mock(IAppAuthApplicationService.class);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AppAuthController(service))
            .setControllerAdvice(new AppSecurityExceptionHandler()).build();
        AppAuthClientSnapshotDTO client = new AppAuthClientSnapshotDTO("creator-client", 7L);
        when(service.requestVerificationCode(any(), eq(client))).thenReturn(
            new AppVerificationChallengeVo("challenge-opaque-value", "c***@example.com", 600L));

        mockMvc.perform(post("/api/auth/verification-codes")
                .requestAttr(AppClientPolicyService.VERIFIED_CLIENT_SNAPSHOT_REQUEST_ATTRIBUTE, client)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"scenario\":\"PASSWORD_RECOVERY\",\"channel\":\"EMAIL\","
                    + "\"target\":\"creator@example.com\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.challenge_id").value("challenge-opaque-value"))
            .andExpect(jsonPath("$.data.masked_target").value("c***@example.com"))
            .andExpect(jsonPath("$.data.expires_in").value(600));

        verify(service).requestVerificationCode(new AppVerificationCodeBo(
            AppVerificationScenario.PASSWORD_RECOVERY, AppVerificationChannel.EMAIL, "creator@example.com"), client);
        assertThat(AppVerificationCodeBo.class.getRecordComponents())
            .extracting(component -> component.getName())
            .containsExactly("scenario", "channel", "target");
    }

    @Test
    void mapsOnlyTheVerifiedClientSnapshotIntoPasswordRecovery() throws Exception {
        IAppAuthApplicationService service = mock(IAppAuthApplicationService.class);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AppAuthController(service))
            .setControllerAdvice(new AppSecurityExceptionHandler()).build();
        AppAuthClientSnapshotDTO client = new AppAuthClientSnapshotDTO("creator-client", 7L);

        mockMvc.perform(post("/api/auth/password-resets")
                .requestAttr(AppClientPolicyService.VERIFIED_CLIENT_SNAPSHOT_REQUEST_ATTRIBUTE, client)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"challengeId\":\"challenge-opaque-value\",\"verificationCode\":\"123456\","
                    + "\"newPassword\":\"next-password\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));

        verify(service).recoverPassword(new AppPasswordResetBo(
            "challenge-opaque-value", "123456", "next-password"), client);
        assertThat(AppPasswordResetBo.class.getRecordComponents())
            .extracting(component -> component.getName())
            .containsExactly("challengeId", "verificationCode", "newPassword");
    }

    @Test
    void rejectsCallerControlledIdentityAndRevisionFieldsFromPublicRecoveryBodies() throws Exception {
        IAppAuthApplicationService service = mock(IAppAuthApplicationService.class);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AppAuthController(service))
            .setControllerAdvice(new AppSecurityExceptionHandler()).build();
        AppAuthClientSnapshotDTO client = new AppAuthClientSnapshotDTO("creator-client", 7L);

        mockMvc.perform(post("/api/auth/password-resets")
                .requestAttr(AppClientPolicyService.VERIFIED_CLIENT_SNAPSHOT_REQUEST_ATTRIBUTE, client)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"challengeId\":\"challenge-opaque-value\",\"verificationCode\":\"123456\","
                    + "\"newPassword\":\"next-password\",\"userId\":1001,\"tenantId\":2001,"
                    + "\"clientId\":\"attacker-client\",\"credentialRevision\":9,\"identityRevision\":9}"))
            .andExpect(status().isBadRequest());

        org.mockito.Mockito.verifyNoInteractions(service);
    }

    @Test
    void rejectsCallerControlledIdentityAndRevisionFieldsFromPublicVerificationBodies() throws Exception {
        IAppAuthApplicationService service = mock(IAppAuthApplicationService.class);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AppAuthController(service))
            .setControllerAdvice(new AppSecurityExceptionHandler()).build();
        AppAuthClientSnapshotDTO client = new AppAuthClientSnapshotDTO("creator-client", 7L);

        mockMvc.perform(post("/api/auth/verification-codes")
                .requestAttr(AppClientPolicyService.VERIFIED_CLIENT_SNAPSHOT_REQUEST_ATTRIBUTE, client)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"scenario\":\"PASSWORD_RECOVERY\",\"channel\":\"EMAIL\","
                    + "\"target\":\"creator@example.com\",\"userId\":1001,\"clientId\":\"attacker-client\","
                    + "\"credentialRevision\":9,\"identityRevision\":9}"))
            .andExpect(status().isBadRequest());

        org.mockito.Mockito.verifyNoInteractions(service);
    }

    @Test
    void mapsCurrentUserAndLogoutWithoutAcceptingUserIdentifiers() throws Exception {
        IAppAuthApplicationService service = mock(IAppAuthApplicationService.class);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AppAuthController(service))
            .setControllerAdvice(new AppSecurityExceptionHandler()).build();
        when(service.me()).thenReturn(new AppMeVo("1001", "creator", "创作者", "138****8000",
            "c***@example.com", false, List.of("personal_creator"), List.of("creation:script:read"),
            new AppWorkspaceVo("workspace-key", "个人工作区", "personal_creator")));

        mockMvc.perform(get("/api/auth/me"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value("1001"));
        mockMvc.perform(post("/api/auth/logout"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));

        verify(service).me();
        verify(service).logoutCurrent();
    }

    @Test
    void mapsPasswordChangeWithoutCallerControlledIdentityFields() throws Exception {
        IAppAuthApplicationService service = mock(IAppAuthApplicationService.class);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AppAuthController(service))
            .setControllerAdvice(new AppSecurityExceptionHandler()).build();

        mockMvc.perform(put("/api/auth/password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"current-password\",\"newPassword\":\"new-password\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));

        verify(service).changePassword(new AppPasswordChangeBo("current-password", "new-password"));
        assertThat(AppPasswordChangeBo.class.getRecordComponents())
            .extracting(component -> component.getName())
            .containsExactly("currentPassword", "newPassword");
    }

    @Test
    void rejectsCallerControlledIdentityAndRevisionFieldsFromPasswordChangeBodies() throws Exception {
        IAppAuthApplicationService service = mock(IAppAuthApplicationService.class);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AppAuthController(service))
            .setControllerAdvice(new AppSecurityExceptionHandler()).build();

        mockMvc.perform(put("/api/auth/password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"current-password\",\"newPassword\":\"new-password\","
                    + "\"userId\":1001,\"tenantId\":2001,\"clientId\":\"attacker-client\","
                    + "\"credentialRevision\":9,\"identityRevision\":9,\"permissionRevision\":9}"))
            .andExpect(status().isBadRequest());

        org.mockito.Mockito.verifyNoInteractions(service);
    }

    @Test
    void mapsOnlyCurrentUserSessionResourcesWithoutCallerControlledIdentityFields() throws Exception {
        IAppAuthApplicationService service = mock(IAppAuthApplicationService.class);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AppAuthController(service))
            .setControllerAdvice(new AppSecurityExceptionHandler()).build();
        String sessionId = "9d4cf756-5a8b-424d-86e6-ae4a75ffad8d";
        when(service.listCurrentUserSessions()).thenReturn(List.of(
            new AppSessionVo(sessionId, "creator-web", "web", LocalDateTime.of(2026, 7, 30, 10, 15), true)));

        mockMvc.perform(get("/api/auth/sessions"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data[0].id").value(sessionId))
            .andExpect(jsonPath("$.data[0].clientId").value("creator-web"))
            .andExpect(jsonPath("$.data[0].deviceName").value("web"))
            .andExpect(jsonPath("$.data[0].lastActiveAt").value("2026-07-30T10:15:00"))
            .andExpect(jsonPath("$.data[0].current").value(true))
            .andExpect(jsonPath("$.data[0].createdAt").doesNotExist())
            .andExpect(jsonPath("$.data[0].token").doesNotExist())
            .andExpect(jsonPath("$.data[0].tokenValue").doesNotExist())
            .andExpect(jsonPath("$.data[0].tokenReference").doesNotExist());
        mockMvc.perform(delete("/api/auth/sessions/{sessionId}", sessionId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));

        verify(service).listCurrentUserSessions();
        verify(service).revokeOwnSession(sessionId);
        assertThat(AppSessionVo.class.getRecordComponents())
            .extracting(component -> component.getName())
            .containsExactly("id", "clientId", "deviceName", "lastActiveAt", "current");
    }
}
