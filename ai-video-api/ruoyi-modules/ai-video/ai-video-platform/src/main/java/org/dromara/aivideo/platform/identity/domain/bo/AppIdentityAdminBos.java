package org.dromara.aivideo.platform.identity.domain.bo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * 运营端创作身份管理的请求模型集合。
 *
 * <p>所有跨 HTTP 边界的业务编号均使用字符串，避免浏览器端大整数精度丢失。</p>
 */
public final class AppIdentityAdminBos {

    private AppIdentityAdminBos() {
    }

    /** 创作端用户分页筛选。 */
    @Getter
    @Setter
    public static class AppUserQueryBo {
        @Size(max = 64)
        private String username;
        private String status;
    }

    /** 创建创作端用户。 */
    public record CreateAppUserBo(
        @NotBlank @Size(max = 64) String username,
        @NotBlank @Size(max = 64) String displayName,
        @Size(max = 32) String phone,
        @Size(max = 128) String email,
        @NotEmpty Set<@NotBlank String> roleIds
    ) {
    }

    /** 编辑创作端用户资料。 */
    public record UpdateAppUserBo(
        @NotBlank @Size(max = 64) String displayName,
        @Size(max = 32) String phone,
        @Size(max = 128) String email,
        boolean clearPhone,
        boolean clearEmail,
        @NotNull @Positive Long expectedIdentityRevision
    ) {
    }

    /** 启停创作端用户。 */
    public record ChangeAppUserStatusBo(
        @NotBlank String status,
        @NotNull @Positive Long expectedIdentityRevision
    ) {
    }

    /** 生成一次性初始密码。 */
    public record ResetAppUserPasswordBo(@NotNull @Positive Long expectedCredentialRevision) {
    }

    /** 强制撤销创作端用户的全部会话。 */
    public record KickoutAppUserBo(
        @NotBlank @Pattern(regexp = "[a-z][a-z0-9_]{0,63}") String reasonCode
    ) {
    }

    /** 替换创作端用户个人作用域角色。 */
    public record ReplaceAppUserRolesBo(
        @NotNull @Positive Long expectedPermissionRevision,
        @NotNull Set<@NotBlank String> roleIds
    ) {
    }

    /** 创作端角色分页筛选。 */
    @Getter
    @Setter
    public static class AppRoleQueryBo {
        @Size(max = 64)
        private String roleCode;
        @Size(max = 16)
        private String scopeType;
        @Size(max = 16)
        private String status;
    }

    /** 创建非内置创作端角色。 */
    public record CreateAppRoleBo(
        @NotBlank @Pattern(regexp = "[a-z][a-z0-9_]{0,63}") String roleCode,
        @NotBlank @Size(max = 64) String roleName,
        @NotBlank @Pattern(regexp = "personal|organization") String scopeType,
        @NotBlank String status
    ) {
    }

    /** 编辑创作端角色元数据和状态。 */
    public record UpdateAppRoleBo(
        @NotBlank @Size(max = 64) String roleName,
        @NotBlank String status,
        @NotNull @Positive Long expectedRoleRevision
    ) {
    }

    /** 替换创作端角色权限。 */
    public record ReplaceAppRolePermissionsBo(
        @NotNull @Positive Long expectedRoleRevision,
        @NotNull Set<@NotBlank String> permissionIds
    ) {
    }

    /** 创作端认证客户端分页筛选。 */
    @Getter
    @Setter
    public static class AppAuthClientQueryBo {
        @Size(max = 64)
        private String clientKey;
        @Size(max = 16)
        private String status;
    }

    /** 创建创作端认证客户端。 */
    public record CreateAppAuthClientBo(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._-]{1,63}") String clientKey,
        @NotBlank @Size(max = 500) String grantTypes,
        @NotBlank @Size(max = 1000) String accessPaths,
        @Size(max = 1000) String ipWhitelist,
        @Min(1) long tokenTimeout,
        @Min(1) long activeTimeout,
        @NotBlank String status
    ) {
    }

    /** 编辑创作端认证客户端策略或状态。 */
    public record UpdateAppAuthClientBo(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._-]{1,63}") String clientKey,
        @NotBlank @Size(max = 500) String grantTypes,
        @NotBlank @Size(max = 1000) String accessPaths,
        @Size(max = 1000) String ipWhitelist,
        @Min(1) long tokenTimeout,
        @Min(1) long activeTimeout,
        @NotBlank String status,
        @NotNull @Positive Long expectedClientRevision
    ) {
    }

    /** 轮换创作端认证客户端密钥。 */
    public record RotateAppAuthClientSecretBo(@NotNull @Positive Long expectedClientRevision) {
    }

    /** 创作端在线会话分页筛选。 */
    @Getter
    @Setter
    public static class AppSessionQueryBo {
        @Pattern(regexp = "[1-9]\\d{0,18}")
        private String appUserId;
        @Size(max = 64)
        private String clientId;
    }

    /** 撤销单个创作端会话。 */
    public record KickoutAppSessionBo(
        @NotBlank @Pattern(regexp = "[a-z][a-z0-9_]{0,63}") String reasonCode
    ) {
    }

    /** 创作端登录日志分页筛选。 */
    @Getter
    @Setter
    public static class AppLoginLogQueryBo {
        @Pattern(regexp = "[1-9]\\d{0,18}")
        private String appUserId;
        @Size(max = 64)
        private String clientId;
        @Min(100) @Max(599)
        private Integer resultCode;
        private LocalDateTime occurredAfter;
        private LocalDateTime occurredBefore;
    }

    /** 创作端安全审计分页筛选。 */
    @Getter
    @Setter
    public static class AppSecurityAuditQueryBo {
        @Size(max = 64)
        private String resourceType;
        @Size(max = 64)
        private String resourceId;
        @Size(max = 64)
        private String action;
        @Size(max = 16)
        private String actorType;
        @Pattern(regexp = "[1-9]\\d{0,18}")
        private String actorId;
        private LocalDateTime occurredAfter;
        private LocalDateTime occurredBefore;
    }
}
