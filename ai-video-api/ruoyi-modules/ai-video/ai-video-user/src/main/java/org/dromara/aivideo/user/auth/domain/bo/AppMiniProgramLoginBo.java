package org.dromara.aivideo.user.auth.domain.bo;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 创作端微信小程序授权登录请求。
 *
 * @param authorizationCode 微信小程序一次性授权码
 */
@JsonIgnoreProperties(ignoreUnknown = false)
public record AppMiniProgramLoginBo(
    @NotBlank(message = "小程序授权码不能为空")
    @Size(max = 4096, message = "小程序授权码长度不能超过 4096 个字符")
    String authorizationCode
) {

    @Override
    public String toString() {
        return "AppMiniProgramLoginBo[authorizationCode=***]";
    }

    /**
     * 拒绝调用方伪造的身份、客户端和修订字段。
     *
     * @param propertyName 未知字段名
     * @param ignored 未知字段值
     */
    @JsonAnySetter
    public void rejectUnknownProperty(String propertyName, Object ignored) {
        throw new IllegalArgumentException("公开小程序登录请求包含不允许的字段");
    }
}
