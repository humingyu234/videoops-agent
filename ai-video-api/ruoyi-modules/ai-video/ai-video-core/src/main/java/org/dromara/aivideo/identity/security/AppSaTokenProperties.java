package org.dromara.aivideo.identity.security;

import jakarta.validation.constraints.AssertTrue;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.nio.charset.StandardCharsets;

/**
 * 创作端独立 Sa-Token 配置。
 */
@Getter
@Setter
@Validated
@Component
@ConfigurationProperties(prefix = "app.security.token")
@ConditionalOnAppSessionRuntimeEnabled
public class AppSaTokenProperties {

    /**
     * 是否启用创作端独立登录命名空间。
     */
    private boolean enabled;

    /**
     * 创作端 JWT 签名密钥，配置值仅允许引用 APP_SA_TOKEN_JWT_SECRET 环境变量。
     */
    private String jwtSecret;

    /**
     * 个人工作区稳定键的 HMAC 密钥，配置值仅允许引用 APP_SECURITY_TOKEN_WORKSPACE_KEY_SECRET 环境变量。
     */
    private String workspaceKeySecret;

    @AssertTrue(message = "创作端 JWT 签名密钥至少需要 32 个 UTF-8 字节")
    public boolean isJwtSecretStrong() {
        return isSecretStrong(jwtSecret);
    }

    @AssertTrue(message = "创作端工作区稳定键密钥至少需要 32 个 UTF-8 字节")
    public boolean isWorkspaceKeySecretStrong() {
        return !enabled || isSecretStrong(workspaceKeySecret);
    }

    static boolean isSecretStrong(String secret) {
        return secret != null && secret.getBytes(StandardCharsets.UTF_8).length >= 32;
    }
}
