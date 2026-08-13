package org.dromara.aivideo.user.security;

import cn.dev33.satoken.exception.NotLoginException;
import jakarta.servlet.DispatcherType;
import org.dromara.aivideo.identity.domain.AppUser;
import org.dromara.aivideo.identity.service.IAppSessionService;
import org.dromara.aivideo.identity.mapper.AppUserMapper;
import org.dromara.aivideo.identity.dto.AppPrincipalSnapshotDTO;
import org.dromara.aivideo.identity.dto.AppAuthClientSnapshotDTO;
import org.dromara.aivideo.identity.security.AppLoginHelper;
import org.dromara.aivideo.identity.security.AppLoginUser;
import org.dromara.aivideo.identity.security.AppSessionRevisionGuard;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Verifies that only the app login namespace can pass creator HTTP authentication.
 */
@Tag("dev")
class AppAuthenticationInterceptorTest {

    @Test
    void skipsAuthenticationForServletAsyncRedispatchAfterTheInitialRequestWasAuthorized() throws Exception {
        AppClientPolicyService clientPolicyService = mock(AppClientPolicyService.class);
        AppLoginHelper loginHelper = mock(AppLoginHelper.class);
        AppSessionRevisionGuard revisionGuard = mock(AppSessionRevisionGuard.class);
        AppUserMapper userMapper = mock(AppUserMapper.class);
        IAppSessionService sessionService = mock(IAppSessionService.class);
        AppAuthenticationInterceptor interceptor = new AppAuthenticationInterceptor(
            clientPolicyService, loginHelper, revisionGuard, userMapper, sessionService);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/studio/creation-assets/88/content");
        request.setDispatcherType(DispatcherType.ASYNC);

        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object())).isTrue();

        verifyNoInteractions(clientPolicyService, loginHelper, revisionGuard, userMapper, sessionService);
    }

    @Test
    void rejectsAProtectedCreatorRequestWhenNoAppSessionExists() {
        AppClientPolicyService clientPolicyService = mock(AppClientPolicyService.class);
        AppLoginHelper loginHelper = mock(AppLoginHelper.class);
        AppSessionRevisionGuard revisionGuard = mock(AppSessionRevisionGuard.class);
        AppUserMapper userMapper = mock(AppUserMapper.class);
        IAppSessionService sessionService = mock(IAppSessionService.class);
        when(loginHelper.isLogin()).thenReturn(false);
        AppAuthenticationInterceptor interceptor = new AppAuthenticationInterceptor(
            clientPolicyService, loginHelper, revisionGuard, userMapper, sessionService);
        MockHttpServletRequest request = protectedRequest("/api/creation/drafts");

        assertThatThrownBy(() -> interceptor.preHandle(request, new MockHttpServletResponse(), new Object()))
            .isInstanceOf(NotLoginException.class);

        verifyNoInteractions(clientPolicyService, revisionGuard, userMapper, sessionService);
    }

    @Test
    void allowsOnlyTheExactPublicPostEndpointsWithoutAnAppSession() throws Exception {
        AppClientPolicyService clientPolicyService = mock(AppClientPolicyService.class);
        AppLoginHelper loginHelper = mock(AppLoginHelper.class);
        AppSessionRevisionGuard revisionGuard = mock(AppSessionRevisionGuard.class);
        AppUserMapper userMapper = mock(AppUserMapper.class);
        IAppSessionService sessionService = mock(IAppSessionService.class);
        AppAuthenticationInterceptor interceptor = new AppAuthenticationInterceptor(
            clientPolicyService, loginHelper, revisionGuard, userMapper, sessionService);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        StrictCredentialHeaders credentials = new StrictCredentialHeaders("desktop", false);
        request.setAttribute(StrictCredentialHeaders.REQUEST_ATTRIBUTE, credentials);
        AppAuthClientSnapshotDTO verifiedClient = new AppAuthClientSnapshotDTO("creator-client", 7L);
        when(clientPolicyService.validate(request, credentials, null)).thenReturn(verifiedClient);

        boolean handled = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        assertThat(handled).isTrue();
        verify(clientPolicyService).validate(request, credentials, null);
        assertThat(request.getAttribute(AppClientPolicyService.VERIFIED_CLIENT_SNAPSHOT_REQUEST_ATTRIBUTE))
            .isEqualTo(verifiedClient);
        verifyNoInteractions(loginHelper, revisionGuard, userMapper, sessionService);
    }

    @Test
    void blocksEveryOtherPathWhenAUserMustChangePassword() {
        AppClientPolicyService clientPolicyService = mock(AppClientPolicyService.class);
        AppLoginHelper loginHelper = mock(AppLoginHelper.class);
        AppSessionRevisionGuard revisionGuard = mock(AppSessionRevisionGuard.class);
        AppUserMapper userMapper = mock(AppUserMapper.class);
        IAppSessionService sessionService = mock(IAppSessionService.class);
        AppAuthenticationInterceptor interceptor = new AppAuthenticationInterceptor(
            clientPolicyService, loginHelper, revisionGuard, userMapper, sessionService);
        AppPrincipalSnapshotDTO principal = new AppPrincipalSnapshotDTO(
            1001L, "creator", "creator-client", 1L, 1L, 1L, 1L, null);
        when(loginHelper.isLogin()).thenReturn(true);
        when(loginHelper.getLoginUser()).thenReturn(new AppLoginUser(principal, "session-1"));
        AppUser user = new AppUser();
        user.setMustChangePassword(true);
        when(userMapper.selectById(1001L)).thenReturn(user);
        MockHttpServletRequest request = protectedRequest("/api/creation/drafts");

        assertThatThrownBy(() -> interceptor.preHandle(request, new MockHttpServletResponse(), new Object()))
            .isInstanceOfSatisfying(AppSecurityException.class,
                exception -> assertThat(exception.getCode()).isEqualTo(AppAuthErrorCodes.APP_PASSWORD_RESET_REQUIRED));

        verify(clientPolicyService).validate(any(), any(), any());
        verify(revisionGuard).checkCurrentSession();
        verify(sessionService, never()).touchCurrentSession();
    }

    @Test
    void allowsOnlyTheExactPutPasswordChangeEndpointWhenAUserMustChangePassword() throws Exception {
        AppClientPolicyService clientPolicyService = mock(AppClientPolicyService.class);
        AppLoginHelper loginHelper = mock(AppLoginHelper.class);
        AppSessionRevisionGuard revisionGuard = mock(AppSessionRevisionGuard.class);
        AppUserMapper userMapper = mock(AppUserMapper.class);
        IAppSessionService sessionService = mock(IAppSessionService.class);
        AppAuthenticationInterceptor interceptor = new AppAuthenticationInterceptor(
            clientPolicyService, loginHelper, revisionGuard, userMapper, sessionService);
        AppPrincipalSnapshotDTO principal = new AppPrincipalSnapshotDTO(
            1001L, "creator", "creator-client", 1L, 1L, 1L, 1L, null);
        when(loginHelper.isLogin()).thenReturn(true);
        when(loginHelper.getLoginUser()).thenReturn(new AppLoginUser(principal, "session-1"));
        AppUser user = new AppUser();
        user.setMustChangePassword(true);
        when(userMapper.selectById(1001L)).thenReturn(user);

        boolean allowed = interceptor.preHandle(protectedRequest("PUT", "/api/auth/password"),
            new MockHttpServletResponse(), new Object());

        assertThat(allowed).isTrue();
        assertThatThrownBy(() -> interceptor.preHandle(protectedRequest("POST", "/api/auth/password"),
            new MockHttpServletResponse(), new Object()))
            .isInstanceOfSatisfying(AppSecurityException.class,
                exception -> assertThat(exception.getCode()).isEqualTo(AppAuthErrorCodes.APP_PASSWORD_RESET_REQUIRED));

        verify(sessionService, times(1)).touchCurrentSession();
    }

    @Test
    void allowsOnlyTheExactSessionManagementEndpointsWhenAUserMustChangePassword() throws Exception {
        AppClientPolicyService clientPolicyService = mock(AppClientPolicyService.class);
        AppLoginHelper loginHelper = mock(AppLoginHelper.class);
        AppSessionRevisionGuard revisionGuard = mock(AppSessionRevisionGuard.class);
        AppUserMapper userMapper = mock(AppUserMapper.class);
        IAppSessionService sessionService = mock(IAppSessionService.class);
        AppAuthenticationInterceptor interceptor = new AppAuthenticationInterceptor(
            clientPolicyService, loginHelper, revisionGuard, userMapper, sessionService);
        AppPrincipalSnapshotDTO principal = new AppPrincipalSnapshotDTO(
            1001L, "creator", "creator-client", 1L, 1L, 1L, 1L, null);
        when(loginHelper.isLogin()).thenReturn(true);
        when(loginHelper.getLoginUser()).thenReturn(new AppLoginUser(principal, "session-1"));
        AppUser user = new AppUser();
        user.setMustChangePassword(true);
        when(userMapper.selectById(1001L)).thenReturn(user);
        String sessionId = "9d4cf756-5a8b-424d-86e6-ae4a75ffad8d";

        assertThat(interceptor.preHandle(protectedRequest("GET", "/api/auth/sessions"),
            new MockHttpServletResponse(), new Object())).isTrue();
        assertThat(interceptor.preHandle(protectedRequest("DELETE", "/api/auth/sessions/" + sessionId),
            new MockHttpServletResponse(), new Object())).isTrue();
        assertPasswordResetRequired(() -> interceptor.preHandle(protectedRequest("POST", "/api/auth/sessions"),
            new MockHttpServletResponse(), new Object()));
        assertPasswordResetRequired(() -> interceptor.preHandle(protectedRequest("DELETE", "/api/auth/sessions"),
            new MockHttpServletResponse(), new Object()));
        assertPasswordResetRequired(() -> interceptor.preHandle(
            protectedRequest("DELETE", "/api/auth/sessions/not-a-uuid"),
            new MockHttpServletResponse(), new Object()));
        assertPasswordResetRequired(() -> interceptor.preHandle(
            protectedRequest("DELETE", "/api/auth/sessions/9D4CF756-5A8B-424D-86E6-AE4A75FFAD8D"),
            new MockHttpServletResponse(), new Object()));
        assertPasswordResetRequired(() -> interceptor.preHandle(
            protectedRequest("GET", "/api/auth/sessions/" + sessionId), new MockHttpServletResponse(), new Object()));
        assertPasswordResetRequired(() -> interceptor.preHandle(
            protectedRequest("DELETE", "/api/auth/sessions/" + sessionId + "/extra"),
            new MockHttpServletResponse(), new Object()));
        assertPasswordResetRequired(() -> interceptor.preHandle(
            protectedRequest("POST", "/api/auth/social-bindings"), new MockHttpServletResponse(), new Object()));
        assertPasswordResetRequired(() -> interceptor.preHandle(protectedRequest("GET", "/api/creation/drafts"),
            new MockHttpServletResponse(), new Object()));

        verify(sessionService, times(2)).touchCurrentSession();
    }

    @Test
    void refreshesLastActiveOnlyAfterTheProtectedRequestPassesEverySecurityCheck() throws Exception {
        AppClientPolicyService clientPolicyService = mock(AppClientPolicyService.class);
        AppLoginHelper loginHelper = mock(AppLoginHelper.class);
        AppSessionRevisionGuard revisionGuard = mock(AppSessionRevisionGuard.class);
        AppUserMapper userMapper = mock(AppUserMapper.class);
        IAppSessionService sessionService = mock(IAppSessionService.class);
        AppAuthenticationInterceptor interceptor = new AppAuthenticationInterceptor(
            clientPolicyService, loginHelper, revisionGuard, userMapper, sessionService);
        AppPrincipalSnapshotDTO principal = new AppPrincipalSnapshotDTO(
            1001L, "creator", "creator-client", 1L, 1L, 1L, 1L, null);
        when(loginHelper.isLogin()).thenReturn(true);
        when(loginHelper.getLoginUser()).thenReturn(new AppLoginUser(principal, "session-1"));
        when(userMapper.selectById(1001L)).thenReturn(new AppUser());

        boolean allowed = interceptor.preHandle(protectedRequest("GET", "/api/auth/sessions"),
            new MockHttpServletResponse(), new Object());

        assertThat(allowed).isTrue();
        verify(revisionGuard).checkCurrentSession();
        verify(sessionService).touchCurrentSession();
    }

    private static MockHttpServletRequest protectedRequest(String path) {
        return protectedRequest("GET", path);
    }

    private static MockHttpServletRequest protectedRequest(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setAttribute(StrictCredentialHeaders.REQUEST_ATTRIBUTE,
            new StrictCredentialHeaders("desktop", true));
        return request;
    }

    private static void assertPasswordResetRequired(ThrowingRunnable request) {
        assertThatThrownBy(request::run)
            .isInstanceOfSatisfying(AppSecurityException.class,
                exception -> assertThat(exception.getCode()).isEqualTo(AppAuthErrorCodes.APP_PASSWORD_RESET_REQUIRED));
    }

    @FunctionalInterface
    private interface ThrowingRunnable {

        void run() throws Exception;
    }
}
