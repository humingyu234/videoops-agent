package org.dromara.aivideo.identity.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 创作端验证码状态机配置。
 *
 * <p>密钥缺失、强度不足或参数偏离冻结值时，验证码服务必须失效关闭；不能用开发默认密钥降级。</p>
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.security.verification")
@ConditionalOnAppSecurityEnabled
public class AppVerificationProperties {

    /** 是否显式启用验证码能力。 */
    private boolean enabled;

    /** 与 app JWT、工作区密钥分离的 HMAC 密钥。 */
    private String hmacSecret;

    /** 挑战有效期秒数，P0-A 固定为十分钟。 */
    private long ttlSeconds = 600L;

    /** 单挑战允许的最大错误次数，P0-A 固定为五次。 */
    private int maxAttempts = 5;

    /** 同一目标和客户端连续申请的冷却秒数。 */
    private long cooldownSeconds = 60L;

    /** 同一目标和客户端每日最多允许申请次数。 */
    private int maxIssuesPerDay = 5;

    /**
     * 判断当前配置是否可以安全地执行验证码状态机。
     */
    public boolean isOperational() {
        return enabled
            && AppSaTokenProperties.isSecretStrong(hmacSecret)
            && ttlSeconds == 600L
            && maxAttempts == 5
            && cooldownSeconds == 60L
            && maxIssuesPerDay == 5;
    }
}
