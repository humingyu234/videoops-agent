package org.dromara.aivideo.bootstrap;

import org.dromara.aivideo.platform.identity.controller.AppAuthClientAdminController;
import org.dromara.aivideo.platform.identity.controller.AppRoleAdminController;
import org.dromara.aivideo.platform.identity.controller.AppSecurityLogAdminController;
import org.dromara.aivideo.platform.identity.controller.AppSessionAdminController;
import org.dromara.aivideo.platform.identity.controller.AppUserAdminController;
import org.dromara.aivideo.platform.identity.service.IAppIdentityAdminService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

/**
 * Verifies that the operating-side starter contains only the operating adapter for app identities.
 */
@Tag("dev")
class PlatformStarterAssemblyIT {

    @Test
    void operatingControllerContextMustContainOnlyAppManagementControllers() {
        try (AnnotationConfigApplicationContext context =
                 new AnnotationConfigApplicationContext(OperatingControllerContextConfiguration.class)) {
            assertThat(context.getBeansWithAnnotation(RestController.class).values())
                .extracting(controller -> controller.getClass().getName())
                .contains(
                    AppUserAdminController.class.getName(),
                    AppRoleAdminController.class.getName(),
                    AppAuthClientAdminController.class.getName(),
                    AppSessionAdminController.class.getName(),
                    AppSecurityLogAdminController.class.getName())
                .doesNotContain("org.dromara.aivideo.user.auth.controller.AppAuthController");
        }
    }

    @Test
    void mustExposeAppManagementControllersButNotCreatorAuthenticationControllers() {
        assertClassIsPresent("org.dromara.aivideo.platform.identity.controller.AppUserAdminController");
        assertClassIsPresent("org.dromara.aivideo.platform.identity.controller.AppRoleAdminController");
        assertClassIsPresent("org.dromara.aivideo.platform.identity.controller.AppAuthClientAdminController");
        assertClassIsPresent("org.dromara.aivideo.platform.identity.controller.AppSessionAdminController");
        assertClassIsPresent("org.dromara.aivideo.platform.identity.controller.AppSecurityLogAdminController");

        assertClassIsAbsent("org.dromara.aivideo.user.auth.controller.AppAuthController");
        assertClassIsAbsent("org.dromara.aivideo.user.security.AppCredentialIngressFilter");
        assertClassIsAbsent("org.dromara.aivideo.identity.security.AppAuthenticationSessionIssuer");
        assertClassIsAbsent("org.dromara.aivideo.identity.security.AppIssuedAccessToken");
    }

    private void assertClassIsPresent(String className) {
        assertDoesNotThrow(() -> Class.forName(className), className + " must be on the operating starter classpath");
    }

    private void assertClassIsAbsent(String className) {
        assertThrows(ClassNotFoundException.class, () -> Class.forName(className),
            className + " must not be on the operating starter classpath");
    }

    /**
     * Controlled controller context: it scans the real operating adapter package while replacing
     * the shared management service, avoiding database and Redis runtime dependencies.
     */
    @Configuration(proxyBeanMethods = false)
    @ComponentScan(
        basePackageClasses = AppUserAdminController.class,
        useDefaultFilters = false,
        includeFilters = @ComponentScan.Filter(type = FilterType.ANNOTATION, classes = RestController.class)
    )
    static class OperatingControllerContextConfiguration {

        @Bean
        IAppIdentityAdminService appIdentityAdminService() {
            return mock(IAppIdentityAdminService.class);
        }
    }
}
