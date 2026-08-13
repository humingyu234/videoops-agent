package org.dromara.aivideo.identity.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.dromara.aivideo.identity.service.IAppSessionService;
import org.dromara.aivideo.identity.domain.AppAuthClient;
import org.dromara.aivideo.identity.domain.AppIdentityStatus;
import org.dromara.aivideo.identity.domain.AppUser;
import org.dromara.aivideo.identity.mapper.AppAuthClientMapper;
import org.dromara.aivideo.identity.mapper.AppUserMapper;
import org.dromara.aivideo.identity.dto.AppPrincipalSnapshotDTO;
import org.dromara.aivideo.identity.domain.AppSessionInvalidationReason;
import org.dromara.aivideo.identity.dto.AppWorkspaceSessionSnapshotDTO;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 校验当前 app 会话所绑定的用户和认证客户端修订号。
 */
@Component
@ConditionalOnAppSecurityEnabled
public class AppSessionRevisionGuard {

    private static final int APP_SESSION_REVISION_STALE_CODE = 46131;

    private final AppLoginHelper loginHelper;
    private final AppUserMapper userMapper;
    private final AppAuthClientMapper authClientMapper;
    private final IAppSessionService sessionService;

    /**
     * 创建创作端会话修订守卫。
     *
     * @param loginHelper 创作端登录会话入口
     * @param userMapper 创作端用户数据访问接口
     * @param authClientMapper 创作端认证客户端数据访问接口
     * @param sessionService 创作端会话失效服务
     */
    public AppSessionRevisionGuard(AppLoginHelper loginHelper, AppUserMapper userMapper,
                                   AppAuthClientMapper authClientMapper, IAppSessionService sessionService) {
        this.loginHelper = Objects.requireNonNull(loginHelper, "创作端登录助手不能为空");
        this.userMapper = Objects.requireNonNull(userMapper, "创作端用户数据访问接口不能为空");
        this.authClientMapper = Objects.requireNonNull(authClientMapper, "创作端认证客户端数据访问接口不能为空");
        this.sessionService = Objects.requireNonNull(sessionService, "创作端会话失效服务不能为空");
    }

    /**
     * 校验当前 app 会话的凭据、身份、权限和客户端修订号。
     */
    public void checkCurrentSession() {
        AppLoginUser loginUser = loginHelper.getLoginUser();
        AppPrincipalSnapshotDTO principal = loginUser.principal();
        AppUser user = findActiveUser(principal.appUserId());
        AppAuthClient authClient = findActiveClient(principal.clientId());
        AppSessionInvalidationReason staleReason = staleReason(principal, user, authClient);
        if (staleReason != null) {
            sessionService.invalidateUserSessions(principal.appUserId(), staleReason);
            loginHelper.logout();
            throw new ServiceException("创作端会话修订已过期", APP_SESSION_REVISION_STALE_CODE);
        }
    }

    /**
     * 查询当前有效的创作端用户，不读取任何运营端身份表。
     *
     * @param appUserId 创作端用户编号
     * @return 创作端用户；不存在时返回空
     */
    private AppUser findActiveUser(Long appUserId) {
        if (appUserId == null || appUserId <= 0) {
            return null;
        }
        return userMapper.selectOne(new LambdaQueryWrapper<AppUser>()
            .eq(AppUser::getUserId, appUserId)
            .eq(AppUser::getDelFlag, "0"));
    }

    /**
     * 查询当前有效的创作端认证客户端，不读取任何运营端客户端表。
     *
     * @param clientId 创作端认证客户端标识
     * @return 创作端认证客户端；不存在时返回空
     */
    private AppAuthClient findActiveClient(String clientId) {
        if (clientId == null || clientId.isBlank()) {
            return null;
        }
        return authClientMapper.selectOne(new LambdaQueryWrapper<AppAuthClient>()
            .eq(AppAuthClient::getClientId, clientId)
            .eq(AppAuthClient::getDelFlag, "0"));
    }

    /**
     * 判断 app 主体快照是否已过期。
     *
     * @param principal 会话主体快照
     * @param user 当前创作端用户
     * @param authClient 当前创作端认证客户端
     * @return 会话失效原因；仍有效时返回 null
     */
    private AppSessionInvalidationReason staleReason(AppPrincipalSnapshotDTO principal, AppUser user,
                                                      AppAuthClient authClient) {
        if (principal == null || user == null || user.getStatus() != AppIdentityStatus.ACTIVE) {
            return AppSessionInvalidationReason.USER_DISABLED;
        }
        if (authClient == null || authClient.getStatus() != AppIdentityStatus.ACTIVE
            || !Objects.equals(principal.clientRevision(), authClient.getClientRevision())) {
            return AppSessionInvalidationReason.CLIENT_CHANGED;
        }
        if (!Objects.equals(principal.credentialRevision(), user.getCredentialRevision())) {
            return AppSessionInvalidationReason.CREDENTIAL_CHANGED;
        }
        if (!Objects.equals(principal.identityRevision(), user.getIdentityRevision())
            || personalWorkspaceRevisionIsStale(principal.workspace(), user)) {
            return AppSessionInvalidationReason.IDENTITY_CHANGED;
        }
        if (!Objects.equals(principal.permissionRevision(), user.getPermissionRevision())) {
            return AppSessionInvalidationReason.PERMISSION_CHANGED;
        }
        return null;
    }

    /**
     * P0-A 仅校验个人工作区：其工作区修订号固定等于用户身份修订号，且没有成员修订号。
     * P0-B 的组织工作区事实源校验由后续组织授权模块补充。
     *
     * @param workspace 会话工作区快照
     * @param user 当前创作端用户
     * @return 个人工作区快照过期时返回 true
     */
    private boolean personalWorkspaceRevisionIsStale(AppWorkspaceSessionSnapshotDTO workspace, AppUser user) {
        if (workspace == null) {
            return true;
        }
        if (!"personal".equals(workspace.workspaceType())) {
            return false;
        }
        return !Objects.equals(workspace.workspaceRevision(), user.getIdentityRevision())
            || workspace.membershipRevision() != null;
    }
}
