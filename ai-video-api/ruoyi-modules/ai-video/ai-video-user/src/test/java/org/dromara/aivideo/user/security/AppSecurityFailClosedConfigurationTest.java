package org.dromara.aivideo.user.security;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies disabling or omitting app token configuration still installs a /api/** denial gate.
 */
@Tag("dev")
class AppSecurityFailClosedConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(FilterConfiguration.class);

    @Test
    void deniesCreatorApiWhenTheSecurityFlagIsTrueButTheCreatorMarkerIsAbsent() {
        contextRunner.withPropertyValues("app.security.token.enabled=true").run(context -> {
            assertThat(context).doesNotHaveBean(AppCredentialIngressFilter.class);
            assertThat(context).hasSingleBean(CreatorApiDisabledFilter.class);
            assertThat(context.getBean(CreatorApiDisabledFilter.class)
                .shouldNotFilter(creatorRequest())).isFalse();
        });
    }

    @Test
    void deniesCreatorApiWhenAppTokenIsExplicitlyDisabledOrMissing() {
        contextRunner.withPropertyValues("app.security.token.enabled=false").run(context -> {
            assertThat(context).hasSingleBean(CreatorApiDisabledFilter.class);
            assertThat(context).doesNotHaveBean(AppCredentialIngressFilter.class);
            assertThat(context.getBean(CreatorApiDisabledFilter.class)
                .shouldNotFilter(creatorRequest())).isFalse();
        });
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(CreatorApiDisabledFilter.class);
            assertThat(context).doesNotHaveBean(AppCredentialIngressFilter.class);
            assertThat(context.getBean(CreatorApiDisabledFilter.class)
                .shouldNotFilter(creatorRequest())).isFalse();
        });
    }

    @Test
    void deniesCreatorApiWhenTheSecurityFlagIsNotExactlyEnabled() {
        contextRunner.withPropertyValues("app.security.token.enabled=invalid").run(context -> {
            assertThat(context).hasSingleBean(CreatorApiDisabledFilter.class);
            assertThat(context).doesNotHaveBean(AppCredentialIngressFilter.class);
            assertThat(context.getBean(CreatorApiDisabledFilter.class)
                .shouldNotFilter(creatorRequest())).isFalse();
        });
    }

    private static MockHttpServletRequest creatorRequest() {
        return new MockHttpServletRequest("GET", "/api/creation/drafts");
    }

    @Configuration(proxyBeanMethods = false)
    @Import({AppCredentialIngressFilter.class, CreatorApiDisabledFilter.class})
    static class FilterConfiguration {
    }
}
