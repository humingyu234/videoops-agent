package org.dromara.aivideo.identity.domain;

import java.util.Arrays;

/**
 * 创作端身份安全审计允许持久化的固定原因代码。
 */
public enum AppSecurityAuditReason {

    /** 注册创作端账号。 */
    IDENTITY_REGISTRATION("identity_registration"),

    /** 完成密码认证。 */
    PASSWORD_AUTHENTICATION("password_authentication"),

    /** 完成短信或邮件验证码认证。 */
    VERIFICATION_CODE_AUTHENTICATION("verification_code_authentication"),

    /** 完成已绑定第三方身份认证。 */
    EXTERNAL_IDENTITY_AUTHENTICATION("external_identity_authentication"),

    /** 用户主动修改密码。 */
    PASSWORD_CHANGE("password_change"),

    /** 用户主动撤销创作端会话。 */
    SESSION_REVOCATION("session_revocation"),

    /** 运营端重置密码。 */
    PASSWORD_RESET("password_reset"),

    /** 用户通过已验证联系方式找回密码。 */
    PASSWORD_RECOVERY("password_recovery"),

    /** 运营端变更账号状态。 */
    ACCOUNT_STATUS_CHANGE("account_status_change"),

    /** 运营端变更账号资料。 */
    USER_PROFILE_CHANGE("user_profile_change"),

    /** 绑定第三方身份。 */
    SOCIAL_IDENTITY_BIND("social_identity_bind"),

    /** 解绑第三方身份。 */
    SOCIAL_IDENTITY_UNBIND("social_identity_unbind"),

    /** 替换创作端用户角色。 */
    USER_ROLE_REPLACEMENT("user_role_replacement"),

    /** 替换创作端角色权限。 */
    ROLE_PERMISSION_REPLACEMENT("role_permission_replacement"),

    /** 创建创作端角色。 */
    ROLE_CREATION("role_creation"),

    /** 修改创作端角色元数据或状态。 */
    ROLE_METADATA_CHANGE("role_metadata_change"),

    /** 创建创作端认证客户端。 */
    AUTH_CLIENT_CREATION("auth_client_creation"),

    /** 修改创作端认证客户端策略。 */
    AUTH_CLIENT_CHANGE("auth_client_change"),

    /** 轮换创作端认证客户端密钥。 */
    AUTH_CLIENT_SECRET_ROTATION("auth_client_secret_rotation"),

    /** 运营端强制撤销创作端会话。 */
    ADMIN_KICKOUT("admin_kickout"),

    /** 运营端因安全风险强制撤销创作端会话。 */
    ADMIN_KICKOUT_SECURITY("admin_kickout_security"),

    /** 运营端因策略违规强制撤销创作端会话。 */
    ADMIN_KICKOUT_POLICY("admin_kickout_policy"),

    /** 运营端在客服或支持工单处理中强制撤销创作端会话。 */
    ADMIN_KICKOUT_SUPPORT("admin_kickout_support");

    private final String code;

    AppSecurityAuditReason(String code) {
        this.code = code;
    }

    /**
     * 返回持久化的稳定原因代码。
     *
     * @return 原因代码
     */
    public String code() {
        return code;
    }

    /**
     * 判断字符串是否为允许持久化的原因代码。
     *
     * @param candidate 待校验原因代码
     * @return 是否允许
     */
    public static boolean isAllowedCode(String candidate) {
        return Arrays.stream(values()).anyMatch(reason -> reason.code.equals(candidate));
    }

    /**
     * 判断代码是否是运营端允许写入的创作会话强制下线原因。
     *
     * @param candidate 外部提交的原因代码
     * @return 是否为受控强制下线原因
     */
    public static boolean isAdminKickoutCode(String candidate) {
        return ADMIN_KICKOUT.code.equals(candidate)
            || ADMIN_KICKOUT_SECURITY.code.equals(candidate)
            || ADMIN_KICKOUT_POLICY.code.equals(candidate)
            || ADMIN_KICKOUT_SUPPORT.code.equals(candidate);
    }
}
