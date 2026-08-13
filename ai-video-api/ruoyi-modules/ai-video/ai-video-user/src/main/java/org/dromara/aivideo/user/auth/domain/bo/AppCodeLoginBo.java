package org.dromara.aivideo.user.auth.domain.bo;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 创作端短信或邮件验证码登录请求。
 *
 * <p>挑战只与已验证的创作端客户端绑定；用户、联系方式、工作区和修订号均不接受客户端传入。</p>
 *
 * @param challengeId 不透明验证码挑战编号
 * @param verificationCode 本次提交的六位验证码
 */
@JsonIgnoreProperties(ignoreUnknown = false)
public record AppCodeLoginBo(
    @NotBlank(message = "验证码挑战编号不能为空")
    @Size(max = 128, message = "验证码挑战编号长度不能超过 128 个字符")
    String challengeId,
    @NotBlank(message = "验证码不能为空")
    @Pattern(regexp = "\\d{6}", message = "验证码格式不正确")
    String verificationCode
) {

    @Override
    public String toString() {
        return "AppCodeLoginBo[challengeId=***, verificationCode=***]";
    }

    /**
     * 公开认证请求不得静默接收可由调用方伪造的身份或客户端字段。
     *
     * @param propertyName 未知字段名
     * @param ignored 未知字段值
     */
    @JsonAnySetter
    public void rejectUnknownProperty(String propertyName, Object ignored) {
        throw new IllegalArgumentException("公开验证码登录请求包含不允许的字段");
    }
}
