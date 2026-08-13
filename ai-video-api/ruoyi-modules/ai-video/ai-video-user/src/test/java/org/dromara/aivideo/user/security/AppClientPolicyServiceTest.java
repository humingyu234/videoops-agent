package org.dromara.aivideo.user.security;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.dromara.aivideo.identity.domain.AppAuthClient;
import org.dromara.aivideo.identity.domain.AppIdentityStatus;
import org.dromara.aivideo.identity.mapper.AppAuthClientMapper;
import org.dromara.aivideo.identity.dto.AppPrincipalSnapshotDTO;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies that creator client policy has no operating-side fallback.
 */
@Tag("dev")
class AppClientPolicyServiceTest {

    @Test
    void rejectsAnUnknownCreatorClientWithoutTryingAnotherIdentityStore() {
        AppAuthClientMapper appClientMapper = mock(AppAuthClientMapper.class);
        when(appClientMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        AppClientPolicyService service = new AppClientPolicyService(appClientMapper);

        assertThatThrownBy(() -> service.validate(publicLoginRequest(), credentials(), null))
            .isInstanceOfSatisfying(AppSecurityException.class,
                exception -> assertThat(exception.getCode()).isEqualTo(AppAuthErrorCodes.APP_AUTH_CLIENT_UNAVAILABLE));
    }

    @Test
    void acceptsAnActiveCreatorClientWhoseFrozenTokenClientAndRevisionMatch() {
        AppAuthClientMapper appClientMapper = mock(AppAuthClientMapper.class);
        when(appClientMapper.selectOne(any(Wrapper.class))).thenReturn(activeClient());
        AppClientPolicyService service = new AppClientPolicyService(appClientMapper);

        service.validate(protectedRequest(), credentials(), new AppPrincipalSnapshotDTO(
            1001L, "creator", "creator-client", 1L, 1L, 1L, 7L, null));
    }

    @Test
    void rejectsAProtectedRequestWhenTheTokenWasIssuedToAnotherCreatorClient() {
        AppAuthClientMapper appClientMapper = mock(AppAuthClientMapper.class);
        when(appClientMapper.selectOne(any(Wrapper.class))).thenReturn(activeClient());
        AppClientPolicyService service = new AppClientPolicyService(appClientMapper);

        assertThatThrownBy(() -> service.validate(protectedRequest(), credentials(), new AppPrincipalSnapshotDTO(
            1001L, "creator", "other-client", 1L, 1L, 1L, 7L, null)))
            .isInstanceOfSatisfying(AppSecurityException.class,
                exception -> assertThat(exception.getCode()).isEqualTo(AppAuthErrorCodes.APP_AUTH_CLIENT_UNAVAILABLE));
    }

    @Test
    void enforcesTheExactConfiguredGrantForEachPublicCreatorLoginEndpoint() {
        AppAuthClientMapper appClientMapper = mock(AppAuthClientMapper.class);
        AppAuthClient client = activeClient();
        client.setGrantTypes("sms,social,mini_program");
        when(appClientMapper.selectOne(any(Wrapper.class))).thenReturn(client);
        AppClientPolicyService service = new AppClientPolicyService(appClientMapper);

        assertThat(service.validate(publicRequest("/api/auth/sms-logins"), credentials(), null))
            .isEqualTo(new org.dromara.aivideo.identity.dto.AppAuthClientSnapshotDTO(
                "creator-client", 7L));
        assertThat(service.validate(publicRequest("/api/auth/social-logins"), credentials(), null))
            .isEqualTo(new org.dromara.aivideo.identity.dto.AppAuthClientSnapshotDTO(
                "creator-client", 7L));
        assertThat(service.validate(publicRequest("/api/auth/mini-program-logins"), credentials(), null))
            .isEqualTo(new org.dromara.aivideo.identity.dto.AppAuthClientSnapshotDTO(
                "creator-client", 7L));
        assertThatThrownBy(() -> service.validate(publicRequest("/api/auth/email-logins"), credentials(), null))
            .isInstanceOfSatisfying(AppSecurityException.class,
                exception -> assertThat(exception.getCode()).isEqualTo(AppAuthErrorCodes.APP_AUTH_CLIENT_UNAVAILABLE));
    }

    @Test
    void rejectsAForgedForwardedIpWhenTheDirectPeerIsNotWhitelisted() {
        AppAuthClientMapper appClientMapper = mock(AppAuthClientMapper.class);
        when(appClientMapper.selectOne(any(Wrapper.class))).thenReturn(activeClient());
        AppClientPolicyService service = new AppClientPolicyService(appClientMapper);
        MockHttpServletRequest request = protectedRequest();
        request.setRemoteAddr("203.0.113.99");
        request.addHeader("X-Forwarded-For", "127.0.0.1");

        assertThatThrownBy(() -> service.validate(request, credentials(), new AppPrincipalSnapshotDTO(
            1001L, "creator", "creator-client", 1L, 1L, 1L, 7L, null)))
            .isInstanceOfSatisfying(AppSecurityException.class,
                exception -> assertThat(exception.getCode()).isEqualTo(AppAuthErrorCodes.APP_AUTH_CLIENT_UNAVAILABLE));
    }

    private static AppAuthClient activeClient() {
        AppAuthClient client = new AppAuthClient();
        client.setClientId("creator-client");
        client.setClientKey("desktop");
        client.setGrantTypes("password");
        client.setAccessPaths("/api/**");
        client.setIpWhitelist("127.0.0.1/32");
        client.setClientRevision(7L);
        client.setStatus(AppIdentityStatus.ACTIVE);
        client.setDelFlag("0");
        return client;
    }

    private static StrictCredentialHeaders credentials() {
        return new StrictCredentialHeaders("desktop", true);
    }

    private static MockHttpServletRequest publicLoginRequest() {
        return publicRequest("/api/auth/login");
    }

    private static MockHttpServletRequest publicRequest(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setRemoteAddr("127.0.0.1");
        return request;
    }

    private static MockHttpServletRequest protectedRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/me");
        request.setRemoteAddr("127.0.0.1");
        return request;
    }
}
