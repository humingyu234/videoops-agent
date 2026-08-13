package org.dromara.aivideo.user.auth.domain.bo;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.dromara.aivideo.identity.security.AppVerificationChannel;
import org.dromara.aivideo.identity.security.AppVerificationScenario;

/**
 * 创作端申请验证码请求。
 *
 * <p>用户、客户端、租户和各类修订号均由服务端上下文推导，不能由公开请求体提供。</p>
 *
 * @param scenario 验证码使用场景
 * @param channel 短信或邮件渠道
 * @param target 用户提交的手机号或邮箱
 */
@JsonIgnoreProperties(ignoreUnknown = false)
public record AppVerificationCodeBo(
    @NotNull(message = "验证码场景不能为空")
    AppVerificationScenario scenario,
    @NotNull(message = "验证码渠道不能为空")
    AppVerificationChannel channel,
    @NotBlank(message = "验证码接收地址不能为空")
    @Size(max = 320, message = "验证码接收地址长度不能超过 320 个字符")
    String target
) {

    @Override
    public String toString() {
        return "AppVerificationCodeBo[scenario=" + scenario + ", channel=" + channel + ", target=***]";
    }

    /**
     * 无论全局 Jackson 配置是否忽略未知字段，公开认证请求都不能静默接收身份或客户端字段。
     *
     * @param propertyName 未知字段名
     * @param ignored 未知字段值
     */
    @JsonAnySetter
    public void rejectUnknownProperty(String propertyName, Object ignored) {
        throw new IllegalArgumentException("公开验证码申请包含不允许的字段");
    }
}
