package org.dromara.aivideo.identity.security;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.config.SaTokenConfig;
import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.context.mock.SaTokenContextMockUtil;
import cn.dev33.satoken.dao.SaTokenDaoDefaultImpl;
import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.util.SaTokenConsts;
import org.dromara.aivideo.identity.event.AppSessionEndedEvent;
import org.dromara.aivideo.identity.domain.AppAuthClient;
import org.dromara.aivideo.identity.domain.AppIdentityStatus;
import org.dromara.aivideo.identity.mapper.AppAuthClientMapper;
import org.dromara.aivideo.identity.dto.AppPrincipalSnapshotDTO;
import org.dromara.aivideo.identity.dto.AppWorkspaceSessionSnapshotDTO;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 验证 app 登录逻辑只信任当前 app 令牌会话中的主体快照。
 */
@Tag("dev")
@ResourceLock("sa-token-manager")
class AppStpLogicBehaviorTest {

    private static final int APP_AUTH_CLIENT_UNAVAILABLE_CODE = 46130;

    private StpLogic defaultLoginLogic;
    private SaTokenConfig previousDefaultLoginConfig;

    @BeforeEach
    void setUp() {
        SaManager.removeStpLogic("app");
        SaManager.setSaTokenDao(new SaTokenDaoDefaultImpl());
        SaTokenContextMockUtil.setMockContext();
        defaultLoginLogic = StpUtil.getStpLogic();
        previousDefaultLoginConfig = defaultLoginLogic.getConfig();
        defaultLoginLogic.setConfig(defaultLoginConfig());
    }

    @AfterEach
    void tearDown() {
        defaultLoginLogic.setConfig(previousDefaultLoginConfig);
        SaTokenContextMockUtil.clearContext();
        SaManager.removeStpLogic("app");
        SaManager.setSaTokenDao(new SaTokenDaoDefaultImpl());
    }

    @Test
    void replacesAnAccidentallyCreatedGenericAppLogicAndUsesOnlyCurrentAppSessionPermissions() {
        StpLogic genericLogic = SaManager.getStpLogic("app");
        AppStpLogicRegistrar registrar = new AppStpLogicRegistrar(properties());
        AppLoginHelper loginHelper = new AppLoginHelper(registrar, ignored -> {
        }, activeClientMapper(6L, 600L, 120L));
        AppStpLogic appLogic = registrar.logic();
        loginHelper.login(principal(), "desktop");

        assertThat(appLogic).isNotSameAs(genericLogic);
        assertThat(SaManager.getStpLogic("app", false)).isSameAs(appLogic);
        assertThat(appLogic.getLoginType()).isEqualTo("app");
        assertThat(appLogic.getConfig().getDynamicActiveTimeout()).isTrue();
        assertThat(appLogic.getPermissionList(1001L)).containsExactlyInAnyOrder("copy:generate", "copy:read");
        assertThat(appLogic.getRoleList(1001L)).containsExactly("personal_creator");
        assertThat(appLogic.getPermissionList(1002L)).isEmpty();
        assertThat(appLogic.getRoleList(1002L)).isEmpty();
    }

    @Test
    void logsOutTheNewAppSessionWhenTheOnlineSessionIndexListenerRejectsIt() {
        AppStpLogicRegistrar registrar = new AppStpLogicRegistrar(properties());
        ApplicationEventPublisher failingPublisher = ignored -> {
            throw new IllegalStateException("online index unavailable");
        };
        AppLoginHelper loginHelper = new AppLoginHelper(registrar, failingPublisher, activeClientMapper(6L, 600L, 120L));
        StpUtil.login(9001L);

        org.assertj.core.api.ThrowableAssert.ThrowingCallable login = () -> loginHelper.login(principal(), "desktop");

        org.assertj.core.api.Assertions.assertThatThrownBy(login)
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("online index unavailable");
        assertThat(loginHelper.isLogin()).isFalse();
        assertThat(StpUtil.isLogin()).isTrue();
    }

    @Test
    void appliesTheVerifiedAppClientTimeoutsInsteadOfTheGlobalDefaults() {
        AppStpLogicRegistrar registrar = new AppStpLogicRegistrar(properties());
        AppLoginHelper loginHelper = new AppLoginHelper(registrar, ignored -> {
        }, activeClientMapper(6L, 120L, 45L));

        loginHelper.login(principal(), "desktop");

        AppStpLogic appLogic = registrar.logic();
        assertThat(loginHelper.getCurrentTokenTimeout()).isBetween(119L, 120L);
        assertThat(appLogic.getTokenUseActiveTimeout(appLogic.getTokenValue())).isEqualTo(45L);
    }

    @Test
    void usesTheShortestPositiveTokenAndActiveTimeoutForTheOnlineSessionIndex() {
        AppStpLogicRegistrar registrar = new AppStpLogicRegistrar(properties());
        AppLoginHelper loginHelper = new AppLoginHelper(registrar, ignored -> {
        }, activeClientMapper(6L, 120L, 45L));

        loginHelper.login(principal(), "desktop");

        assertThat(loginHelper.getCurrentSessionIndexTimeout()).isBetween(44L, 45L);
    }

    @Test
    void doesNotReturnAnIndexTtlForAnIdleExpiredAppToken() {
        AppStpLogicRegistrar registrar = new AppStpLogicRegistrar(properties());
        AppLoginHelper loginHelper = new AppLoginHelper(registrar, ignored -> {
        }, activeClientMapper(6L, 120L, 1L));

        loginHelper.login(principal(), "desktop");
        AppStpLogic appLogic = registrar.logic();
        String appToken = appLogic.getTokenValue();
        SaManager.getSaTokenDao().update(appLogic.splicingKeyLastActiveTime(appToken),
            (System.currentTimeMillis() - 5_000L) + ",1");

        assertThat(loginHelper.getCurrentSessionIndexTimeout()).isZero();
    }

    @Test
    void rejectsEveryUnavailableOrMalformedClientPolicyWithTheContractCodeBeforeIssuingAnAppToken() {
        AppStpLogicRegistrar registrar = new AppStpLogicRegistrar(properties());
        AppLoginHelper missingClientHelper = new AppLoginHelper(registrar, ignored -> {
        }, clientMapper(null));
        AppLoginHelper revisionMismatchHelper = new AppLoginHelper(registrar, ignored -> {
        }, activeClientMapper(7L, 120L, 45L));
        AppLoginHelper disabledClientHelper = new AppLoginHelper(registrar, ignored -> {
        }, clientMapper(client(6L, 120L, 45L, AppIdentityStatus.DISABLED)));
        AppLoginHelper nonPositiveTimeoutHelper = new AppLoginHelper(registrar, ignored -> {
        }, activeClientMapper(6L, 0L, 45L));
        AppLoginHelper activeTimeoutExceedsTokenTimeoutHelper = new AppLoginHelper(registrar, ignored -> {
        }, activeClientMapper(6L, 45L, 120L));
        StpUtil.login(9001L);

        assertClientUnavailable(missingClientHelper);
        assertClientUnavailable(revisionMismatchHelper);
        assertClientUnavailable(disabledClientHelper);
        assertClientUnavailable(nonPositiveTimeoutHelper);
        assertClientUnavailable(activeTimeoutExceedsTokenTimeoutHelper);
        assertThat(StpUtil.isLogin()).isTrue();
    }

    @Test
    void checksAnExpiredAppTokenEvenWhenTheSystemTokenWasCheckedInTheSameRequest() {
        AppStpLogicRegistrar registrar = new AppStpLogicRegistrar(properties());
        AppLoginHelper loginHelper = new AppLoginHelper(registrar, ignored -> {
        }, activeClientMapper(6L, 600L, 1L));
        StpUtil.login(9001L);
        StpUtil.getLoginId();

        loginHelper.login(principal(), "desktop");
        AppStpLogic appLogic = registrar.logic();
        String appToken = appLogic.getTokenValue();
        SaManager.getSaTokenDao().update(appLogic.splicingKeyLastActiveTime(appToken),
            (System.currentTimeMillis() - 5_000L) + ",1");
        SaHolder.getStorage().delete(SaTokenConsts.TOKEN_ACTIVE_TIMEOUT_CHECKED_KEY + "app");

        assertThat(SaHolder.getStorage().get(SaTokenConsts.TOKEN_ACTIVE_TIMEOUT_CHECKED_KEY)).isNotNull();
        assertThatThrownBy(appLogic::getLoginId).isInstanceOf(NotLoginException.class);
        assertThat(StpUtil.isLogin()).isTrue();
    }

    @Test
    void publishesOnlyTheOpaqueAppSessionEndEventAfterLoggingOutTheAppToken() {
        AppStpLogicRegistrar registrar = new AppStpLogicRegistrar(properties());
        AtomicReference<Object> event = new AtomicReference<>();
        AppLoginHelper loginHelper = new AppLoginHelper(registrar, event::set, activeClientMapper(6L, 600L, 120L));
        StpUtil.login(9001L);
        AppLoginUser loginUser = loginHelper.login(principal(), "desktop");

        loginHelper.logout();

        assertThat(event.get()).isInstanceOf(AppSessionEndedEvent.class);
        assertThat(((AppSessionEndedEvent) event.get()).sessionId()).isEqualTo(loginUser.sessionId());
        assertThat(loginHelper.isLogin()).isFalse();
        assertThat(StpUtil.isLogin()).isTrue();
    }

    @Test
    void keepsTheAppTokenLoggedOutWhenTheOnlineSessionRemovalListenerFails() {
        AppStpLogicRegistrar registrar = new AppStpLogicRegistrar(properties());
        ApplicationEventPublisher failingEndEventPublisher = event -> {
            if (event instanceof AppSessionEndedEvent) {
                throw new IllegalStateException("online index removal unavailable");
            }
        };
        AppLoginHelper loginHelper = new AppLoginHelper(registrar, failingEndEventPublisher,
            activeClientMapper(6L, 600L, 120L));
        StpUtil.login(9001L);
        loginHelper.login(principal(), "desktop");

        assertThatThrownBy(loginHelper::logout)
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("online index removal unavailable");
        assertThat(loginHelper.isLogin()).isFalse();
        assertThat(StpUtil.isLogin()).isTrue();
    }

    private AppSaTokenProperties properties() {
        AppSaTokenProperties properties = new AppSaTokenProperties();
        properties.setEnabled(true);
        properties.setJwtSecret("jwt-secret-for-unit-test-32-bytes");
        properties.setWorkspaceKeySecret("workspace-key-secret-for-unit-test-32-bytes");
        return properties;
    }

    private SaTokenConfig defaultLoginConfig() {
        SaTokenConfig config = new SaTokenConfig();
        config.setTokenName("Authorization");
        config.setTokenPrefix("Bearer");
        config.setIsReadHeader(true);
        config.setIsReadBody(false);
        config.setIsReadCookie(false);
        config.setActiveTimeout(300L);
        config.setDynamicActiveTimeout(false);
        return config;
    }

    private void assertClientUnavailable(AppLoginHelper loginHelper) {
        assertThatThrownBy(() -> loginHelper.login(principal(), "desktop"))
            .isInstanceOfSatisfying(ServiceException.class,
                exception -> assertThat(exception.getCode()).isEqualTo(APP_AUTH_CLIENT_UNAVAILABLE_CODE));
        assertThat(loginHelper.isLogin()).isFalse();
    }

    private AppAuthClientMapper activeClientMapper(long revision, long tokenTimeout, long activeTimeout) {
        return clientMapper(client(revision, tokenTimeout, activeTimeout, AppIdentityStatus.ACTIVE));
    }

    private AppAuthClient client(long revision, long tokenTimeout, long activeTimeout, AppIdentityStatus status) {
        AppAuthClient client = new AppAuthClient();
        client.setClientId("desktop");
        client.setClientRevision(revision);
        client.setTokenTimeout(tokenTimeout);
        client.setActiveTimeout(activeTimeout);
        client.setStatus(status);
        client.setDelFlag("0");
        return client;
    }

    private AppAuthClientMapper clientMapper(AppAuthClient client) {
        AppAuthClientMapper mapper = mock(AppAuthClientMapper.class);
        when(mapper.selectOne(any())).thenReturn(client);
        return mapper;
    }

    private AppPrincipalSnapshotDTO principal() {
        AppWorkspaceSessionSnapshotDTO workspace = new AppWorkspaceSessionSnapshotDTO(
            "opaque-personal-key", "personal", 2001L, "app_user", 1001L,
            "personal", 1001L, "personal_creator", Set.of("copy:generate", "copy:read"), 3L, null);
        return new AppPrincipalSnapshotDTO(1001L, "creator", "desktop", 2L, 3L, 4L, 6L, workspace);
    }
}
