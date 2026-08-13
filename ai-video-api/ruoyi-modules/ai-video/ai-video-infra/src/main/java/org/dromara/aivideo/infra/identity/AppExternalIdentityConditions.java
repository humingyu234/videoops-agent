package org.dromara.aivideo.infra.identity;

import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 外部身份适配器的严格装配条件。
 */
public final class AppExternalIdentityConditions {

    private static final String PREFIX = "app.security.external-identity.";

    /** 与项目 {@code SocialUtils} 支持的来源键保持一一对应，新增来源必须显式审查。 */
    private static final Set<String> SOCIAL_UTILS_PROVIDERS = Set.of(
        "dingtalk", "baidu", "github", "gitee", "weibo", "coding", "oschina", "alipay_wallet", "qq",
        "wechat_open", "taobao", "douyin", "linkedin", "microsoft", "renren", "stack_overflow", "huawei",
        "wechat_enterprise", "gitlab", "wechat_mp", "aliyun", "maxkey", "topiam", "gitea");

    private AppExternalIdentityConditions() {
    }

    static boolean socialConfigured(Environment environment) {
        if (!sharedConfigured(environment) || !enabled(environment, PREFIX + "social.enabled")) {
            return false;
        }
        List<String> providers = Binder.get(environment)
            .bind(PREFIX + "social.allowed-providers", Bindable.listOf(String.class))
            .orElse(List.of());
        if (providers.isEmpty()) {
            return false;
        }
        for (String provider : providers) {
            if (!isCanonicalProvider(provider) || !SOCIAL_UTILS_PROVIDERS.contains(provider)) {
                return false;
            }
            String configPrefix = "justauth.type." + provider + ".";
            if (!hasText(environment.getProperty(configPrefix + "client-id"))
                || !hasText(environment.getProperty(configPrefix + "client-secret"))
                || !hasText(environment.getProperty(configPrefix + "redirect-uri"))) {
                return false;
            }
        }
        return true;
    }

    static boolean miniProgramConfigured(Environment environment) {
        return sharedConfigured(environment)
            && enabled(environment, PREFIX + "mini-program.enabled")
            && hasText(environment.getProperty(PREFIX + "mini-program.app-id"))
            && hasText(environment.getProperty(PREFIX + "mini-program.app-secret"));
    }

    public static boolean isSupportedSocialProvider(String provider) {
        return provider != null && SOCIAL_UTILS_PROVIDERS.contains(provider);
    }

    private static boolean sharedConfigured(Environment environment) {
        String ttlSeconds = environment.getProperty(PREFIX + "replay-ttl-seconds");
        return enabled(environment, PREFIX + "enabled")
            && AppExternalIdentityProperties.isSecretStrong(environment.getProperty(PREFIX + "replay-hmac-secret"))
            && (ttlSeconds == null || "600".equals(ttlSeconds));
    }

    private static boolean enabled(Environment environment, String property) {
        String value = environment.getProperty(property);
        return value != null && "true".equalsIgnoreCase(value);
    }

    private static boolean isCanonicalProvider(String provider) {
        return hasText(provider) && provider.equals(provider.trim().toLowerCase(Locale.ROOT));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

/**
 * 社交授权配置完整时才注册社交外部身份端口。
 */
final class AppSocialIdentityGatewayConfiguredCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return AppExternalIdentityConditions.socialConfigured(context.getEnvironment());
    }
}

/**
 * 微信小程序授权配置完整时才注册小程序外部身份端口。
 */
final class AppMiniProgramIdentityGatewayConfiguredCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return AppExternalIdentityConditions.miniProgramConfigured(context.getEnvironment());
    }
}
