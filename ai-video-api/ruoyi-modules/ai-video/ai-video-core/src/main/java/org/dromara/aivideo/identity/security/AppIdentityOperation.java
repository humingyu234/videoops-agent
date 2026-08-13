package org.dromara.aivideo.identity.security;

/**
 * 运营端对创作端身份执行的受控操作。
 */
public enum AppIdentityOperation {

    /**
     * 创建创作端用户。
     */
    REGISTER_APP_USER,

    /**
     * 修改创作端用户密码。
     */
    CHANGE_PASSWORD,

    /**
     * 重置创作端用户密码。
     */
    RESET_PASSWORD,

    /**
     * 修改创作端用户状态。
     */
    CHANGE_STATUS,

    /**
     * 修改创作端用户资料。
     */
    UPDATE_APP_USER,

    /**
     * 创建创作端角色。
     */
    CREATE_APP_ROLE,

    /**
     * 修改创作端角色元数据或状态。
     */
    UPDATE_APP_ROLE,

    /**
     * 创建创作端认证客户端。
     */
    CREATE_APP_AUTH_CLIENT,

    /**
     * 修改创作端认证客户端策略。
     */
    UPDATE_APP_AUTH_CLIENT,

    /**
     * 轮换创作端认证客户端密钥。
     */
    ROTATE_APP_AUTH_CLIENT_SECRET,

    /**
     * 撤销单个创作端会话。
     */
    REVOKE_APP_SESSION,

    /**
     * 绑定创作端第三方身份。
     */
    BIND_SOCIAL_IDENTITY,

    /**
     * 解绑创作端第三方身份。
     */
    UNBIND_SOCIAL_IDENTITY,

    /**
     * 替换创作端用户的个人角色集合。
     */
    REPLACE_USER_ROLES,

    /**
     * 替换创作端角色的权限集合。
     */
    REPLACE_ROLE_PERMISSIONS
}
