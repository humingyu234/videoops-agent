package org.dromara.aivideo.platform.identity.domain.vo;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 运营端创作身份管理的响应模型集合。
 *
 * <p>所有编号均以字符串输出；密码摘要、客户端密钥摘要、app token 原文绝不进入任何响应模型。</p>
 */
public final class AppIdentityAdminVos {

    private AppIdentityAdminVos() {
    }

    /** 创作端用户行。 */
    public record AppUserAdminVo(
        String id,
        String username,
        String displayName,
        String maskedPhone,
        String maskedEmail,
        String status,
        boolean mustChangePassword,
        String credentialRevision,
        String identityRevision,
        String permissionRevision,
        LocalDateTime createTime,
        LocalDateTime updateTime
    ) {
    }

    /** 创作端用户详情。 */
    public record AppUserDetailAdminVo(
        AppUserAdminVo user,
        List<AppRoleAdminVo> roles,
        List<AppSessionAdminVo> sessions
    ) {
    }

    /** 仅当前成功响应可见的创作端初始密码。 */
    public record AppUserInitialPasswordVo(AppUserAdminVo user, String initialPassword) {

        @Override
        public String toString() {
            return "AppUserInitialPasswordVo[user=" + user + ", initialPassword=***]";
        }
    }

    /** 创作端角色行。 */
    public record AppRoleAdminVo(
        String id,
        String roleCode,
        String roleName,
        String scopeType,
        boolean builtIn,
        String status,
        String roleRevision,
        List<String> permissionIds,
        long userReferenceCount,
        LocalDateTime createTime,
        LocalDateTime updateTime
    ) {
    }

    /** 创作端权限注册表行。 */
    public record AppPermissionAdminVo(
        String id,
        String permissionCode,
        String permissionName,
        String resourceType,
        String action,
        String status,
        String permissionRevision
    ) {
    }

    /** 创作端认证客户端行。 */
    public record AppAuthClientAdminVo(
        String id,
        String clientId,
        String clientKey,
        String grantTypes,
        String accessPaths,
        String ipWhitelist,
        long tokenTimeout,
        long activeTimeout,
        String status,
        String clientRevision,
        boolean hasSecret,
        long activeSessionCount,
        LocalDateTime createTime,
        LocalDateTime updateTime
    ) {
    }

    /** 仅当前成功响应可见的创作端认证客户端密钥。 */
    public record AppAuthClientSecretVo(AppAuthClientAdminVo client, String clientSecret) {

        @Override
        public String toString() {
            return "AppAuthClientSecretVo[client=" + client + ", clientSecret=***]";
        }
    }

    /** 运营端查看的创作端在线会话。 */
    public record AppSessionAdminVo(
        String id,
        String appUserId,
        String clientId,
        String deviceName,
        LocalDateTime lastActiveAt
    ) {
    }

    /** 创作端登录日志行。 */
    public record AppLoginLogAdminVo(
        String id,
        String authMethod,
        String maskedIdentifier,
        String clientId,
        Integer resultCode,
        String failureCategory,
        String appUserId,
        String sessionId,
        String ipAddress,
        String deviceSummary,
        String requestId,
        LocalDateTime occurredAt
    ) {
    }

    /** 创作端安全审计行。 */
    public record AppSecurityAuditAdminVo(
        String id,
        String resourceType,
        String resourceId,
        String action,
        String actorType,
        String actorId,
        String beforeDigest,
        String afterDigest,
        String reason,
        String requestId,
        String ipAddress,
        LocalDateTime occurredAt
    ) {
    }
}
