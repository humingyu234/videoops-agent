package org.dromara.aivideo.user.security;

/**
 * 创作端身份安全稳定错误码。
 */
public final class AppAuthErrorCodes {

    /** 创作端账号或登录凭据不正确。 */
    public static final int APP_AUTH_CREDENTIALS_INVALID = 46128;
    /** 创作端账号不可用。 */
    public static final int APP_ACCOUNT_UNAVAILABLE = 46129;
    /** 创作端认证客户端不可用。 */
    public static final int APP_AUTH_CLIENT_UNAVAILABLE = 46130;
    /** 创作端会话修订已过期。 */
    public static final int APP_SESSION_REVISION_STALE = 46131;
    /** 请求具有重复、拼接或跨通道凭据。 */
    public static final int MULTIPLE_AUTH_CREDENTIALS_REJECTED = 46132;
    /** 创作端用户必须修改密码。 */
    public static final int APP_PASSWORD_RESET_REQUIRED = 46133;
    /** 创作端角色修订冲突。 */
    public static final int APP_ROLE_REVISION_CONFLICT = 46134;

    private AppAuthErrorCodes() {
    }
}
