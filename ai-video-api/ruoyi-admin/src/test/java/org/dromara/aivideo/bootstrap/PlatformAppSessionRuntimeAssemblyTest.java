package org.dromara.aivideo.bootstrap;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.context.mock.SaTokenContextMockUtil;
import cn.dev33.satoken.dao.SaTokenDaoDefaultImpl;
import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import org.dromara.aivideo.identity.service.IAppSecurityAuditService;
import org.dromara.aivideo.identity.service.impl.AppSessionServiceImpl;
import org.dromara.aivideo.identity.mapper.AppUserMapper;
import org.dromara.aivideo.identity.security.AppIdentityOperationAuthorizer;
import org.dromara.aivideo.identity.security.AppLoginHelper;
import org.dromara.aivideo.identity.security.AppPersonalWorkspaceSnapshotProvider;
import org.dromara.aivideo.identity.security.AppSaTokenProperties;
import org.dromara.aivideo.identity.security.AppSessionRevisionGuard;
import org.dromara.aivideo.identity.security.AppSessionTokenRevoker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.io.ClassPathResource;

import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Verifies the actual operating-starter switch assembles only the app session-revocation runtime.
 */
@Tag("dev")
@ResourceLock("sa-token-manager")
class PlatformAppSessionRuntimeAssemblyTest {

    private StpLogic defaultLoginLogic;

    @BeforeEach
    void setUp() {
        SaManager.removeStpLogic("app");
        SaManager.setSaTokenDao(new SaTokenDaoDefaultImpl());
        SaTokenContextMockUtil.setMockContext();
        defaultLoginLogic = StpUtil.getStpLogic();
    }

    @AfterEach
    void tearDown() {
        SaTokenContextMockUtil.clearContext();
        SaManager.removeStpLogic("app");
        SaManager.setSaTokenDao(new SaTokenDaoDefaultImpl());
    }

    @Test
    void assemblesRevocationButNotCreatorLoginFromTheOperatingStarterConfiguration() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addLast(
                new PropertiesPropertySource("operating-starter-yaml", operatingStarterProperties()));
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test-secret", Map.of(
                "app.security.token.jwt-secret", "platform-session-revocation-test-jwt-secret-32-bytes")));
            context.register(SessionRevocationRuntimeConfiguration.class);
            context.refresh();

            assertThat(context.getBean(AppSessionServiceImpl.class)).isNotNull();
            AppSessionTokenRevoker revoker = context.getBean(AppSessionTokenRevoker.class);
            assertThat(revoker).isNotNull();
            assertThat(context.getBeansOfType(AppLoginHelper.class)).isEmpty();
            assertThat(context.getBeansOfType(AppSessionRevisionGuard.class)).isEmpty();
            assertThat(context.getBeansOfType(AppPersonalWorkspaceSnapshotProvider.class)).isEmpty();
            assertThat(StpUtil.getStpLogic()).isSameAs(defaultLoginLogic);

            StpLogic appLogic = SaManager.getStpLogic("app", false);
            appLogic.login(7001L);
            assertThat(appLogic.isLogin()).isTrue();

            revoker.kickoutUserSessions(7001L);

            assertThat(appLogic.isLogin()).isFalse();
        }
    }

    private Properties operatingStarterProperties() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"));
        Properties properties = yaml.getObject();
        assertThat(properties).isNotNull();
        return properties;
    }

    @Configuration(proxyBeanMethods = false)
    @ComponentScan(
        basePackages = "org.dromara.aivideo.identity",
        useDefaultFilters = false,
        includeFilters = @ComponentScan.Filter(
            type = FilterType.REGEX,
            pattern = {
                "org\\.dromara\\.aivideo\\.identity\\.security\\.(AppStpLogicRegistrar|AppLoginHelper|AppSessionTokenRevokerImpl|AppSessionRevisionGuard|AppPersonalWorkspaceSnapshotProvider)",
                "org\\.dromara\\.aivideo\\.identity\\.service\\.impl\\.AppSessionServiceImpl"
            }
        )
    )
    static class SessionRevocationRuntimeConfiguration {

        @Bean
        AppSaTokenProperties appSaTokenProperties(Environment environment) {
            AppSaTokenProperties properties = new AppSaTokenProperties();
            properties.setEnabled(Boolean.parseBoolean(environment.getRequiredProperty("app.security.token.enabled")));
            properties.setJwtSecret(environment.getRequiredProperty("app.security.token.jwt-secret"));
            properties.setWorkspaceKeySecret(environment.getProperty("app.security.token.workspace-key-secret"));
            return properties;
        }

        @Bean
        AppUserMapper appUserMapper() {
            return mock(AppUserMapper.class);
        }

        @Bean
        IAppSecurityAuditService appSecurityAuditService() {
            return mock(IAppSecurityAuditService.class);
        }

        @Bean
        AppIdentityOperationAuthorizer appIdentityOperationAuthorizer() {
            return mock(AppIdentityOperationAuthorizer.class);
        }
    }
}
