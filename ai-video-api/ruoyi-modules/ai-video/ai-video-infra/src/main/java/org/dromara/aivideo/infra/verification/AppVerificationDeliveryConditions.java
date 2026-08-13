package org.dromara.aivideo.infra.verification;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * 验证码投递渠道的严格装配条件。
 */
final class AppVerificationDeliveryConditions {

    private AppVerificationDeliveryConditions() {
    }

    static boolean smsConfigured(Environment environment) {
        return enabled(environment, "app.security.verification.delivery.sms.")
            && hasText(environment.getProperty("app.security.verification.delivery.sms.config-id"))
            && hasText(environment.getProperty("app.security.verification.delivery.sms.template-id"))
            && hasText(environment.getProperty("app.security.verification.delivery.sms.code-parameter", "code"))
            && hasText(environment.getProperty("app.security.verification.delivery.sms.expires-in-minutes-parameter",
                "expiresInMinutes"));
    }

    static boolean mailConfigured(Environment environment) {
        String template = environment.getProperty("app.security.verification.delivery.mail.content-template");
        return enabled(environment, "app.security.verification.delivery.mail.")
            && hasText(environment.getProperty("app.security.verification.delivery.mail.subject"))
            && hasText(template)
            && template.contains("{code}")
            && template.contains("{expiresInMinutes}");
    }

    private static boolean enabled(Environment environment, String prefix) {
        String value = environment.getProperty(prefix + "enabled");
        return value != null && "true".equalsIgnoreCase(value);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

/**
 * 短信渠道配置完整时才注册短信投递端口。
 */
final class AppSmsVerificationDeliveryConfiguredCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return AppVerificationDeliveryConditions.smsConfigured(context.getEnvironment());
    }
}

/**
 * 邮件渠道配置完整且包含所有验证码占位符时才注册邮件投递端口。
 */
final class AppMailVerificationDeliveryConfiguredCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return AppVerificationDeliveryConditions.mailConfigured(context.getEnvironment());
    }
}
