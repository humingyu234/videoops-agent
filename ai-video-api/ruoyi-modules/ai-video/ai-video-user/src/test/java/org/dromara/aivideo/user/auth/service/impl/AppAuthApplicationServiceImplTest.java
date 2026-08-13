package org.dromara.aivideo.user.auth.service.impl;

import org.dromara.aivideo.identity.service.IAppIdentityService;
import org.dromara.aivideo.identity.service.IAppSecurityAuditService;
import org.dromara.aivideo.identity.service.IAppSessionService;
import org.dromara.aivideo.identity.service.IAppVerificationCodeService;
import org.dromara.aivideo.identity.dto.BindSocialIdentityDTO;
import org.dromara.aivideo.identity.dto.ChangeAppPasswordDTO;
import org.dromara.aivideo.identity.dto.RecoverAppPasswordDTO;
import org.dromara.aivideo.identity.dto.AppVerificationCodeRequestDTO;
import org.dromara.aivideo.identity.dto.AppAuthClientSnapshotDTO;
import org.dromara.aivideo.identity.dto.AppAuthenticatedIdentityDTO;
import org.dromara.aivideo.identity.dto.AppVerificationChallengeDTO;
import org.dromara.aivideo.identity.domain.AppIdentityStatus;
import org.dromara.aivideo.identity.domain.AppLoginLog;
import org.dromara.aivideo.identity.domain.AppSecurityAuditReason;
import org.dromara.aivideo.identity.domain.AppUser;
import org.dromara.aivideo.identity.mapper.AppLoginLogMapper;
import org.dromara.aivideo.identity.mapper.AppUserMapper;
import org.dromara.aivideo.identity.dto.AppPrincipalSnapshotDTO;
import org.dromara.aivideo.identity.dto.AppSecurityAuditDTO;
import org.dromara.aivideo.identity.dto.AppSessionSummaryDTO;
import org.dromara.aivideo.identity.dto.AppWorkspaceSessionSnapshotDTO;
import org.dromara.aivideo.identity.domain.AppExternalIdentityChannel;
import org.dromara.aivideo.identity.service.IAppExternalIdentityService;
import org.dromara.aivideo.identity.dto.AppExternalIdentityDTO;
import org.dromara.aivideo.identity.dto.AppMiniProgramAuthorizationDTO;
import org.dromara.aivideo.identity.dto.AppSocialIdentityAuthorizationDTO;
import org.dromara.aivideo.identity.security.AppAuthenticationSessionIssuer;
import org.dromara.aivideo.identity.security.AppActorContext;
import org.dromara.aivideo.identity.security.AppAuditRequestContext;
import org.dromara.aivideo.identity.security.AppAuditRequestContextHolder;
import org.dromara.aivideo.identity.security.AppIssuedAccessToken;
import org.dromara.aivideo.identity.security.AppLoginHelper;
import org.dromara.aivideo.identity.security.AppLoginUser;
import org.dromara.aivideo.identity.security.AppLoginVerificationGrant;
import org.dromara.aivideo.identity.security.IAppLoginVerificationService;
import org.dromara.aivideo.identity.security.AppLoginVerificationReservation;
import org.dromara.aivideo.identity.security.AppLoginVerificationRequest;
import org.dromara.aivideo.identity.security.AppPersonalWorkspaceSnapshotProvider;
import org.dromara.aivideo.identity.security.AppVerificationChannel;
import org.dromara.aivideo.identity.security.AppVerificationScenario;
import org.dromara.aivideo.user.auth.domain.bo.AppPasswordChangeBo;
import org.dromara.aivideo.user.auth.domain.bo.AppCodeLoginBo;
import org.dromara.aivideo.user.auth.domain.bo.AppPasswordLoginBo;
import org.dromara.aivideo.user.auth.domain.bo.AppPasswordResetBo;
import org.dromara.aivideo.user.auth.domain.bo.AppMiniProgramLoginBo;
import org.dromara.aivideo.user.auth.domain.bo.AppSocialBindingBo;
import org.dromara.aivideo.user.auth.domain.bo.AppSocialLoginBo;
import org.dromara.aivideo.user.auth.domain.bo.AppVerificationCodeBo;
import org.dromara.aivideo.user.auth.domain.vo.AppLoginVo;
import org.dromara.aivideo.user.auth.domain.vo.AppMeVo;
import org.dromara.aivideo.user.auth.domain.vo.AppSessionVo;
import org.dromara.aivideo.user.auth.domain.vo.AppVerificationChallengeVo;
import org.dromara.aivideo.user.security.AppAuthErrorCodes;
import org.dromara.aivideo.user.security.AppSecurityException;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("dev")
class AppAuthApplicationServiceImplTest {

    private static final AppAuthClientSnapshotDTO VERIFIED_CLIENT = new AppAuthClientSnapshotDTO("creator-web", 7L);

    @Mock
    private IAppIdentityService identityService;

    @Mock
    private IAppSessionService sessionService;

    @Mock
    private IAppVerificationCodeService verificationCodeService;

    @Mock
    private IAppLoginVerificationService loginVerificationPort;

    @Mock
    private IAppSecurityAuditService securityAuditService;

    @Mock
    private AppUserMapper userMapper;

    @Mock
    private AppPersonalWorkspaceSnapshotProvider workspaceProvider;

    @Mock
    private AppAuthenticationSessionIssuer sessionIssuer;

    @Mock
    private AppLoginHelper loginHelper;

    @Mock
    private AppLoginLogMapper loginLogMapper;

    @Mock
    private IAppExternalIdentityService socialIdentityPort;

    @Mock
    private IAppExternalIdentityService miniProgramIdentityPort;

    private AppAuthApplicationServiceImpl service;

    @BeforeEach
    void setUp() {
        when(socialIdentityPort.channel()).thenReturn(AppExternalIdentityChannel.SOCIAL);
        when(miniProgramIdentityPort.channel()).thenReturn(AppExternalIdentityChannel.MINI_PROGRAM);
        service = serviceWithExternalIdentityPorts(List.of(socialIdentityPort, miniProgramIdentityPort));
    }

    @Test
    void passwordLoginUsesTheVerifiedClientSnapshotAndReturnsOnlyTheIssuedAppToken() {
        AppUser user = activeUser();
        AppWorkspaceSessionSnapshotDTO workspace = workspace();
        AppPrincipalSnapshotDTO principal = principal(workspace);
        when(identityService.authenticatePassword(any(), eq(VERIFIED_CLIENT)))
            .thenReturn(new AppAuthenticatedIdentityDTO(1001L, "creator", false, 2L, 3L, 4L));
        when(userMapper.selectById(1001L)).thenReturn(user);
        when(workspaceProvider.personalWorkspace(user)).thenReturn(workspace);
        when(sessionIssuer.issue(any(), eq("web")))
            .thenReturn(new AppIssuedAccessToken(new AppLoginUser(principal, "session-1"), "app-token", 1800L));
        when(loginLogMapper.insert(any(AppLoginLog.class))).thenReturn(1);

        AppLoginVo result;
        try (AppAuditRequestContextHolder.Scope ignored = httpAuditScope("1")) {
            result = service.passwordLogin(new AppPasswordLoginBo("creator", "correct-password"), VERIFIED_CLIENT);
        }

        assertThat(result.accessToken()).isEqualTo("app-token");
        assertThat(result.clientId()).isEqualTo("creator-web");
        assertThat(result.expireIn()).isEqualTo(1800L);
        assertThat(result.currentWorkspace().id()).isEqualTo("workspace-key");

        ArgumentCaptor<org.dromara.aivideo.identity.dto.AuthenticatePasswordDTO>
            commandCaptor = ArgumentCaptor.forClass(
                org.dromara.aivideo.identity.dto.AuthenticatePasswordDTO.class);
        verify(identityService).authenticatePassword(commandCaptor.capture(), eq(VERIFIED_CLIENT));
        assertThat(commandCaptor.getValue().clientId()).isEqualTo("creator-web");
        assertThat(commandCaptor.getValue().identifier()).isEqualTo("creator");
        assertThat(commandCaptor.getValue().password()).isEqualTo("correct-password");
        verify(sessionIssuer).issue(principal, "web");
        verify(loginLogMapper).insert(any(AppLoginLog.class));
    }

    @Test
    void smsLoginReservesTheVerifiedLoginCodeAndConsumesItOnlyAfterSessionAuditSucceeds() {
        AppUser user = activeUser();
        AppWorkspaceSessionSnapshotDTO workspace = workspace();
        AppPrincipalSnapshotDTO principal = principal(workspace);
        AppLoginVerificationReservation reservation = new AppLoginVerificationReservation(
            "challenge-opaque-value", "reservation-opaque-value",
            new AppLoginVerificationGrant(1001L, AppVerificationChannel.PHONE, 2L, 3L));
        when(loginVerificationPort.reserve(any(AppLoginVerificationRequest.class))).thenReturn(reservation);
        when(identityService.authenticateVerifiedContact(reservation.grant(), VERIFIED_CLIENT))
            .thenReturn(new AppAuthenticatedIdentityDTO(1001L, "creator", false, 2L, 3L, 4L));
        when(userMapper.selectById(1001L)).thenReturn(user);
        when(workspaceProvider.personalWorkspace(user)).thenReturn(workspace);
        when(sessionIssuer.issue(any(), eq("web")))
            .thenReturn(new AppIssuedAccessToken(new AppLoginUser(principal, "session-1"), "app-token", 1800L));
        when(loginLogMapper.insert(any(AppLoginLog.class))).thenReturn(1);

        AppLoginVo result;
        try (AppAuditRequestContextHolder.Scope ignored = httpAuditScope("4")) {
            result = service.smsLogin(new AppCodeLoginBo("challenge-opaque-value", "123456"), VERIFIED_CLIENT);
        }

        assertThat(result.accessToken()).isEqualTo("app-token");
        ArgumentCaptor<AppLoginVerificationRequest> requestCaptor =
            ArgumentCaptor.forClass(AppLoginVerificationRequest.class);
        verify(loginVerificationPort).reserve(requestCaptor.capture());
        assertThat(requestCaptor.getValue()).isEqualTo(new AppLoginVerificationRequest(
            "challenge-opaque-value", "123456", "creator-web", 7L));
        verify(identityService).authenticateVerifiedContact(reservation.grant(), VERIFIED_CLIENT);
        verify(loginVerificationPort).commit(reservation);
        ArgumentCaptor<AppLoginLog> logCaptor = ArgumentCaptor.forClass(AppLoginLog.class);
        verify(loginLogMapper).insert(logCaptor.capture());
        assertThat(logCaptor.getValue().getAuthMethod())
            .isEqualTo(org.dromara.aivideo.identity.domain.AppAuthMethod.SMS);
        assertThat(logCaptor.getValue().getMaskedIdentifier()).isEqualTo("138****8000");
    }

    @Test
    void smsLoginReleasesItsReservationWhenTheVerifiedCodeWasIssuedForEmail() {
        AppLoginVerificationReservation reservation = new AppLoginVerificationReservation(
            "challenge-opaque-value", "reservation-opaque-value",
            new AppLoginVerificationGrant(1001L, AppVerificationChannel.EMAIL, 2L, 3L));
        when(loginVerificationPort.reserve(any(AppLoginVerificationRequest.class))).thenReturn(reservation);
        when(loginLogMapper.insert(any(AppLoginLog.class))).thenReturn(1);

        try (AppAuditRequestContextHolder.Scope ignored = httpAuditScope("5")) {
            assertThatThrownBy(() -> service.smsLogin(
                new AppCodeLoginBo("challenge-opaque-value", "123456"), VERIFIED_CLIENT))
                .isInstanceOf(AppSecurityException.class)
                .extracting(error -> ((AppSecurityException) error).getCode())
                .isEqualTo(AppAuthErrorCodes.APP_AUTH_CREDENTIALS_INVALID);
        }

        verify(loginVerificationPort).release(reservation);
        verify(loginVerificationPort, never()).commit(reservation);
        verifyNoInteractions(identityService, sessionIssuer);
    }

    @Test
    void socialLoginExchangesTheOneTimeAuthorizationThenAuthenticatesOnlyTheBoundAppIdentity() {
        AppUser user = activeUser();
        AppWorkspaceSessionSnapshotDTO workspace = workspace();
        AppPrincipalSnapshotDTO principal = principal(workspace);
        AppSocialLoginBo request = new AppSocialLoginBo("wechat_open", "one-time-auth-code", "callback-state");
        AppExternalIdentityDTO externalIdentity = new AppExternalIdentityDTO("wechat_open", "open-id-1001");
        when(socialIdentityPort.exchange(new AppSocialIdentityAuthorizationDTO(
            "wechat_open", "one-time-auth-code", "callback-state"))).thenReturn(externalIdentity);
        when(identityService.authenticateExternalIdentity(externalIdentity, VERIFIED_CLIENT))
            .thenReturn(new AppAuthenticatedIdentityDTO(1001L, "creator", false, 2L, 3L, 4L));
        when(userMapper.selectById(1001L)).thenReturn(user);
        when(workspaceProvider.personalWorkspace(user)).thenReturn(workspace);
        when(sessionIssuer.issue(any(), eq("web")))
            .thenReturn(new AppIssuedAccessToken(new AppLoginUser(principal, "session-1"), "app-token", 1800L));
        when(loginLogMapper.insert(any(AppLoginLog.class))).thenReturn(1);

        AppLoginVo result;
        try (AppAuditRequestContextHolder.Scope ignored = httpAuditScope("6")) {
            result = service.socialLogin(request, VERIFIED_CLIENT);
        }

        assertThat(result.accessToken()).isEqualTo("app-token");
        verify(socialIdentityPort).exchange(new AppSocialIdentityAuthorizationDTO(
            "wechat_open", "one-time-auth-code", "callback-state"));
        verify(identityService).authenticateExternalIdentity(externalIdentity, VERIFIED_CLIENT);
        ArgumentCaptor<AppLoginLog> logCaptor = ArgumentCaptor.forClass(AppLoginLog.class);
        verify(loginLogMapper).insert(logCaptor.capture());
        assertThat(logCaptor.getValue().getAuthMethod())
            .isEqualTo(org.dromara.aivideo.identity.domain.AppAuthMethod.SOCIAL);
        assertThat(logCaptor.getValue().getMaskedIdentifier()).isEqualTo("***");
    }

    @Test
    void miniProgramLoginDoesNotIssueASessionWhenTheExternalIdentityIsUnbound() {
        AppMiniProgramLoginBo request = new AppMiniProgramLoginBo("one-time-mini-program-code");
        AppExternalIdentityDTO externalIdentity = new AppExternalIdentityDTO("wechat_mini_program", "open-id-1001");
        when(miniProgramIdentityPort.exchange(new AppMiniProgramAuthorizationDTO(
            "one-time-mini-program-code"))).thenReturn(externalIdentity);
        when(identityService.authenticateExternalIdentity(externalIdentity, VERIFIED_CLIENT))
            .thenThrow(new ServiceException("third party identity is not bound"));
        when(loginLogMapper.insert(any(AppLoginLog.class))).thenReturn(1);

        try (AppAuditRequestContextHolder.Scope ignored = httpAuditScope("7")) {
            assertThatThrownBy(() -> service.miniProgramLogin(request, VERIFIED_CLIENT))
                .isInstanceOf(AppSecurityException.class)
                .extracting(error -> ((AppSecurityException) error).getCode())
                .isEqualTo(AppAuthErrorCodes.APP_AUTH_CREDENTIALS_INVALID);
        }

        verify(sessionIssuer, never()).issue(any(), any());
        ArgumentCaptor<AppLoginLog> logCaptor = ArgumentCaptor.forClass(AppLoginLog.class);
        verify(loginLogMapper).insert(logCaptor.capture());
        assertThat(logCaptor.getValue().getAuthMethod())
            .isEqualTo(org.dromara.aivideo.identity.domain.AppAuthMethod.MINI_PROGRAM);
        assertThat(logCaptor.getValue().getMaskedIdentifier()).isEqualTo("***");
    }

    @Test
    void externalIdentityLoginFailsClosedWhenNoConfiguredAdapterIsAvailable() {
        AppAuthApplicationServiceImpl serviceWithoutAdapters = serviceWithExternalIdentityPorts(List.of());
        when(loginLogMapper.insert(any(AppLoginLog.class))).thenReturn(1);

        try (AppAuditRequestContextHolder.Scope ignored = httpAuditScope("8")) {
            assertThatThrownBy(() -> serviceWithoutAdapters.socialLogin(
                new AppSocialLoginBo("wechat_open", "one-time-auth-code", "callback-state"), VERIFIED_CLIENT))
                .isInstanceOf(AppSecurityException.class)
                .extracting(error -> ((AppSecurityException) error).getCode())
                .isEqualTo(AppAuthErrorCodes.APP_AUTH_CREDENTIALS_INVALID);
        }

        verifyNoInteractions(identityService, sessionIssuer);
    }

    @Test
    void socialBindingUsesOnlyTheCurrentAppSessionIdentityAndTheVerifiedExternalIdentity() {
        AppPrincipalSnapshotDTO principal = principal(workspace());
        AppSocialBindingBo request = new AppSocialBindingBo("wechat_open", "one-time-auth-code", "callback-state");
        AppExternalIdentityDTO externalIdentity = new AppExternalIdentityDTO("wechat_open", "open-id-1001");
        when(loginHelper.getLoginUser()).thenReturn(new AppLoginUser(principal, "session-1"));
        when(socialIdentityPort.exchange(new AppSocialIdentityAuthorizationDTO(
            "wechat_open", "one-time-auth-code", "callback-state"))).thenReturn(externalIdentity);

        service.bindSocialIdentity(request);

        verify(identityService).bindSocialIdentity(
            new BindSocialIdentityDTO(1001L, "wechat_open", "open-id-1001", 3L),
            AppActorContext.appUser(1001L));
    }

    @Test
    void socialUnbindingUsesOnlyTheCurrentAppSessionIdentity() {
        AppPrincipalSnapshotDTO principal = principal(workspace());
        when(loginHelper.getLoginUser()).thenReturn(new AppLoginUser(principal, "session-1"));

        service.unbindSocialIdentity(9001L);

        verify(identityService).unbindSocialIdentity(1001L, 9001L, AppActorContext.appUser(1001L));
    }

    @Test
    void requestVerificationCodeUsesOnlyTheVerifiedClientAndReturnsASafeProjection() {
        when(verificationCodeService.issue(any(AppVerificationCodeRequestDTO.class), eq(VERIFIED_CLIENT)))
            .thenReturn(new AppVerificationChallengeDTO("challenge-opaque-value", "c***@example.com", 600L));

        AppVerificationChallengeVo result = service.requestVerificationCode(new AppVerificationCodeBo(
            AppVerificationScenario.PASSWORD_RECOVERY, AppVerificationChannel.EMAIL, "creator@example.com"),
            VERIFIED_CLIENT);

        assertThat(result).isEqualTo(new AppVerificationChallengeVo(
            "challenge-opaque-value", "c***@example.com", 600L));
        ArgumentCaptor<AppVerificationCodeRequestDTO> requestCaptor = ArgumentCaptor.forClass(
            AppVerificationCodeRequestDTO.class);
        verify(verificationCodeService).issue(requestCaptor.capture(), eq(VERIFIED_CLIENT));
        assertThat(requestCaptor.getValue()).isEqualTo(new AppVerificationCodeRequestDTO(
            AppVerificationScenario.PASSWORD_RECOVERY, AppVerificationChannel.EMAIL, "creator@example.com"));
    }

    @Test
    void recoverPasswordUsesOnlyTheVerifiedClientAndDoesNotReadTheCurrentSession() {
        service.recoverPassword(new AppPasswordResetBo(
            "challenge-opaque-value", "123456", "next-password"), VERIFIED_CLIENT);

        ArgumentCaptor<RecoverAppPasswordDTO> commandCaptor = ArgumentCaptor.forClass(RecoverAppPasswordDTO.class);
        verify(identityService).recoverPassword(commandCaptor.capture(), eq(VERIFIED_CLIENT));
        assertThat(commandCaptor.getValue()).isEqualTo(new RecoverAppPasswordDTO(
            "challenge-opaque-value", "123456", "next-password"));
        verifyNoInteractions(loginHelper, sessionIssuer);
    }

    @Test
    void mapsExpectedVerificationAndRecoveryFailuresToTheGenericCredentialError() {
        when(verificationCodeService.issue(any(AppVerificationCodeRequestDTO.class), eq(VERIFIED_CLIENT)))
            .thenThrow(new ServiceException("内部验证码状态机错误"));
        doThrow(new ServiceException("内部找回密码状态机错误"))
            .when(identityService).recoverPassword(any(RecoverAppPasswordDTO.class), eq(VERIFIED_CLIENT));

        assertThatThrownBy(() -> service.requestVerificationCode(new AppVerificationCodeBo(
            AppVerificationScenario.PASSWORD_RECOVERY, AppVerificationChannel.EMAIL, "creator@example.com"),
            VERIFIED_CLIENT))
            .isInstanceOf(AppSecurityException.class)
            .extracting(error -> ((AppSecurityException) error).getCode())
            .isEqualTo(AppAuthErrorCodes.APP_AUTH_CREDENTIALS_INVALID);
        assertThatThrownBy(() -> service.recoverPassword(new AppPasswordResetBo(
            "challenge-opaque-value", "123456", "next-password"), VERIFIED_CLIENT))
            .isInstanceOf(AppSecurityException.class)
            .extracting(error -> ((AppSecurityException) error).getCode())
            .isEqualTo(AppAuthErrorCodes.APP_AUTH_CREDENTIALS_INVALID);
    }

    @Test
    void passwordLoginDoesNotExposeAccountOrPasswordFailureDetails() {
        when(identityService.authenticatePassword(any(), eq(VERIFIED_CLIENT)))
            .thenThrow(new ServiceException("internal password mismatch detail"));
        when(loginLogMapper.insert(any(AppLoginLog.class))).thenReturn(1);

        try (AppAuditRequestContextHolder.Scope ignored = httpAuditScope("2")) {
            assertThatThrownBy(() -> service.passwordLogin(new AppPasswordLoginBo("missing", "wrong"), VERIFIED_CLIENT))
                .isInstanceOf(AppSecurityException.class)
                .extracting(error -> ((AppSecurityException) error).getCode())
                .isEqualTo(AppAuthErrorCodes.APP_AUTH_CREDENTIALS_INVALID);
        }

        verify(sessionIssuer, never()).issue(any(), any());
        ArgumentCaptor<AppLoginLog> loginLogCaptor = ArgumentCaptor.forClass(AppLoginLog.class);
        verify(loginLogMapper).insert(loginLogCaptor.capture());
        assertThat(loginLogCaptor.getValue().getMaskedIdentifier()).doesNotContain("missing");
        assertThat(loginLogCaptor.getValue().getFailureCategory()).isEqualTo("credentials_invalid");
    }

    @Test
    void passwordLoginRevokesTheNewAppSessionWhenTheSuccessAuditCannotBeRecorded() {
        AppUser user = activeUser();
        AppWorkspaceSessionSnapshotDTO workspace = workspace();
        AppPrincipalSnapshotDTO principal = principal(workspace);
        when(identityService.authenticatePassword(any(), eq(VERIFIED_CLIENT)))
            .thenReturn(new AppAuthenticatedIdentityDTO(1001L, "creator", false, 2L, 3L, 4L));
        when(userMapper.selectById(1001L)).thenReturn(user);
        when(workspaceProvider.personalWorkspace(user)).thenReturn(workspace);
        when(sessionIssuer.issue(any(), eq("web")))
            .thenReturn(new AppIssuedAccessToken(new AppLoginUser(principal, "session-1"), "app-token", 1800L));
        when(loginLogMapper.insert(any(AppLoginLog.class))).thenReturn(0);

        try (AppAuditRequestContextHolder.Scope ignored = httpAuditScope("3")) {
            assertThatThrownBy(() -> service.passwordLogin(new AppPasswordLoginBo("creator", "correct-password"),
                VERIFIED_CLIENT)).isInstanceOf(ServiceException.class);
        }

        verify(loginHelper, times(1)).logout();
    }

    @Test
    void meReadsOnlyTheCurrentAppSessionAndMasksContactDetails() {
        AppWorkspaceSessionSnapshotDTO workspace = workspace();
        AppPrincipalSnapshotDTO principal = principal(workspace);
        when(loginHelper.getLoginUser()).thenReturn(new AppLoginUser(principal, "session-1"));
        when(userMapper.selectById(1001L)).thenReturn(activeUser());

        AppMeVo result = service.me();

        assertThat(result.id()).isEqualTo("1001");
        assertThat(result.username()).isEqualTo("creator");
        assertThat(result.phone()).isEqualTo("138****8000");
        assertThat(result.email()).isEqualTo("c***@example.com");
        assertThat(result.roles()).containsExactly("personal_creator");
        assertThat(result.permissions()).containsExactly("creation:script:read");
        assertThat(result.workspace().id()).isEqualTo("workspace-key");
    }

    @Test
    void logoutOnlyDelegatesToTheAppLoginHelper() {
        service.logoutCurrent();

        verify(loginHelper).logout();
    }

    @Test
    void changePasswordUsesOnlyTheCurrentAppPrincipalAndDoesNotIssueAnotherSession() throws Exception {
        AppPrincipalSnapshotDTO principal = principal(workspace());
        when(loginHelper.getLoginUser()).thenReturn(new AppLoginUser(principal, "session-1"));

        service.changePassword(new AppPasswordChangeBo("current-password", "next-password"));

        ArgumentCaptor<ChangeAppPasswordDTO> commandCaptor = ArgumentCaptor.forClass(ChangeAppPasswordDTO.class);
        ArgumentCaptor<AppActorContext> actorCaptor = ArgumentCaptor.forClass(AppActorContext.class);
        verify(identityService).changePassword(commandCaptor.capture(), actorCaptor.capture());
        assertThat(commandCaptor.getValue().userId()).isEqualTo(1001L);
        assertThat(commandCaptor.getValue().currentPassword()).isEqualTo("current-password");
        assertThat(commandCaptor.getValue().newPassword()).isEqualTo("next-password");
        assertThat(commandCaptor.getValue().expectedCredentialRevision()).isEqualTo(2L);
        assertThat(actorCaptor.getValue()).isEqualTo(AppActorContext.appUser(1001L));
        verifyNoInteractions(sessionIssuer);
    }

    @Test
    void listCurrentUserSessionsUsesOnlyTheCurrentAppLoginUserAndReturnsSafeProjection() {
        AppPrincipalSnapshotDTO principal = principal(workspace());
        String sessionId = "9d4cf756-5a8b-424d-86e6-ae4a75ffad8d";
        LocalDateTime lastActiveAt = LocalDateTime.of(2026, 7, 30, 10, 15);
        when(loginHelper.getLoginUser()).thenReturn(new AppLoginUser(principal, "current-session"));
        when(sessionService.currentUserSessions(1001L)).thenReturn(List.of(
            new AppSessionSummaryDTO(sessionId, "creator-web", "web", lastActiveAt, true)));

        List<AppSessionVo> result = service.listCurrentUserSessions();

        assertThat(result).containsExactly(new AppSessionVo(
            sessionId, "creator-web", "web", lastActiveAt, true));
        verify(sessionService).currentUserSessions(1001L);
    }

    @Test
    void revokeOwnSessionUsesOnlyTheCurrentAppLoginUserAndWritesSafeAudit() {
        AppPrincipalSnapshotDTO principal = principal(workspace());
        String sessionId = "9d4cf756-5a8b-424d-86e6-ae4a75ffad8d";
        when(loginHelper.getLoginUser()).thenReturn(new AppLoginUser(principal, "current-session"));
        when(sessionService.findBySessionId(sessionId)).thenReturn(Optional.of(
            new AppSessionSummaryDTO(sessionId, "creator-web", "web", LocalDateTime.now(), false, 1001L)));

        service.revokeOwnSession(sessionId);

        ArgumentCaptor<AppActorContext> actorCaptor = ArgumentCaptor.forClass(AppActorContext.class);
        verify(sessionService).revokeSession(eq(1001L), eq(sessionId), actorCaptor.capture(),
            eq(AppSecurityAuditReason.SESSION_REVOCATION.code()));
        verify(sessionService).findBySessionId(sessionId);
        verify(sessionService, never()).currentUserSessions(anyLong());
        verifyNoInteractions(securityAuditService);
        assertThat(actorCaptor.getValue()).isEqualTo(AppActorContext.appUser(1001L));
    }

    @Test
    void revokeOwnSessionIsIdempotentWhenTheSessionIsAlreadyGone() {
        AppPrincipalSnapshotDTO principal = principal(workspace());
        String sessionId = "9d4cf756-5a8b-424d-86e6-ae4a75ffad8d";
        when(loginHelper.getLoginUser()).thenReturn(new AppLoginUser(principal, "current-session"));
        when(sessionService.findBySessionId(sessionId)).thenReturn(Optional.empty());

        service.revokeOwnSession(sessionId);

        verify(sessionService).findBySessionId(sessionId);
        verify(sessionService, never()).currentUserSessions(anyLong());
        verify(sessionService, never()).revokeSession(anyLong(), any(), any(), any());
        verify(securityAuditService).append(new AppSecurityAuditDTO(
            "app_session", sessionId, "session_revocation_ignored",
            org.dromara.aivideo.identity.domain.AppActorType.APP_USER,
            1001L, null, null, AppSecurityAuditReason.SESSION_REVOCATION.code()));
    }

    @Test
    void revokeOwnSessionDoesNotRevokeAnotherUsersValidSessionButStillAuditsTheRequest() {
        AppPrincipalSnapshotDTO principal = principal(workspace());
        String otherUserSessionId = "ab12cd34-5a8b-424d-86e6-ae4a75ffad8d";
        when(loginHelper.getLoginUser()).thenReturn(new AppLoginUser(principal, "current-session"));
        when(sessionService.findBySessionId(otherUserSessionId)).thenReturn(Optional.of(
            new AppSessionSummaryDTO(otherUserSessionId, "creator-web", "web", LocalDateTime.now(), false, 2002L)));

        assertThatCode(() -> service.revokeOwnSession(otherUserSessionId)).doesNotThrowAnyException();

        verify(sessionService).findBySessionId(otherUserSessionId);
        verify(sessionService, never()).currentUserSessions(anyLong());
        verify(sessionService, never()).revokeSession(anyLong(), any(), any(), any());
        verify(securityAuditService).append(new AppSecurityAuditDTO(
            "app_session", otherUserSessionId, "session_revocation_ignored",
            org.dromara.aivideo.identity.domain.AppActorType.APP_USER,
            1001L, null, null, AppSecurityAuditReason.SESSION_REVOCATION.code()));
    }

    @Test
    void revokeOwnSessionRemainsIdempotentWhenTheSessionDisappearsDuringRevocation() {
        AppPrincipalSnapshotDTO principal = principal(workspace());
        String sessionId = "9d4cf756-5a8b-424d-86e6-ae4a75ffad8d";
        when(loginHelper.getLoginUser()).thenReturn(new AppLoginUser(principal, "current-session"));
        when(sessionService.findBySessionId(sessionId)).thenReturn(
            Optional.of(new AppSessionSummaryDTO(sessionId, "creator-web", "web", LocalDateTime.now(), false, 1001L)),
            Optional.empty());
        doThrow(new ServiceException("创作端会话不存在或已失效"))
            .when(sessionService).revokeSession(eq(1001L), eq(sessionId), any(AppActorContext.class),
                eq(AppSecurityAuditReason.SESSION_REVOCATION.code()));

        service.revokeOwnSession(sessionId);

        verify(sessionService).revokeSession(eq(1001L), eq(sessionId), any(AppActorContext.class),
            eq(AppSecurityAuditReason.SESSION_REVOCATION.code()));
        verify(sessionService, times(2)).findBySessionId(sessionId);
        verify(sessionService, never()).currentUserSessions(anyLong());
        verify(securityAuditService).append(new AppSecurityAuditDTO(
            "app_session", sessionId, "session_revocation_ignored",
            org.dromara.aivideo.identity.domain.AppActorType.APP_USER,
            1001L, null, null, AppSecurityAuditReason.SESSION_REVOCATION.code()));
    }

    @Test
    void rejectsUnsafeSessionIdsBeforeReadingOrAuditingAnything() {
        assertThatThrownBy(() -> service.revokeOwnSession("token-raw-value"))
            .isInstanceOf(ServiceException.class);

        verifyNoInteractions(loginHelper, sessionService, securityAuditService);
    }

    @Test
    void passwordChangeRequestNeverLeaksPlaintextInToString() {
        AppPasswordChangeBo request = new AppPasswordChangeBo("current-password", "next-password");

        assertThat(request.toString()).doesNotContain("current-password", "next-password");
    }

    @Test
    void sensitiveAuthenticationDtosNeverExposePasswordsOrTokensInToString() {
        AppPasswordLoginBo request = new AppPasswordLoginBo("creator@example.com", "raw-password-123");
        AppLoginVo login = new AppLoginVo("app-token-secret", "creator-web", 1800L,
            new org.dromara.aivideo.user.auth.domain.vo.AppWorkspaceVo(
                "workspace-key", "个人工作区", "personal_creator"));
        AppIssuedAccessToken issued = new AppIssuedAccessToken(new AppLoginUser(principal(workspace()), "session-1"),
            "app-token-secret", 1800L);

        assertThat(request.toString()).doesNotContain("raw-password-123", "creator@example.com");
        assertThat(login.toString()).doesNotContain("app-token-secret");
        assertThat(issued.toString()).doesNotContain("app-token-secret");
    }

    @Test
    void passwordRecoveryDtosNeverExposeVerificationChallengesCodesOrTargetsInToString() {
        AppVerificationCodeBo verificationRequest = new AppVerificationCodeBo(
            AppVerificationScenario.PASSWORD_RECOVERY, AppVerificationChannel.EMAIL, "creator@example.com");
        AppPasswordResetBo passwordReset = new AppPasswordResetBo(
            "challenge-opaque-value", "123456", "next-password");
        AppVerificationChallengeVo challenge = new AppVerificationChallengeVo(
            "challenge-opaque-value", "c***@example.com", 600L);

        assertThat(verificationRequest.toString()).doesNotContain("creator@example.com");
        assertThat(passwordReset.toString()).doesNotContain("challenge-opaque-value", "123456", "next-password");
        assertThat(challenge.toString()).doesNotContain("challenge-opaque-value", "c***@example.com");
    }

    private static AppUser activeUser() {
        AppUser user = new AppUser();
        user.setUserId(1001L);
        user.setUsername("creator");
        user.setDisplayName("创作者");
        user.setPhoneNormalized("13800138000");
        user.setEmailNormalized("creator@example.com");
        user.setPersonalTenantId(2001L);
        user.setStatus(AppIdentityStatus.ACTIVE);
        user.setDelFlag("0");
        user.setMustChangePassword(false);
        return user;
    }

    private AppAuthApplicationServiceImpl serviceWithExternalIdentityPorts(
        List<IAppExternalIdentityService> externalIdentityPorts) {
        return new AppAuthApplicationServiceImpl(identityService, sessionService, verificationCodeService,
            loginVerificationPort, securityAuditService, userMapper, workspaceProvider, sessionIssuer, loginHelper,
            loginLogMapper, externalIdentityPorts);
    }

    private static AppWorkspaceSessionSnapshotDTO workspace() {
        return new AppWorkspaceSessionSnapshotDTO("workspace-key", "personal", 2001L, "app_user", 1001L,
            "personal", 1001L, "personal_creator", java.util.Set.of("creation:script:read"), 3L, null);
    }

    private static AppPrincipalSnapshotDTO principal(AppWorkspaceSessionSnapshotDTO workspace) {
        return new AppPrincipalSnapshotDTO(1001L, "creator", "creator-web", 2L, 3L, 4L, 7L, workspace);
    }

    private static AppAuditRequestContextHolder.Scope httpAuditScope(String suffix) {
        return AppAuditRequestContextHolder.bindTrusted(
            new AppAuditRequestContext(("0".repeat(31) + suffix), "127.0.0.1"));
    }
}
