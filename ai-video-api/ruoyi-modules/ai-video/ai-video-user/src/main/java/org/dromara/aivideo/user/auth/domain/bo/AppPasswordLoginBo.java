package org.dromara.aivideo.user.auth.domain.bo;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 创作端密码登录请求。
 *
 * <p>认证客户端、用户编号、角色和工作区均由服务端请求上下文决定，禁止由请求体提供。</p>
 *
 * @param identifier 用户名、手机号或邮箱
 * @param password 本次校验使用的明文密码
 */
@JsonIgnoreProperties(ignoreUnknown = false)
public record AppPasswordLoginBo(
    @NotBlank(message = "登录标识不能为空")
    @Size(max = 128, message = "登录标识长度不能超过 128 个字符")
    String identifier,
    @NotBlank(message = "密码不能为空")
    @Size(max = 256, message = "密码长度不能超过 256 个字符")
    String password
) {

    @Override
    public String toString() {
        return "AppPasswordLoginBo[identifier=***, password=***]";
    }

    /**
     * 公开认证请求不能静默接收用户、租户、客户端或修订号等调用方可控字段。
     *
     * @param propertyName 未知字段名
     * @param ignored 未知字段值
     */
    @JsonAnySetter
    public void rejectUnknownProperty(String propertyName, Object ignored) {
        throw new IllegalArgumentException("公开密码登录请求包含不允许的字段");
    }
}
