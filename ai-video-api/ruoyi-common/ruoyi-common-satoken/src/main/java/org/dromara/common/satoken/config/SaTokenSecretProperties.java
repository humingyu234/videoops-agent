package org.dromara.common.satoken.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.nio.charset.StandardCharsets;

/**
 * Validates the default operating-side Sa-Token signing secret before its JWT login logic is initialized.
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "sa-token")
public class SaTokenSecretProperties {

    @NotBlank(message = "运营端 Sa-Token JWT 签名密钥不能为空")
    private String jwtSecretKey;

    @AssertTrue(message = "运营端 Sa-Token JWT 签名密钥至少需要 32 个 UTF-8 字节")
    public boolean isJwtSecretKeyStrong() {
        return jwtSecretKey != null && jwtSecretKey.getBytes(StandardCharsets.UTF_8).length >= 32;
    }
}
