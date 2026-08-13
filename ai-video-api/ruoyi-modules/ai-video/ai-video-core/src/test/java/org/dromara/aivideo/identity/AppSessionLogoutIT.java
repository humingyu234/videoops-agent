package org.dromara.aivideo.identity;

import cn.dev33.satoken.stp.StpUtil;
import org.dromara.aivideo.identity.security.AppLoginUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证创作端正常注销会同步删除 Redis 在线会话索引，并保持运营端登录空间不受影响。
 */
@Tag("dev")
@ResourceLock("sa-token-manager")
class AppSessionLogoutIT {

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
    void removesOnlyTheCurrentAppOnlineIndexWhenLoggingOut() throws Exception {
        long appUserId = 62_002L;
        fixture.insertActiveUserAndClient(appUserId);
        String systemToken = fixture.loginSystem(9_201L);
        AppLoginUser appLoginUser = fixture.loginApp(appUserId);

        assertThat(fixture.sessionService().currentUserSessions(appUserId))
            .extracting(session -> session.sessionId())
            .containsExactly(appLoginUser.sessionId());
        assertThat(fixture.redisKeys(AppSessionIntegrationTestFixture.onlineSessionPattern())).hasSize(1);

        fixture.loginHelper().logout();

        assertThat(fixture.loginHelper().isLogin()).isFalse();
        assertThat(fixture.sessionService().currentUserSessions(appUserId)).isEmpty();
        assertThat(fixture.redisKeys(AppSessionIntegrationTestFixture.onlineSessionPattern())).isEmpty();
        assertThat(StpUtil.isLogin()).isTrue();
        assertThat(fixture.redisKeys("Authorization:login:token:*"))
            .contains("Authorization:login:token:" + systemToken);
    }
}
