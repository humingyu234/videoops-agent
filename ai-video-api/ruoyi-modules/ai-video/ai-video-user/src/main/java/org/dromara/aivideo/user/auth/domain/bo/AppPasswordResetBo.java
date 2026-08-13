package org.dromara.aivideo.user.auth.domain.bo;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 创作端使用一次性验证码找回密码请求。
 *
 * <p>挑战只绑定已验证客户端；用户编号、联系方式、租户及凭据修订号都不接受客户端传入。</p>
 *
 * @param challengeId 不透明一次性挑战编号
 * @param verificationCode 本次提交的六位验证码
 * @param newPassword 新明文密码，仅传递给核心密码策略和散列逻辑
 */
@JsonIgnoreProperties(ignoreUnknown = false)
public record AppPasswordResetBo(
    @NotBlank(message = "验证码挑战编号不能为空")
    @Size(max = 128, message = "验证码挑战编号长度不能超过 128 个字符")
    String challengeId,
    @NotBlank(message = "验证码不能为空")
    @Pattern(regexp = "\\d{6}", message = "验证码格式不正确")
    String verificationCode,
    @NotBlank(message = "新密码不能为空")
    @Size(max = 256, message = "新密码长度不能超过 256 个字符")
    String newPassword
) {

    @Override
    public String toString() {
        return "AppPasswordResetBo[challengeId=***, verificationCode=***, newPassword=***]";
    }

    /**
     * 无论全局 Jackson 配置是否忽略未知字段，公开认证请求都不能静默接收身份或客户端字段。
     *
     * @param propertyName 未知字段名
     * @param ignored 未知字段值
     */
    @JsonAnySetter
    public void rejectUnknownProperty(String propertyName, Object ignored) {
        throw new IllegalArgumentException("公开找回密码请求包含不允许的字段");
    }
}
