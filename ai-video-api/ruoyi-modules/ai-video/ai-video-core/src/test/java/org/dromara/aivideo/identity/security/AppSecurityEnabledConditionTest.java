package org.dromara.aivideo.identity.security;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.mock.env.MockEnvironment;

import java.net.URL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Ensures ambiguous raw configuration never enables the creator authentication chain.
 */
@Tag("dev")
class AppSecurityEnabledConditionTest {

    private final AppSecurityEnabledCondition condition = new AppSecurityEnabledCondition();

    @Test
    void acceptsOnlyTheExactTrueValueIgnoringCase() {
        ClassLoader creatorRuntime = AppSecurityEnabledConditionTest.class.getClassLoader();
        assertThat(matches("true", creatorRuntime)).isTrue();
        assertThat(matches("TRUE", creatorRuntime)).isTrue();
        assertThat(matches(" true ", creatorRuntime)).isFalse();
        assertThat(matches("false", creatorRuntime)).isFalse();
        assertThat(matches("invalid", creatorRuntime)).isFalse();
        assertThat(matches(null, creatorRuntime)).isFalse();
    }

    @Test
    void refusesAnEnvironmentOverrideWhenTheCreatorStarterMarkerIsAbsent() {
        ClassLoader noMarkerClassLoader = new ClassLoader(null) {
            @Override
            public URL getResource(String name) {
                return null;
            }
        };

        assertThat(matches("true", noMarkerClassLoader)).isFalse();
    }

    private boolean matches(String value, ClassLoader classLoader) {
        MockEnvironment environment = new MockEnvironment();
        if (value != null) {
            environment.setProperty("app.security.token.enabled", value);
        }
        ConditionContext context = mock(ConditionContext.class);
        when(context.getEnvironment()).thenReturn(environment);
        when(context.getClassLoader()).thenReturn(classLoader);
        return condition.matches(context, null);
    }
}
