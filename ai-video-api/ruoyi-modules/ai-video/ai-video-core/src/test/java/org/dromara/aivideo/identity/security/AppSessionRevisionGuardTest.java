package org.dromara.aivideo.identity.security;

import org.dromara.aivideo.identity.service.IAppSessionService;
import org.dromara.aivideo.identity.domain.AppAuthClient;
import org.dromara.aivideo.identity.domain.AppIdentityStatus;
import org.dromara.aivideo.identity.domain.AppUser;
import org.dromara.aivideo.identity.mapper.AppAuthClientMapper;
import org.dromara.aivideo.identity.mapper.AppUserMapper;
import org.dromara.aivideo.identity.dto.AppPrincipalSnapshotDTO;
import org.dromara.aivideo.identity.domain.AppSessionInvalidationReason;
import org.dromara.aivideo.identity.dto.AppWorkspaceSessionSnapshotDTO;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证创作端会话修订守卫只查询 app 身份表并先撤销失效会话。
 */
@Tag("dev")
class AppSessionRevisionGuardTest {

    @Test
    void revokesTheCurrentAppSessionBeforeReportingAStaleUserRevision() {
        AppLoginHelper loginHelper = mock(AppLoginHelper.class);
        AppUserMapper userMapper = mock(AppUserMapper.class);
        AppAuthClientMapper clientMapper = mock(AppAuthClientMapper.class);
        IAppSessionService sessionService = mock(IAppSessionService.class);
        when(loginHelper.getLoginUser()).thenReturn(loginUser());
        when(userMapper.selectOne(any())).thenReturn(activeUser(8L, 3L, 4L));
        when(clientMapper.selectOne(any())).thenReturn(activeClient(6L));
        AppSessionRevisionGuard guard = new AppSessionRevisionGuard(loginHelper, userMapper, clientMapper, sessionService);

        assertThatThrownBy(guard::checkCurrentSession)
            .isInstanceOf(ServiceException.class)
            .hasFieldOrPropertyWithValue("code", 46131);

        org.mockito.InOrder invalidationThenLogout = inOrder(sessionService, loginHelper);
        invalidationThenLogout.verify(sessionService)
            .invalidateUserSessions(1001L, AppSessionInvalidationReason.CREDENTIAL_CHANGED);
        invalidationThenLogout.verify(loginHelper).logout();
    }

    @Test
    void acceptsMatchingRevisionsWithoutTouchingTheCurrentAppSession() {
        AppLoginHelper loginHelper = mock(AppLoginHelper.class);
        AppUserMapper userMapper = mock(AppUserMapper.class);
        AppAuthClientMapper clientMapper = mock(AppAuthClientMapper.class);
        IAppSessionService sessionService = mock(IAppSessionService.class);
        when(loginHelper.getLoginUser()).thenReturn(loginUser());
        when(userMapper.selectOne(any())).thenReturn(activeUser(2L, 3L, 4L));
        when(clientMapper.selectOne(any())).thenReturn(activeClient(6L));
        AppSessionRevisionGuard guard = new AppSessionRevisionGuard(loginHelper, userMapper, clientMapper, sessionService);

        assertThatCode(guard::checkCurrentSession).doesNotThrowAnyException();

        verify(loginHelper, never()).logout();
        verify(sessionService, never()).invalidateUserSessions(org.mockito.ArgumentMatchers.anyLong(), any());
    }

    private AppLoginUser loginUser() {
        AppWorkspaceSessionSnapshotDTO workspace = new AppWorkspaceSessionSnapshotDTO(
            "opaque-personal-key", "personal", 2001L, "app_user", 1001L,
            "personal", 1001L, "personal_creator", Set.of("copy:generate"), 3L, null);
        return new AppLoginUser(new AppPrincipalSnapshotDTO(
            1001L, "creator", "desktop", 2L, 3L, 4L, 6L, workspace), "random-session-id");
    }

    private AppUser activeUser(long credentialRevision, long identityRevision, long permissionRevision) {
        AppUser user = new AppUser();
        user.setUserId(1001L);
        user.setStatus(AppIdentityStatus.ACTIVE);
        user.setCredentialRevision(credentialRevision);
        user.setIdentityRevision(identityRevision);
        user.setPermissionRevision(permissionRevision);
        return user;
    }

    private AppAuthClient activeClient(long clientRevision) {
        AppAuthClient client = new AppAuthClient();
        client.setClientId("desktop");
        client.setStatus(AppIdentityStatus.ACTIVE);
        client.setClientRevision(clientRevision);
        return client;
    }
}
