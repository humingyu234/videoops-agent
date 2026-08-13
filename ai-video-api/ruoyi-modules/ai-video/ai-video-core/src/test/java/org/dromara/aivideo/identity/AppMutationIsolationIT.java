package org.dromara.aivideo.identity;

import cn.dev33.satoken.stp.StpUtil;
import org.dromara.aivideo.identity.dto.BindSocialIdentityDTO;
import org.dromara.aivideo.identity.dto.ChangeAppPasswordDTO;
import org.dromara.aivideo.identity.dto.ChangeAppUserStatusDTO;
import org.dromara.aivideo.identity.dto.ResetAppPasswordDTO;
import org.dromara.aivideo.identity.domain.AppIdentityStatus;
import org.dromara.aivideo.identity.security.AppActorContext;
import org.dromara.aivideo.identity.security.AppLoginUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 Task5 身份变更事件只会在事务提交后撤销对应 app 会话，且不会影响默认 login 命名空间。
 */
@Tag("dev")
@ResourceLock("sa-token-manager")
class AppMutationIsolationIT {

    private AppSessionIntegrationTestFixture fixture;

    @BeforeEach
    void setUp() throws Exception {
        fixture = AppSessionIntegrationTestFixture.create();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (fixture != null) {
            try {
                fixture.close();
            } finally {
                fixture = null;
            }
        }
    }

    @Test
    void invalidatesOnlyCommittedUsersAppSessionsAndKeepsDefaultLoginSession() throws Exception {
        long committedUserId = 61_001L;
        long rolledBackUserId = 61_002L;
        fixture.insertActiveUserAndClient(committedUserId);
        fixture.insertActiveUserAndClient(rolledBackUserId);
        fixture.prepareRolePermissionMutation(committedUserId);
        fixture.prepareRolePermissionMutation(rolledBackUserId);
        fixture.loginSystem(9_100L);
        AppLoginUser committedLogin = fixture.loginApp(committedUserId);
        AppLoginUser rolledBackLogin = fixture.loginApp(rolledBackUserId);

        assertThat(fixture.sessionService().currentUserSessions(committedUserId))
            .extracting(session -> session.sessionId())
            .containsExactly(committedLogin.sessionId());
        assertThat(fixture.sessionService().currentUserSessions(rolledBackUserId))
            .extracting(session -> session.sessionId())
            .containsExactly(rolledBackLogin.sessionId());

        fixture.replaceRolePermissionsAndCommit(committedUserId);
        fixture.replaceRolePermissionsAndRollback(rolledBackUserId);

        assertThat(fixture.sessionService().currentUserSessions(committedUserId)).isEmpty();
        assertThat(fixture.sessionService().currentUserSessions(rolledBackUserId))
            .extracting(session -> session.sessionId())
            .containsExactly(rolledBackLogin.sessionId());
        assertThat(StpUtil.isLogin()).isTrue();
    }

    @Test
    void clearsOnlyAppSessionsAfterCommittedIdentityMutationsAndKeepsSystemLogin() throws Exception {
        long passwordUserId = 61_101L;
        long resetUserId = 61_102L;
        long disabledUserId = 61_103L;
        long socialUserId = 61_104L;
        fixture.insertActiveUserAndClient(passwordUserId);
        fixture.insertActiveUserAndClient(resetUserId);
        fixture.insertActiveUserAndClient(disabledUserId);
        fixture.insertActiveUserAndClient(socialUserId);
        fixture.authorizeSystemActor(9_100L);
        fixture.loginSystem(9_100L);

        assertCommittedMutationInvalidatesOnlyAppSession(passwordUserId, () -> fixture.withNonHttpAudit(() ->
            fixture.identityService().changePassword(
                new ChangeAppPasswordDTO(passwordUserId, fixture.initialPassword(), "Password#Next123", 1L),
                AppActorContext.appUser(passwordUserId))));
        assertCommittedMutationInvalidatesOnlyAppSession(resetUserId, () -> fixture.withNonHttpAudit(() ->
            fixture.identityService().resetPassword(
                new ResetAppPasswordDTO(resetUserId, "Reset#Next123", 1L), AppActorContext.sysUser(9_100L))));
        assertCommittedMutationInvalidatesOnlyAppSession(disabledUserId, () -> fixture.withNonHttpAudit(() ->
            fixture.identityService().changeStatus(
                new ChangeAppUserStatusDTO(disabledUserId, AppIdentityStatus.DISABLED, 1L), AppActorContext.sysUser(9_100L))));

        AppLoginUser socialBindingLogin = fixture.loginApp(socialUserId);
        assertThat(fixture.sessionService().currentUserSessions(socialUserId))
            .extracting(session -> session.sessionId())
            .containsExactly(socialBindingLogin.sessionId());
        fixture.withNonHttpAudit(() -> fixture.identityService().bindSocialIdentity(
            new BindSocialIdentityDTO(socialUserId, "github", "session-event-subject", 1L),
            AppActorContext.appUser(socialUserId)));
        assertAppSessionInvalidatedAndSystemLoginPreserved(socialUserId);

        long socialIdentityId = fixture.socialIdentityId(socialUserId, "github");
        AppLoginUser socialUnbindingLogin = fixture.loginApp(socialUserId);
        assertThat(fixture.sessionService().currentUserSessions(socialUserId))
            .extracting(session -> session.sessionId())
            .containsExactly(socialUnbindingLogin.sessionId());
        fixture.withNonHttpAudit(() -> fixture.identityService().unbindSocialIdentity(
            socialUserId, socialIdentityId, AppActorContext.appUser(socialUserId)));
        assertAppSessionInvalidatedAndSystemLoginPreserved(socialUserId);
    }

    @Test
    void retainsAppAndSystemSessionsWhenAnIdentityMutationRollsBack() throws Exception {
        long userId = 61_105L;
        fixture.insertActiveUserAndClient(userId);
        fixture.loginSystem(9_100L);
        AppLoginUser login = fixture.loginApp(userId);

        fixture.withRollbackOnlyTransaction(() -> fixture.withNonHttpAudit(() ->
            fixture.identityService().changePassword(
                new ChangeAppPasswordDTO(userId, fixture.initialPassword(), "Rollback#Next123", 1L),
                AppActorContext.appUser(userId))));

        assertThat(fixture.sessionService().currentUserSessions(userId))
            .extracting(session -> session.sessionId())
            .containsExactly(login.sessionId());
        assertThat(fixture.loginHelper().isLogin()).isTrue();
        assertThat(StpUtil.isLogin()).isTrue();
    }

    private void assertCommittedMutationInvalidatesOnlyAppSession(long userId, Runnable mutation) throws Exception {
        AppLoginUser login = fixture.loginApp(userId);
        assertThat(fixture.sessionService().currentUserSessions(userId))
            .extracting(session -> session.sessionId())
            .containsExactly(login.sessionId());

        mutation.run();

        assertAppSessionInvalidatedAndSystemLoginPreserved(userId);
    }

    private void assertAppSessionInvalidatedAndSystemLoginPreserved(long userId) {
        assertThat(fixture.sessionService().currentUserSessions(userId)).isEmpty();
        assertThat(fixture.loginHelper().isLogin()).isFalse();
        assertThat(StpUtil.isLogin()).isTrue();
    }
}
