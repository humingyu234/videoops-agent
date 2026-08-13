package org.dromara.aivideo.identity.security;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.dao.SaTokenDaoDefaultImpl;
import cn.dev33.satoken.exception.SaTokenException;
import org.dromara.aivideo.identity.service.impl.AppSessionServiceImpl;
import org.dromara.aivideo.identity.mapper.AppAuthClientMapper;
import org.dromara.aivideo.identity.mapper.AppUserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.core.env.MapPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * 验证 app 会话组件只在创作端显式启用时装配。
 */
@Tag("dev")
@ResourceLock("sa-token-manager")
class AppSessionComponentIsolationTest {

    @AfterEach
    void tearDown() {
        SaManager.removeStpLogic("app");
        SaManager.setSaTokenDao(new SaTokenDaoDefaultImpl());
    }

    @Test
    void doesNotCreateAnyAppLoginComponentWhenTheAppNamespaceIsDisabled() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                "app.security.token.enabled", "false")));
            context.register(DisabledAppSessionComponentsConfiguration.class);
            context.refresh();

            assertThat(context.getBeansOfType(AppSaTokenProperties.class)).isEmpty();
            assertThat(context.getBeansOfType(AppStpLogicRegistrar.class)).isEmpty();
            assertThat(context.getBeansOfType(AppLoginHelper.class)).isEmpty();
            assertThat(context.getBeansOfType(AppSessionTokenRevoker.class)).isEmpty();
            assertThat(context.getBeansOfType(AppSessionServiceImpl.class)).isEmpty();
            assertThat(context.getBeansOfType(AppSessionRevisionGuard.class)).isEmpty();
            assertThat(context.getBeansOfType(AppPersonalWorkspaceSnapshotProvider.class)).isEmpty();
            assertThatThrownBy(() -> SaManager.getStpLogic("app", false))
                .isInstanceOf(SaTokenException.class);
        }
    }

    @Test
    void createsTheDedicatedAppLogicOnlyWhenTheNamespaceIsExplicitlyEnabled() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                "app.security.token.enabled", "true")));
            context.register(EnabledAppLoginComponentsConfiguration.class);
            context.refresh();

            AppStpLogicRegistrar registrar = context.getBean(AppStpLogicRegistrar.class);
            assertThat(context.getBean(AppLoginHelper.class)).isNotNull();
            assertThat(registrar.logic()).isInstanceOf(AppStpLogic.class);
            assertThat(SaManager.getStpLogic("app", false)).isSameAs(registrar.logic());
        }
    }

    @Test
    void createsOnlyTheSessionRevocationRuntimeWhenTheOperatingStarterEnablesIt() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                "app.security.token.enabled", "false",
                "app.security.session-revocation.enabled", "true")));
            context.register(SessionRevocationOnlyComponentsConfiguration.class);
            context.refresh();

            AppStpLogicRegistrar registrar = context.getBean(AppStpLogicRegistrar.class);
            assertThat(context.getBean(AppSessionServiceImpl.class)).isNotNull();
            assertThat(context.getBean(AppSessionTokenRevoker.class)).isNotNull();
            assertThat(context.getBeansOfType(AppLoginHelper.class)).isEmpty();
            assertThat(context.getBeansOfType(AppSessionRevisionGuard.class)).isEmpty();
            assertThat(context.getBeansOfType(AppPersonalWorkspaceSnapshotProvider.class)).isEmpty();
            assertThat(registrar.logic()).isInstanceOf(AppStpLogic.class);
        }
    }

    /**
     * 仅扫描带条件的 app 登录组件，用于验证关闭开关时不会在运营端启动进程中创建它们。
     */
    @Configuration(proxyBeanMethods = false)
    @ComponentScan(
        basePackageClasses = {
            AppSaTokenProperties.class,
            AppStpLogicRegistrar.class,
            AppLoginHelper.class,
            AppSessionTokenRevokerImpl.class,
            AppSessionServiceImpl.class,
            AppSessionRevisionGuard.class,
            AppPersonalWorkspaceSnapshotProvider.class
        },
        useDefaultFilters = false,
        includeFilters = @ComponentScan.Filter(
            type = FilterType.ASSIGNABLE_TYPE,
            classes = {
                AppSaTokenProperties.class,
                AppStpLogicRegistrar.class,
                AppLoginHelper.class,
                AppSessionTokenRevokerImpl.class,
                AppSessionServiceImpl.class,
                AppSessionRevisionGuard.class,
                AppPersonalWorkspaceSnapshotProvider.class
            }
        )
    )
    static class DisabledAppSessionComponentsConfiguration {
    }

    /**
     * 以显式测试密钥装配最小 app 登录链路，避免测试中读取环境变量。
     */
    @Configuration(proxyBeanMethods = false)
    @ComponentScan(
        basePackageClasses = {AppStpLogicRegistrar.class, AppLoginHelper.class},
        useDefaultFilters = false,
        includeFilters = @ComponentScan.Filter(
            type = FilterType.ASSIGNABLE_TYPE,
            classes = {AppStpLogicRegistrar.class, AppLoginHelper.class}
        )
    )
    static class EnabledAppLoginComponentsConfiguration {

        @Bean
        AppSaTokenProperties appSaTokenProperties() {
            AppSaTokenProperties properties = new AppSaTokenProperties();
            properties.setEnabled(true);
            properties.setJwtSecret("jwt-secret-for-component-test-32-bytes");
            properties.setWorkspaceKeySecret("workspace-key-secret-for-component-test-32-bytes");
            return properties;
        }

        @Bean
        AppAuthClientMapper appAuthClientMapper() {
            return mock(AppAuthClientMapper.class);
        }
    }

    /**
     * 运营端只提供撤销既有 app 会话的最小运行时，绝不装配用户端登录助手。
     */
    @Configuration(proxyBeanMethods = false)
    @ComponentScan(
        basePackageClasses = {
            AppStpLogicRegistrar.class,
            AppLoginHelper.class,
            AppSessionTokenRevokerImpl.class,
            AppSessionServiceImpl.class,
            AppSessionRevisionGuard.class,
            AppPersonalWorkspaceSnapshotProvider.class
        },
        useDefaultFilters = false,
        includeFilters = @ComponentScan.Filter(
            type = FilterType.ASSIGNABLE_TYPE,
            classes = {
                AppStpLogicRegistrar.class,
                AppLoginHelper.class,
                AppSessionTokenRevokerImpl.class,
                AppSessionServiceImpl.class,
                AppSessionRevisionGuard.class,
                AppPersonalWorkspaceSnapshotProvider.class
            }
        )
    )
    static class SessionRevocationOnlyComponentsConfiguration {

        @Bean
        AppSaTokenProperties appSaTokenProperties() {
            AppSaTokenProperties properties = new AppSaTokenProperties();
            properties.setEnabled(false);
            properties.setJwtSecret("jwt-secret-for-session-revocation-test-32-bytes");
            return properties;
        }

        @Bean
        AppUserMapper appUserMapper() {
            return mock(AppUserMapper.class);
        }

        @Bean
        org.dromara.aivideo.identity.service.IAppSecurityAuditService appSecurityAuditService() {
            return mock(org.dromara.aivideo.identity.service.IAppSecurityAuditService.class);
        }

        @Bean
        AppIdentityOperationAuthorizer appIdentityOperationAuthorizer() {
            return mock(AppIdentityOperationAuthorizer.class);
        }
    }
}
