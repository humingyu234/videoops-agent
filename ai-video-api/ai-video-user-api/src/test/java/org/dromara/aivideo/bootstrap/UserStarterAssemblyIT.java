package org.dromara.aivideo.bootstrap;

import org.dromara.aivideo.user.auth.controller.AppAuthController;
import org.dromara.aivideo.user.auth.service.IAppAuthApplicationService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

/**
 * Verifies that the creator-facing starter does not assemble operating-side modules.
 */
@Tag("dev")
class UserStarterAssemblyIT {

    @Test
    void creatorControllerContextMustContainOnlyCreatorAuthenticationController() {
        try (AnnotationConfigApplicationContext context =
                 new AnnotationConfigApplicationContext(CreatorControllerContextConfiguration.class)) {
            assertThat(context.getBeansWithAnnotation(RestController.class).values())
                .extracting(controller -> controller.getClass().getName())
                .contains(AppAuthController.class.getName())
                .doesNotContain(
                    "org.dromara.system.controller.system.SysUserController",
                    "org.dromara.system.controller.system.SysRoleController",
                    "org.dromara.system.controller.system.SysMenuController",
                    "org.dromara.web.controller.system.SysUserController",
                    "org.dromara.web.controller.system.AuthController",
                    "org.dromara.aivideo.platform.identity.controller.AppUserAdminController");
        }
    }

    @Test
    void mustNotExposeOperatingSideClasses() {
        assertClassIsAbsent("org.dromara.system.mapper.SysUserMapper");
        assertClassIsAbsent("org.dromara.web.service.SysLoginService");
        assertClassIsAbsent("org.dromara.gen.controller.GenController");
        assertClassIsAbsent("org.dromara.aivideo.platform.identity.controller.AppUserAdminController");
        assertClassIsAbsent("org.dromara.aivideo.platform.identity.controller.AppAuthClientAdminController");
    }

    private void assertClassIsAbsent(String className) {
        assertThrows(ClassNotFoundException.class, () -> Class.forName(className), className + " must not be on the creator starter classpath");
    }

    /**
     * Controlled controller context: it scans the real creator adapter package while replacing
     * its application-service collaborator so no database or Redis runtime is required.
     */
    @Configuration(proxyBeanMethods = false)
    @ComponentScan(
        basePackageClasses = AppAuthController.class,
        useDefaultFilters = false,
        includeFilters = @ComponentScan.Filter(type = FilterType.ANNOTATION, classes = RestController.class)
    )
    static class CreatorControllerContextConfiguration {

        @Bean
        IAppAuthApplicationService appAuthApplicationService() {
            return mock(IAppAuthApplicationService.class);
        }
    }
}
