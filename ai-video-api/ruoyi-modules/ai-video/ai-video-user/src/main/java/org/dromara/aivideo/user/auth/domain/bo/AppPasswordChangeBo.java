package org.dromara.aivideo.user.auth.domain.bo;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 创作端登录后修改密码请求。
 *
 * <p>用户身份和凭据修订均由当前 app 会话确定，禁止由请求体提供。</p>
 *
 * @param currentPassword 当前明文密码，仅用于本次校验
 * @param newPassword 新明文密码，仅用于本次修改
 */
@JsonIgnoreProperties(ignoreUnknown = false)
public record AppPasswordChangeBo(
    @NotBlank(message = "当前密码不能为空")
    @Size(max = 256, message = "当前密码长度不能超过 256 个字符")
    String currentPassword,
    @NotBlank(message = "新密码不能为空")
    @Size(max = 256, message = "新密码长度不能超过 256 个字符")
    String newPassword
) {

    @Override
    public String toString() {
        return "AppPasswordChangeBo[currentPassword=***, newPassword=***]";
    }

    /**
     * 已登录用户的身份和凭据修订只取当前 app 会话，不能由请求体覆盖或附带。
     *
     * @param propertyName 未知字段名
     * @param ignored 未知字段值
     */
    @JsonAnySetter
    public void rejectUnknownProperty(String propertyName, Object ignored) {
        throw new IllegalArgumentException("改密请求包含不允许的字段");
    }
}
