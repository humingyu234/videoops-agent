package org.dromara.aivideo.identity.security;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * app 会话运行时的最小装配条件。
 *
 * <p>用户端通过 {@code app.security.token.enabled} 启用完整登录链；运营端只能通过
 * {@code app.security.session-revocation.enabled} 启用既有 app 会话的撤销运行时。
 * 两个开关均采用严格的原始值判断，避免空白或非预期字符串被归一化为已启用。</p>
 */
final class AppSessionRuntimeEnabledCondition implements Condition {

    private static final String USER_LOGIN_ENABLED_PROPERTY = "app.security.token.enabled";
    private static final String SESSION_REVOCATION_ENABLED_PROPERTY = "app.security.session-revocation.enabled";

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return isExplicitlyEnabled(context, USER_LOGIN_ENABLED_PROPERTY)
            || isExplicitlyEnabled(context, SESSION_REVOCATION_ENABLED_PROPERTY);
    }

    private boolean isExplicitlyEnabled(ConditionContext context, String property) {
        String configuredValue = context.getEnvironment().getProperty(property);
        return configuredValue != null && "true".equalsIgnoreCase(configuredValue);
    }
}
