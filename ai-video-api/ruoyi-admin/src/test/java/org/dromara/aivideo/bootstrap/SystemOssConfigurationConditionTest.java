package org.dromara.aivideo.bootstrap;

import org.dromara.system.runner.SystemApplicationRunner;
import org.dromara.system.service.ISysOssConfigService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@Tag("dev")
class SystemOssConfigurationConditionTest {

    @Test
    void doesNotRegisterDatabaseOssInitializerWhenOssIsDisabled() {
        ISysOssConfigService service = mock(ISysOssConfigService.class);

        runner(service)
            .withPropertyValues("aivideo.oss.enabled=false")
            .run(context -> {
                assertThat(context).doesNotHaveBean(SystemApplicationRunner.class);
                verifyNoInteractions(service);
            });
    }

    @Test
    void registersDatabaseOssInitializerOnlyWhenOssIsEnabled() {
        ISysOssConfigService service = mock(ISysOssConfigService.class);

        runner(service)
            .withPropertyValues("aivideo.oss.enabled=true")
            .run(context -> {
                assertThat(context).hasSingleBean(SystemApplicationRunner.class);
                context.getBean(SystemApplicationRunner.class).run(null);
                verify(service).init();
            });
    }

    private ApplicationContextRunner runner(ISysOssConfigService service) {
        return new ApplicationContextRunner()
            .withBean(ISysOssConfigService.class, () -> service)
            .withUserConfiguration(SystemApplicationRunner.class);
    }
}
