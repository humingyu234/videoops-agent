package org.dromara.aivideo.identity;

import cn.dev33.satoken.stp.StpUtil;
import org.dromara.aivideo.identity.domain.AppSessionInvalidationReason;
import org.dromara.aivideo.identity.dto.AppWorkspaceSessionSnapshotDTO;
import org.dromara.aivideo.identity.security.AppLoginUser;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证工作区切换后的组织会话失效只影响指定组织，不影响其他组织、个人工作区或运营端登录态。
 */
@Tag("dev")
@ResourceLock("sa-token-manager")
class AppSessionWorkspaceInvalidationIT {

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
    void rejectsCallerSuppliedWorkspaceFactsAndInvalidatesOnlyTheSelectedOrganizationSession() throws Exception {
        long userId = 64_001L;
        long organizationA = 74_001L;
        long organizationB = 74_002L;
        fixture.insertActiveUserAndClient(userId);
        String systemToken = fixture.loginSystem(9_300L);

        AppLoginUser personalLogin = fixture.loginApp(userId);
        AppWorkspaceSessionSnapshotDTO canonicalPersonalWorkspace = fixture.loginHelper().getPrincipal().workspace();

        assertThatThrownBy(() -> fixture.sessionService().replaceWorkspace(organizationWorkspace(organizationA)))
            .isInstanceOf(ServiceException.class)
            .hasFieldOrPropertyWithValue("code", 46126);
        assertThatThrownBy(() -> fixture.sessionService().replaceWorkspace(new AppWorkspaceSessionSnapshotDTO(
            "wrong-workspace-key", "personal", 1L, "app_user", userId, "personal", userId,
            "personal_creator", Set.of(), 1L, null)))
            .isInstanceOf(ServiceException.class)
            .hasFieldOrPropertyWithValue("code", 46126);
        assertThatThrownBy(() -> fixture.sessionService().replaceWorkspace(new AppWorkspaceSessionSnapshotDTO(
            canonicalPersonalWorkspace.workspaceKey(), "personal", 1L, "app_user", userId, "personal", userId,
            "personal_creator", Set.of(), 1L, 1L)))
            .isInstanceOf(ServiceException.class)
            .hasFieldOrPropertyWithValue("code", 46126);
        assertThat(fixture.loginHelper().getPrincipal().workspace()).isEqualTo(canonicalPersonalWorkspace);

        AppWorkspaceSessionSnapshotDTO forgedPersonalWorkspace = new AppWorkspaceSessionSnapshotDTO(
            canonicalPersonalWorkspace.workspaceKey(),
            "personal",
            99_999L,
            "organization",
            organizationA,
            "organization",
            organizationA,
            "organization_owner",
            Set.of("aivideo:quota:use", "aivideo:studio:generate"),
            99L,
            null);
        assertThat(fixture.sessionService().replaceWorkspace(forgedPersonalWorkspace))
            .extracting(principal -> principal.workspace())
            .isEqualTo(canonicalPersonalWorkspace);
        assertThat(fixture.loginHelper().getPrincipal().workspace()).isEqualTo(canonicalPersonalWorkspace);

        AppLoginUser organizationALogin = fixture.loginAppWithTrustedWorkspaceForTest(userId,
            organizationWorkspace(organizationA));
        AppLoginUser organizationBLogin = fixture.loginAppWithTrustedWorkspaceForTest(userId,
            organizationWorkspace(organizationB));

        assertThat(fixture.sessionService().currentUserSessions(userId))
            .extracting(session -> session.sessionId())
            .containsExactlyInAnyOrder(organizationALogin.sessionId(), organizationBLogin.sessionId(),
                personalLogin.sessionId());
        assertThat(fixture.redisKeys(AppSessionIntegrationTestFixture.onlineSessionPattern())).hasSize(3);

        fixture.sessionService().invalidateOrganizationSessions(organizationA,
            AppSessionInvalidationReason.MEMBERSHIP_CHANGED);

        assertThat(fixture.sessionService().currentUserSessions(userId))
            .extracting(session -> session.sessionId())
            .containsExactlyInAnyOrder(organizationBLogin.sessionId(), personalLogin.sessionId());
        assertThat(fixture.redisKeys(AppSessionIntegrationTestFixture.onlineSessionPattern())).hasSize(2);
        assertThat(fixture.loginHelper().isLogin()).isTrue();
        assertThat(StpUtil.isLogin()).isTrue();
        assertThat(fixture.redisKeys("Authorization:login:token:*"))
            .contains("Authorization:login:token:" + systemToken);
    }

    /**
     * 构造组织工作区快照，供 replaceWorkspace 联测覆盖组织选择性失效。
     *
     * @param organizationId 组织编号
     * @return 组织工作区会话快照
     */
    private AppWorkspaceSessionSnapshotDTO organizationWorkspace(long organizationId) {
        return new AppWorkspaceSessionSnapshotDTO(
            "organization-workspace-" + organizationId,
            "organization",
            organizationId,
            "organization",
            organizationId,
            "organization",
            organizationId,
            "organization_creator",
            Set.of("aivideo:studio:query"),
            1L,
            1L);
    }
}
