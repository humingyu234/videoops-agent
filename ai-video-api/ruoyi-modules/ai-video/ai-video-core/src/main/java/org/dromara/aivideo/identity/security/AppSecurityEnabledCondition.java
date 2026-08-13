package org.dromara.aivideo.identity.security;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * 创作端安全链的失效关闭条件。
 */
final class AppSecurityEnabledCondition implements Condition {

    private static final String ENABLED_PROPERTY = "app.security.token.enabled";

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String configuredValue = context.getEnvironment().getProperty(ENABLED_PROPERTY);
        return AppSecurityRuntime.isCreatorSecurityEnabled(configuredValue, context.getClassLoader());
    }
}
