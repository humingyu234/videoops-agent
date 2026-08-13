package org.dromara.aivideo.identity.domain;

/**
 * 创作端会话必须失效的稳定原因。
 */
public enum AppSessionInvalidationReason {

    /** 用户账号被停用。 */
    USER_DISABLED,

    /** 密码或其他凭据已变更。 */
    CREDENTIAL_CHANGED,

    /** 第三方身份或联系方式已变更。 */
    IDENTITY_CHANGED,

    /** 角色或权限映射已变更。 */
    PERMISSION_CHANGED,

    /** 认证客户端策略或密钥已变更。 */
    CLIENT_CHANGED,

    /** 组织成员关系已变更。 */
    MEMBERSHIP_CHANGED,

    /** 组织已停用。 */
    ORGANIZATION_DISABLED,

    /** 运营端主动强制下线。 */
    ADMIN_KICKOUT
}
