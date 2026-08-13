package org.dromara.aivideo.identity;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.context.model.SaStorage;
import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.util.SaTokenConsts;
import org.dromara.aivideo.identity.security.AppLoginUser;
import org.dromara.aivideo.identity.security.AppActorContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 app Sa-Token 命名空间、Redis 在线索引与默认 login 命名空间完全隔离。
 */
@Tag("dev")
@ResourceLock("sa-token-manager")
class AppSessionNamespaceIT {

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
    void writesDistinctRawTokenNamespacesAndInvalidatesOnlyAppSessions() throws Exception {
        long appUserId = 62_001L;
        fixture.insertActiveUserAndClient(appUserId);
        String sameRawToken = "task6-same-raw-" + UUID.randomUUID();
        String systemToken = fixture.loginSystem(9_200L, sameRawToken);
        assertDefaultLoginAndStorage(true, false);

        StpLogic appLogic = SaManager.getStpLogic("app", false);
        appLogic.login(appUserId, appLogic.createSaLoginParameter().setToken(sameRawToken));

        assertThat(fixture.redisKeys("Authorization:login:token:*"))
            .containsExactly("Authorization:login:token:" + sameRawToken);
        assertThat(fixture.redisKeys("Authorization:app:token:*"))
            .containsExactly("Authorization:app:token:" + sameRawToken);
        assertDefaultLoginAndStorage(true, true);

        appLogic.logout();

        assertThat(fixture.redisKeys("Authorization:app:token:*"))
            .doesNotContain("Authorization:app:token:" + sameRawToken);
        assertThat(fixture.redisKeys("Authorization:login:token:*"))
            .contains("Authorization:login:token:" + sameRawToken);
        assertDefaultLoginAndStorage(true, false);

        AppLoginUser appLogin = fixture.loginApp(appUserId);
        assertDefaultLoginAndStorage(true, true);

        assertThat(fixture.redisKeys("Authorization:login:token:*"))
            .contains("Authorization:login:token:" + systemToken);
        assertThat(fixture.redisKeys("Authorization:app:token:*")).isNotEmpty();
        assertThat(fixture.redisKeys(AppSessionIntegrationTestFixture.onlineSessionPattern())).hasSize(1);
        assertThat(fixture.sessionService().currentUserSessions(appUserId))
            .extracting(session -> session.sessionId())
            .containsExactly(appLogin.sessionId());

        fixture.sessionService().revokeSession(appUserId, appLogin.sessionId(),
            AppActorContext.appUser(appUserId), "Task6 Redis 索引往返撤销");

        assertThat(fixture.loginHelper().isLogin()).isFalse();
        assertThat(fixture.sessionService().currentUserSessions(appUserId)).isEmpty();
        assertThat(fixture.redisKeys(AppSessionIntegrationTestFixture.onlineSessionPattern())).isEmpty();
        assertDefaultLoginAndStorage(true, true);

        AppLoginUser reestablishedLogin = fixture.loginApp(appUserId);
        assertThat(fixture.sessionService().currentUserSessions(appUserId))
            .extracting(session -> session.sessionId())
            .containsExactly(reestablishedLogin.sessionId());
        assertDefaultLoginAndStorage(true, true);

        fixture.invalidateUserSessions(appUserId);

        assertThat(fixture.sessionService().currentUserSessions(appUserId)).isEmpty();
        assertThat(fixture.redisKeys(AppSessionIntegrationTestFixture.onlineSessionPattern())).isEmpty();
        assertThat(fixture.redisKeys("Authorization:login:token:*"))
            .contains("Authorization:login:token:" + systemToken);
        assertDefaultLoginAndStorage(true, true);
    }

    /**
     * 断言当前 mock 请求存储内的 login 与 app 新建令牌槽位状态，不输出任何令牌原文。
     *
     * @param defaultLoginExpected 默认 login 命名空间是否仍有效
     * @param appStorageExpected app 命名空间的新建令牌槽位是否存在
     */
    private void assertDefaultLoginAndStorage(boolean defaultLoginExpected, boolean appStorageExpected) {
        SaStorage storage = SaHolder.getStorage();
        assertThat(StpUtil.isLogin()).isEqualTo(defaultLoginExpected);
        assertThat(storage.get(SaTokenConsts.JUST_CREATED) != null).isEqualTo(defaultLoginExpected);
        assertThat(storage.get(SaTokenConsts.JUST_CREATED + "app") != null).isEqualTo(appStorageExpected);
    }
}
