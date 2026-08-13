package org.dromara.aivideo.identity.security;

/**
 * 创作端验证码使用场景。
 */
public enum AppVerificationScenario {

    /** 登录验证码。 */
    LOGIN,

    /** 找回密码验证码。 */
    PASSWORD_RECOVERY;

    /**
     * Redis 状态机使用的稳定小写值。
     */
    public String key() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
