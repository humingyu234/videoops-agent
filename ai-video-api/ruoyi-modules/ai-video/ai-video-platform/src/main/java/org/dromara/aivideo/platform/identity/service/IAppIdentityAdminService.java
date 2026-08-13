package org.dromara.aivideo.platform.identity.service;

import org.dromara.aivideo.platform.identity.domain.bo.AppIdentityAdminBos.AppAuthClientQueryBo;
import org.dromara.aivideo.platform.identity.domain.bo.AppIdentityAdminBos.AppLoginLogQueryBo;
import org.dromara.aivideo.platform.identity.domain.bo.AppIdentityAdminBos.AppRoleQueryBo;
import org.dromara.aivideo.platform.identity.domain.bo.AppIdentityAdminBos.AppSecurityAuditQueryBo;
import org.dromara.aivideo.platform.identity.domain.bo.AppIdentityAdminBos.AppSessionQueryBo;
import org.dromara.aivideo.platform.identity.domain.bo.AppIdentityAdminBos.AppUserQueryBo;
import org.dromara.aivideo.platform.identity.domain.bo.AppIdentityAdminBos.ChangeAppUserStatusBo;
import org.dromara.aivideo.platform.identity.domain.bo.AppIdentityAdminBos.CreateAppAuthClientBo;
import org.dromara.aivideo.platform.identity.domain.bo.AppIdentityAdminBos.CreateAppRoleBo;
import org.dromara.aivideo.platform.identity.domain.bo.AppIdentityAdminBos.CreateAppUserBo;
import org.dromara.aivideo.platform.identity.domain.bo.AppIdentityAdminBos.KickoutAppSessionBo;
import org.dromara.aivideo.platform.identity.domain.bo.AppIdentityAdminBos.KickoutAppUserBo;
import org.dromara.aivideo.platform.identity.domain.bo.AppIdentityAdminBos.ReplaceAppRolePermissionsBo;
import org.dromara.aivideo.platform.identity.domain.bo.AppIdentityAdminBos.ReplaceAppUserRolesBo;
import org.dromara.aivideo.platform.identity.domain.bo.AppIdentityAdminBos.ResetAppUserPasswordBo;
import org.dromara.aivideo.platform.identity.domain.bo.AppIdentityAdminBos.RotateAppAuthClientSecretBo;
import org.dromara.aivideo.platform.identity.domain.bo.AppIdentityAdminBos.UpdateAppAuthClientBo;
import org.dromara.aivideo.platform.identity.domain.bo.AppIdentityAdminBos.UpdateAppRoleBo;
import org.dromara.aivideo.platform.identity.domain.bo.AppIdentityAdminBos.UpdateAppUserBo;
import org.dromara.aivideo.platform.identity.domain.vo.AppIdentityAdminVos.AppAuthClientAdminVo;
import org.dromara.aivideo.platform.identity.domain.vo.AppIdentityAdminVos.AppAuthClientSecretVo;
import org.dromara.aivideo.platform.identity.domain.vo.AppIdentityAdminVos.AppLoginLogAdminVo;
import org.dromara.aivideo.platform.identity.domain.vo.AppIdentityAdminVos.AppPermissionAdminVo;
import org.dromara.aivideo.platform.identity.domain.vo.AppIdentityAdminVos.AppRoleAdminVo;
import org.dromara.aivideo.platform.identity.domain.vo.AppIdentityAdminVos.AppSecurityAuditAdminVo;
import org.dromara.aivideo.platform.identity.domain.vo.AppIdentityAdminVos.AppSessionAdminVo;
import org.dromara.aivideo.platform.identity.domain.vo.AppIdentityAdminVos.AppUserDetailAdminVo;
import org.dromara.aivideo.platform.identity.domain.vo.AppIdentityAdminVos.AppUserInitialPasswordVo;
import org.dromara.aivideo.platform.identity.domain.vo.AppIdentityAdminVos.AppUserAdminVo;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.mybatis.core.page.PageQuery;

import java.util.List;

/**
 * 运营端管理独立创作端身份资源的应用服务。
 */
public interface IAppIdentityAdminService {

    PageResult<AppUserAdminVo> pageUsers(AppUserQueryBo query, PageQuery pageQuery);

    AppUserInitialPasswordVo createUser(CreateAppUserBo command);

    AppUserDetailAdminVo getUser(String userId);

    void updateUser(String userId, UpdateAppUserBo command);

    void changeUserStatus(String userId, ChangeAppUserStatusBo command);

    AppUserInitialPasswordVo resetUserPassword(String userId, ResetAppUserPasswordBo command);

    void kickoutUser(String userId, KickoutAppUserBo command);

    void replaceUserRoles(String userId, ReplaceAppUserRolesBo command);

    PageResult<AppRoleAdminVo> pageRoles(AppRoleQueryBo query, PageQuery pageQuery);

    AppRoleAdminVo createRole(CreateAppRoleBo command);

    void updateRole(String roleId, UpdateAppRoleBo command);

    void replaceRolePermissions(String roleId, ReplaceAppRolePermissionsBo command);

    List<AppPermissionAdminVo> listPermissions();

    PageResult<AppAuthClientAdminVo> pageAuthClients(AppAuthClientQueryBo query, PageQuery pageQuery);

    AppAuthClientSecretVo createAuthClient(CreateAppAuthClientBo command);

    void updateAuthClient(String clientId, UpdateAppAuthClientBo command);

    AppAuthClientSecretVo rotateAuthClientSecret(String clientId, RotateAppAuthClientSecretBo command);

    PageResult<AppSessionAdminVo> pageSessions(AppSessionQueryBo query, PageQuery pageQuery);

    void kickoutSession(String sessionId, KickoutAppSessionBo command);

    PageResult<AppLoginLogAdminVo> pageLoginLogs(AppLoginLogQueryBo query, PageQuery pageQuery);

    PageResult<AppSecurityAuditAdminVo> pageSecurityAudits(AppSecurityAuditQueryBo query, PageQuery pageQuery);
}
