package org.dromara.aivideo.identity.security;

import org.dromara.aivideo.identity.service.IAppPermissionService;
import org.dromara.aivideo.identity.domain.AppUser;
import org.dromara.aivideo.identity.dto.AppWorkspaceSessionSnapshotDTO;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.Objects;

/**
 * 构造 P0-A 阶段个人工作区的固定会话快照。
 */
@Component
@ConditionalOnAppSecurityEnabled
public class AppPersonalWorkspaceSnapshotProvider {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final IAppPermissionService permissionService;
    private final AppSaTokenProperties tokenProperties;

    /**
     * 创建个人工作区快照提供者。
     *
     * @param permissionService 创作端权限查询服务
     * @param tokenProperties 创作端独立密钥配置
     */
    public AppPersonalWorkspaceSnapshotProvider(IAppPermissionService permissionService,
                                                AppSaTokenProperties tokenProperties) {
        this.permissionService = Objects.requireNonNull(permissionService, "创作端权限服务不能为空");
        this.tokenProperties = Objects.requireNonNull(tokenProperties, "创作端令牌配置不能为空");
    }

    /**
     * 为创作端用户创建个人工作区快照。
     *
     * @param user 已通过身份校验的创作端用户
     * @return 个人工作区会话快照
     */
    public AppWorkspaceSessionSnapshotDTO personalWorkspace(AppUser user) {
        if (user == null || user.getUserId() == null || user.getUserId() <= 0
            || user.getPersonalTenantId() == null || user.getPersonalTenantId() <= 0
            || user.getIdentityRevision() == null || user.getIdentityRevision() <= 0) {
            throw new ServiceException("个人工作区快照所需用户信息不完整");
        }
        return new AppWorkspaceSessionSnapshotDTO(
            hmacWorkspaceKey(user.getPersonalTenantId()),
            "personal",
            user.getPersonalTenantId(),
            "app_user",
            user.getUserId(),
            "personal",
            user.getUserId(),
            "personal_creator",
            permissionService.permissionCodes(user.getUserId()),
            user.getIdentityRevision(),
            null);
    }

    /**
     * 生成不会泄露个人租户编号的稳定工作区键。
     *
     * @param personalTenantId 个人租户编号
     * @return URL 安全的 HMAC 工作区键
     */
    private String hmacWorkspaceKey(long personalTenantId) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(tokenProperties.getWorkspaceKeySecret().getBytes(StandardCharsets.UTF_8),
                HMAC_ALGORITHM));
            byte[] digest = mac.doFinal(("personal:" + personalTenantId).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("个人工作区稳定键计算失败", exception);
        }
    }
}
