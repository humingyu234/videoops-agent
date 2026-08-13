package org.dromara.aivideo.infra.identity;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 创作端外部身份适配配置。
 *
 * <p>所有渠道默认关闭；授权码和状态令牌的回放保护密钥与创作端令牌、验证码密钥独立。</p>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.security.external-identity")
public class AppExternalIdentityProperties {

    /** 是否显式启用外部身份能力。 */
    private boolean enabled;

    /** 外部授权一次性回放标记的 HMAC 密钥。 */
    private String replayHmacSecret;

    /** 外部授权一次性回放标记的固定有效秒数。 */
    private long replayTtlSeconds = 600L;

    /** 社交授权配置。 */
    private Social social = new Social();

    /** 微信小程序授权配置。 */
    private MiniProgram miniProgram = new MiniProgram();

    /**
     * 判断共享回放保护配置能否安全运行。
     */
    public boolean isOperational() {
        return enabled
            && isSecretStrong(replayHmacSecret)
            && replayTtlSeconds == 600L;
    }

    static boolean isSecretStrong(String secret) {
        return secret != null && secret.getBytes(StandardCharsets.UTF_8).length >= 32;
    }

    /**
     * 社交授权来源白名单。
     */
    @Getter
    @Setter
    public static class Social {

        /** 是否显式启用社交授权。 */
        private boolean enabled;

        /** 允许调用 {@code SocialUtils} 的来源稳定键。 */
        private List<String> allowedProviders = new ArrayList<>();
    }

    /**
     * 微信小程序授权配置。
     */
    @Getter
    @Setter
    public static class MiniProgram {

        /** 是否显式启用微信小程序授权。 */
        private boolean enabled;

        /** 微信小程序 AppId。 */
        private String appId;

        /** 微信小程序 AppSecret。 */
        private String appSecret;
    }
}
