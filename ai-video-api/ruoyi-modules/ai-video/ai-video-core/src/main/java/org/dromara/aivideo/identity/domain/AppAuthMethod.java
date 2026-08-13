package org.dromara.aivideo.identity.domain;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 创作端登录日志使用的认证方式。
 */
@Getter
@RequiredArgsConstructor
public enum AppAuthMethod {

    /**
     * 密码认证。
     */
    PASSWORD("password"),

    /**
     * 短信验证码认证。
     */
    SMS("sms"),

    /**
     * 邮件验证码认证。
     */
    EMAIL("email"),

    /**
     * 第三方身份认证。
     */
    SOCIAL("social"),

    /**
     * 小程序认证。
     */
    MINI_PROGRAM("mini_program");

    /**
     * 数据库存储值。
     */
    @EnumValue
    private final String value;
}
