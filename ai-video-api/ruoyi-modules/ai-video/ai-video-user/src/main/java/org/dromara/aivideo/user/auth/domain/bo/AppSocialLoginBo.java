package org.dromara.aivideo.user.auth.domain.bo;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 创作端第三方授权登录请求。
 *
 * <p>授权码和回调状态仅能交给外部身份适配器一次性消费；请求体不能携带用户、客户端或修订号。</p>
 *
 * @param provider 第三方来源白名单键
 * @param authorizationCode 第三方一次性授权码
 * @param state 回调状态令牌
 */
@JsonIgnoreProperties(ignoreUnknown = false)
public record AppSocialLoginBo(
    @NotBlank(message = "第三方来源不能为空")
    @Pattern(regexp = "[a-z0-9_]{1,32}", message = "第三方来源格式不正确")
    String provider,
    @NotBlank(message = "第三方授权码不能为空")
    @Size(max = 4096, message = "第三方授权码长度不能超过 4096 个字符")
    String authorizationCode,
    @NotBlank(message = "回调状态不能为空")
    @Size(max = 1024, message = "回调状态长度不能超过 1024 个字符")
    String state
) {

    @Override
    public String toString() {
        return "AppSocialLoginBo[provider=" + provider + ", authorizationCode=***, state=***]";
    }

    /**
     * 拒绝调用方伪造的身份、客户端和修订字段。
     *
     * @param propertyName 未知字段名
     * @param ignored 未知字段值
     */
    @JsonAnySetter
    public void rejectUnknownProperty(String propertyName, Object ignored) {
        throw new IllegalArgumentException("公开第三方登录请求包含不允许的字段");
    }
}
