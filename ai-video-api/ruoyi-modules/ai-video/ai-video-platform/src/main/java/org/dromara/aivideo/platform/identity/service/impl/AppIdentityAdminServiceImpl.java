package org.dromara.aivideo.platform.identity.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.dromara.aivideo.identity.service.IAppAuthClientService;
import org.dromara.aivideo.identity.service.IAppIdentityService;
import org.dromara.aivideo.identity.service.IAppPermissionService;
import org.dromara.aivideo.identity.service.IAppSecurityAuditService;
import org.dromara.aivideo.identity.service.IAppSessionService;
import org.dromara.aivideo.identity.event.AppSessionInvalidationEvent;
import org.dromara.aivideo.identity.dto.CreateAppAuthClientDTO;
import org.dromara.aivideo.identity.dto.RotateAppAuthClientSecretDTO;
import org.dromara.aivideo.identity.dto.UpdateAppAuthClientDTO;
import org.dromara.aivideo.identity.dto.ChangeAppUserStatusDTO;
import org.dromara.aivideo.identity.dto.RegisterAppUserDTO;
import org.dromara.aivideo.identity.dto.ResetAppPasswordDTO;
import org.dromara.aivideo.identity.dto.UpdateAppUserProfileDTO;
import org.dromara.aivideo.identity.dto.CreateAppRoleDTO;
import org.dromara.aivideo.identity.dto.UpdateAppRoleDTO;
import org.dromara.aivideo.identity.dto.AppAuthClientSecretDTO;
import org.dromara.aivideo.identity.dto.AppRegisteredIdentityDTO;
import org.dromara.aivideo.identity.dto.AppRoleDTO;
import org.dromara.aivideo.identity.domain.AppActorType;
import org.dromara.aivideo.identity.domain.AppAuthClient;
import org.dromara.aivideo.identity.domain.AppIdentityStatus;
import org.dromara.aivideo.identity.domain.AppLoginLog;
import org.dromara.aivideo.identity.domain.AppPermission;
import org.dromara.aivideo.identity.domain.AppRole;
import org.dromara.aivideo.identity.domain.AppRolePermission;
import org.dromara.aivideo.identity.domain.AppSecurityAudit;
import org.dromara.aivideo.identity.domain.AppSecurityAuditReason;
import org.dromara.aivideo.identity.domain.AppUser;
import org.dromara.aivideo.identity.domain.AppUserRole;
import org.dromara.aivideo.identity.mapper.AppAuthClientMapper;
import org.dromara.aivideo.identity.mapper.AppLoginLogMapper;
import org.dromara.aivideo.identity.mapper.AppPermissionMapper;
import org.dromara.aivideo.identity.mapper.AppRoleMapper;
import org.dromara.aivideo.identity.mapper.AppRolePermissionMapper;
import org.dromara.aivideo.identity.mapper.AppSecurityAuditMapper;
import org.dromara.aivideo.identity.mapper.AppUserMapper;
import org.dromara.aivideo.identity.mapper.AppUserRoleMapper;
import org.dromara.aivideo.identity.dto.AppSecurityAuditDTO;
import org.dromara.aivideo.identity.domain.AppSessionInvalidationReason;
import org.dromara.aivideo.identity.dto.AppSessionQueryDTO;
import org.dromara.aivideo.identity.dto.AppSessionSummaryDTO;
import org.dromara.aivideo.identity.security.AppActorContext;
import org.dromara.aivideo.identity.security.AppIdentityOperation;
import org.dromara.aivideo.identity.security.IAppIdentityOperationAuthorizationService;
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
import org.dromara.aivideo.platform.identity.domain.vo.AppIdentityAdminVos.AppUserAdminVo;
import org.dromara.aivideo.platform.identity.domain.vo.AppIdentityAdminVos.AppUserDetailAdminVo;
import org.dromara.aivideo.platform.identity.domain.vo.AppIdentityAdminVos.AppUserInitialPasswordVo;
import org.dromara.aivideo.platform.identity.service.IAppIdentityAdminService;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 运营端对独立创作端身份资源的适配服务。
 *
 * <p>这是平台模块唯一读取默认 sys 登录主体的位置：得到编号后立即转换成
 * {@link AppActorContext}，核心层从不接收 sys {@code LoginUser}。</p>
 */
@Service
public class AppIdentityAdminServiceImpl implements IAppIdentityAdminService, IAppIdentityOperationAuthorizationService {

    private static final String APP_USER_RESOURCE = "app_user";
    private static final char[] PASSWORD_ALPHABET =
        "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789".toCharArray();

    private final AppUserMapper userMapper;
    private final AppRoleMapper roleMapper;
    private final AppPermissionMapper permissionMapper;
    private final AppUserRoleMapper userRoleMapper;
    private final AppRolePermissionMapper rolePermissionMapper;
    private final AppAuthClientMapper authClientMapper;
    private final AppLoginLogMapper loginLogMapper;
    private final AppSecurityAuditMapper securityAuditMapper;
    private final IAppSecurityAuditService securityAuditService;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectProvider<IAppIdentityService> identityServiceProvider;
    private final ObjectProvider<IAppPermissionService> permissionServiceProvider;
    private final ObjectProvider<IAppAuthClientService> authClientServiceProvider;
    private final ObjectProvider<IAppSessionService> sessionServiceProvider;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * 创建运营端创作身份管理适配服务。
     *
     * <p>核心写服务通过 {@link ObjectProvider} 延迟取得，以避免授权 SPI 在应用启动时解析时
     * 形成平台适配器与核心服务的循环依赖。</p>
     */
    public AppIdentityAdminServiceImpl(AppUserMapper userMapper, AppRoleMapper roleMapper,
                                       AppPermissionMapper permissionMapper, AppUserRoleMapper userRoleMapper,
                                       AppRolePermissionMapper rolePermissionMapper,
                                       AppAuthClientMapper authClientMapper, AppLoginLogMapper loginLogMapper,
                                       AppSecurityAuditMapper securityAuditMapper,
                                       IAppSecurityAuditService securityAuditService,
                                       ApplicationEventPublisher eventPublisher,
                                       ObjectProvider<IAppIdentityService> identityServiceProvider,
                                       ObjectProvider<IAppPermissionService> permissionServiceProvider,
                                       ObjectProvider<IAppAuthClientService> authClientServiceProvider,
                                       ObjectProvider<IAppSessionService> sessionServiceProvider) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.permissionMapper = permissionMapper;
        this.userRoleMapper = userRoleMapper;
        this.rolePermissionMapper = rolePermissionMapper;
        this.authClientMapper = authClientMapper;
        this.loginLogMapper = loginLogMapper;
        this.securityAuditMapper = securityAuditMapper;
        this.securityAuditService = securityAuditService;
        this.eventPublisher = eventPublisher;
        this.identityServiceProvider = identityServiceProvider;
        this.permissionServiceProvider = permissionServiceProvider;
        this.authClientServiceProvider = authClientServiceProvider;
        this.sessionServiceProvider = sessionServiceProvider;
    }

    /**
     * 为核心身份服务判定是否由当前 sys 用户携带了与操作严格匹配的权限。
     */
    @Override
    public boolean isAuthorized(AppActorContext actor, AppIdentityOperation operation, long targetResourceId) {
        try {
            AppActorContext currentActor = currentActor();
            String permission = permissionFor(operation);
            return permission != null && currentActor.equals(actor) && StpUtil.hasPermission(permission);
        } catch (ServiceException ignored) {
            return false;
        }
    }

    @Override
    public PageResult<AppUserAdminVo> pageUsers(AppUserQueryBo query, PageQuery pageQuery) {
        AppUserQueryBo effectiveQuery = query == null ? new AppUserQueryBo() : query;
        AppIdentityStatus status = parseOptionalStatus(effectiveQuery.getStatus());
        Page<AppUser> page = userMapper.selectPage(page(pageQuery), new LambdaQueryWrapper<AppUser>()
            .eq(AppUser::getDelFlag, "0")
            .like(notBlank(effectiveQuery.getUsername()), AppUser::getUsername, trim(effectiveQuery.getUsername()))
            .eq(status != null, AppUser::getStatus, status)
            .orderByDesc(AppUser::getCreateTime));
        return PageResult.build(page.getRecords().stream().map(this::toUserVo).toList(), page.getTotal());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AppUserInitialPasswordVo createUser(CreateAppUserBo command) {
        AppActorContext actor = currentActor();
        String initialPassword = generatePassword();
        AppRegisteredIdentityDTO registered = identityService().register(new RegisterAppUserDTO(
            command.username(), initialPassword, command.displayName(), command.phone(), command.email()), actor);
        AppUser registeredUser = requireUser(registered.userId());
        permissionService().replaceUserRoles(registered.userId(), registeredUser.getPermissionRevision(),
            parseIds(command.roleIds(), "角色编号"), actor);
        return new AppUserInitialPasswordVo(toUserVo(requireUser(registered.userId())), initialPassword);
    }

    @Override
    public AppUserDetailAdminVo getUser(String userId) {
        AppUser user = requireUser(parseId(userId, "创作端用户编号"));
        return new AppUserDetailAdminVo(toUserVo(user), rolesForUser(user.getUserId()), sessionsForUser(user.getUserId()));
    }

    @Override
    public void updateUser(String userId, UpdateAppUserBo command) {
        identityService().updateProfile(new UpdateAppUserProfileDTO(
            parseId(userId, "创作端用户编号"), command.displayName(), command.phone(), command.email(),
            command.clearPhone(), command.clearEmail(), command.expectedIdentityRevision()), currentActor());
    }

    @Override
    public void changeUserStatus(String userId, ChangeAppUserStatusBo command) {
        AppIdentityStatus status = parseStatus(command.status());
        if (status == AppIdentityStatus.INACTIVE) {
            throw new ServiceException("创作端用户状态只允许启用或停用");
        }
        identityService().changeStatus(new ChangeAppUserStatusDTO(
            parseId(userId, "创作端用户编号"), status, command.expectedIdentityRevision()), currentActor());
    }

    @Override
    public AppUserInitialPasswordVo resetUserPassword(String userId, ResetAppUserPasswordBo command) {
        long appUserId = parseId(userId, "创作端用户编号");
        String initialPassword = generatePassword();
        identityService().resetPassword(new ResetAppPasswordDTO(appUserId, initialPassword,
            command.expectedCredentialRevision()), currentActor());
        return new AppUserInitialPasswordVo(toUserVo(requireUser(appUserId)), initialPassword);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void kickoutUser(String userId, KickoutAppUserBo command) {
        long appUserId = parseId(userId, "创作端用户编号");
        AppActorContext actor = currentActor();
        String auditReason = requireAdminKickoutReason(command.reasonCode());
        requireUser(appUserId);
        securityAuditService.append(new AppSecurityAuditDTO(
            APP_USER_RESOURCE,
            Long.toString(appUserId),
            "user_kicked_out",
            actor.actorType(),
            actor.actorId(),
            null,
            null,
            auditReason));
        eventPublisher.publishEvent(AppSessionInvalidationEvent.forUsers(
            Set.of(appUserId), AppSessionInvalidationReason.ADMIN_KICKOUT));
    }

    @Override
    public void replaceUserRoles(String userId, ReplaceAppUserRolesBo command) {
        permissionService().replaceUserRoles(parseId(userId, "创作端用户编号"), command.expectedPermissionRevision(),
            parseIds(command.roleIds(), "角色编号"), currentActor());
    }

    @Override
    public PageResult<AppRoleAdminVo> pageRoles(AppRoleQueryBo query, PageQuery pageQuery) {
        AppRoleQueryBo effectiveQuery = query == null ? new AppRoleQueryBo() : query;
        AppIdentityStatus status = parseOptionalStatus(effectiveQuery.getStatus());
        Page<AppRole> page = roleMapper.selectPage(page(pageQuery), new LambdaQueryWrapper<AppRole>()
            .eq(AppRole::getDelFlag, "0")
            .like(notBlank(effectiveQuery.getRoleCode()), AppRole::getRoleCode, trim(effectiveQuery.getRoleCode()))
            .eq(notBlank(effectiveQuery.getScopeType()), AppRole::getScopeType, trim(effectiveQuery.getScopeType()))
            .eq(status != null, AppRole::getStatus, status)
            .orderByAsc(AppRole::getRoleCode));
        return PageResult.build(page.getRecords().stream().map(this::toRoleVo).toList(), page.getTotal());
    }

    @Override
    public AppRoleAdminVo createRole(CreateAppRoleBo command) {
        AppRoleDTO role = permissionService().createRole(new CreateAppRoleDTO(command.roleCode(), command.roleName(),
            command.scopeType(), parseStatus(command.status())), currentActor());
        return toRoleVo(role);
    }

    @Override
    public void updateRole(String roleId, UpdateAppRoleBo command) {
        permissionService().updateRole(new UpdateAppRoleDTO(parseId(roleId, "创作端角色编号"), command.roleName(),
            parseStatus(command.status()), command.expectedRoleRevision()), currentActor());
    }

    @Override
    public void replaceRolePermissions(String roleId, ReplaceAppRolePermissionsBo command) {
        permissionService().replaceRolePermissions(parseId(roleId, "创作端角色编号"), command.expectedRoleRevision(),
            parseIds(command.permissionIds(), "权限编号"), currentActor());
    }

    @Override
    public List<AppPermissionAdminVo> listPermissions() {
        return permissionMapper.selectList(new LambdaQueryWrapper<AppPermission>()
                .orderByAsc(AppPermission::getPermissionCode))
            .stream()
            .map(this::toPermissionVo)
            .toList();
    }

    @Override
    public PageResult<AppAuthClientAdminVo> pageAuthClients(AppAuthClientQueryBo query, PageQuery pageQuery) {
        AppAuthClientQueryBo effectiveQuery = query == null ? new AppAuthClientQueryBo() : query;
        AppIdentityStatus status = parseOptionalStatus(effectiveQuery.getStatus());
        Page<AppAuthClient> page = authClientMapper.selectPage(page(pageQuery), new LambdaQueryWrapper<AppAuthClient>()
            .eq(AppAuthClient::getDelFlag, "0")
            .like(notBlank(effectiveQuery.getClientKey()), AppAuthClient::getClientKey, trim(effectiveQuery.getClientKey()))
            .eq(status != null, AppAuthClient::getStatus, status)
            .orderByAsc(AppAuthClient::getClientKey));
        return PageResult.build(page.getRecords().stream().map(this::toAuthClientVo).toList(), page.getTotal());
    }

    @Override
    public AppAuthClientSecretVo createAuthClient(CreateAppAuthClientBo command) {
        AppAuthClientSecretDTO secret = authClientService().create(new CreateAppAuthClientDTO(command.clientKey(),
            command.grantTypes(), command.accessPaths(), command.ipWhitelist(), command.tokenTimeout(),
            command.activeTimeout(), parseStatus(command.status())), currentActor());
        return new AppAuthClientSecretVo(toAuthClientVo(requireAuthClient(secret.id())), secret.clientSecret());
    }

    @Override
    public void updateAuthClient(String clientId, UpdateAppAuthClientBo command) {
        authClientService().update(new UpdateAppAuthClientDTO(parseId(clientId, "创作端认证客户端编号"), command.clientKey(),
            command.grantTypes(), command.accessPaths(), command.ipWhitelist(), command.tokenTimeout(),
            command.activeTimeout(), parseStatus(command.status()), command.expectedClientRevision()), currentActor());
    }

    @Override
    public AppAuthClientSecretVo rotateAuthClientSecret(String clientId, RotateAppAuthClientSecretBo command) {
        AppAuthClientSecretDTO secret = authClientService().rotateSecret(new RotateAppAuthClientSecretDTO(
            parseId(clientId, "创作端认证客户端编号"), command.expectedClientRevision()), currentActor());
        return new AppAuthClientSecretVo(toAuthClientVo(requireAuthClient(secret.id())), secret.clientSecret());
    }

    @Override
    public PageResult<AppSessionAdminVo> pageSessions(AppSessionQueryBo query, PageQuery pageQuery) {
        AppSessionQueryBo effectiveQuery = query == null ? new AppSessionQueryBo() : query;
        AppSessionQueryDTO sessionQuery = new AppSessionQueryDTO();
        sessionQuery.setAppUserId(notBlank(effectiveQuery.getAppUserId())
            ? parseId(effectiveQuery.getAppUserId(), "创作端用户编号") : null);
        sessionQuery.setClientId(trim(effectiveQuery.getClientId()));
        copyPage(pageQuery, sessionQuery);
        PageResult<AppSessionSummaryDTO> result = sessionService().page(sessionQuery);
        return PageResult.build(result.getRows().stream().map(this::toSessionVo).toList(), result.getTotal());
    }

    @Override
    public void kickoutSession(String sessionId, KickoutAppSessionBo command) {
        String auditReason = requireAdminKickoutReason(command.reasonCode());
        AppSessionSummaryDTO session = findSession(sessionId);
        if (session.appUserId() == null || session.appUserId() <= 0) {
            throw new ServiceException("创作端会话归属信息不可用");
        }
        sessionService().revokeSession(session.appUserId(), session.sessionId(), currentActor(), auditReason);
    }

    @Override
    public PageResult<AppLoginLogAdminVo> pageLoginLogs(AppLoginLogQueryBo query, PageQuery pageQuery) {
        AppLoginLogQueryBo effectiveQuery = query == null ? new AppLoginLogQueryBo() : query;
        validateTimeRange(effectiveQuery.getOccurredAfter(), effectiveQuery.getOccurredBefore());
        Page<AppLoginLog> page = loginLogMapper.selectPage(page(pageQuery), new LambdaQueryWrapper<AppLoginLog>()
            .eq(notBlank(effectiveQuery.getAppUserId()), AppLoginLog::getUserId,
                notBlank(effectiveQuery.getAppUserId()) ? parseId(effectiveQuery.getAppUserId(), "创作端用户编号") : null)
            .eq(notBlank(effectiveQuery.getClientId()), AppLoginLog::getClientId, trim(effectiveQuery.getClientId()))
            .eq(effectiveQuery.getResultCode() != null, AppLoginLog::getResultCode, effectiveQuery.getResultCode())
            .ge(effectiveQuery.getOccurredAfter() != null, AppLoginLog::getOccurredAt, effectiveQuery.getOccurredAfter())
            .le(effectiveQuery.getOccurredBefore() != null, AppLoginLog::getOccurredAt, effectiveQuery.getOccurredBefore())
            .orderByDesc(AppLoginLog::getOccurredAt));
        return PageResult.build(page.getRecords().stream().map(this::toLoginLogVo).toList(), page.getTotal());
    }

    @Override
    public PageResult<AppSecurityAuditAdminVo> pageSecurityAudits(AppSecurityAuditQueryBo query, PageQuery pageQuery) {
        AppSecurityAuditQueryBo effectiveQuery = query == null ? new AppSecurityAuditQueryBo() : query;
        validateTimeRange(effectiveQuery.getOccurredAfter(), effectiveQuery.getOccurredBefore());
        AppActorType actorType = parseOptionalActorType(effectiveQuery.getActorType());
        Page<AppSecurityAudit> page = securityAuditMapper.selectPage(page(pageQuery), new LambdaQueryWrapper<AppSecurityAudit>()
            .eq(notBlank(effectiveQuery.getResourceType()), AppSecurityAudit::getResourceType,
                trim(effectiveQuery.getResourceType()))
            .eq(notBlank(effectiveQuery.getResourceId()), AppSecurityAudit::getResourceId, trim(effectiveQuery.getResourceId()))
            .eq(notBlank(effectiveQuery.getAction()), AppSecurityAudit::getAction, trim(effectiveQuery.getAction()))
            .eq(actorType != null, AppSecurityAudit::getActorType, actorType)
            .eq(notBlank(effectiveQuery.getActorId()), AppSecurityAudit::getActorId,
                notBlank(effectiveQuery.getActorId()) ? parseId(effectiveQuery.getActorId(), "运营主体编号") : null)
            .ge(effectiveQuery.getOccurredAfter() != null, AppSecurityAudit::getOccurredAt,
                effectiveQuery.getOccurredAfter())
            .le(effectiveQuery.getOccurredBefore() != null, AppSecurityAudit::getOccurredAt,
                effectiveQuery.getOccurredBefore())
            .orderByDesc(AppSecurityAudit::getOccurredAt));
        return PageResult.build(page.getRecords().stream().map(this::toSecurityAuditVo).toList(), page.getTotal());
    }

    private IAppIdentityService identityService() {
        return identityServiceProvider.getObject();
    }

    private IAppPermissionService permissionService() {
        return permissionServiceProvider.getObject();
    }

    private IAppAuthClientService authClientService() {
        return authClientServiceProvider.getObject();
    }

    private IAppSessionService sessionService() {
        return sessionServiceProvider.getObject();
    }

    private AppActorContext currentActor() {
        Long sysUserId = LoginHelper.getUserId();
        if (sysUserId == null || sysUserId <= 0) {
            throw new ServiceException("运营端登录主体不存在");
        }
        return AppActorContext.sysUser(sysUserId);
    }

    private String permissionFor(AppIdentityOperation operation) {
        if (operation == null) {
            return null;
        }
        return switch (operation) {
            case REGISTER_APP_USER -> "aivideo:app-user:add";
            case UPDATE_APP_USER, CHANGE_STATUS -> "aivideo:app-user:edit";
            case RESET_PASSWORD -> "aivideo:app-user:reset-password";
            case REPLACE_USER_ROLES -> "aivideo:app-user:assign-role";
            case CREATE_APP_ROLE, UPDATE_APP_ROLE -> "aivideo:app-role:edit";
            case REPLACE_ROLE_PERMISSIONS -> "aivideo:app-role:assign-permission";
            case CREATE_APP_AUTH_CLIENT, UPDATE_APP_AUTH_CLIENT -> "aivideo:app-auth-client:edit";
            case ROTATE_APP_AUTH_CLIENT_SECRET -> "aivideo:app-auth-client:rotate-secret";
            case REVOKE_APP_SESSION -> "aivideo:app-session:kickout";
            default -> null;
        };
    }

    private AppUser requireUser(long appUserId) {
        AppUser user = userMapper.selectOne(new LambdaQueryWrapper<AppUser>()
            .eq(AppUser::getUserId, appUserId)
            .eq(AppUser::getDelFlag, "0"));
        if (user == null) {
            throw new ServiceException("创作端用户不存在");
        }
        return user;
    }

    private AppAuthClient requireAuthClient(long id) {
        AppAuthClient client = authClientMapper.selectOne(new LambdaQueryWrapper<AppAuthClient>()
            .eq(AppAuthClient::getId, id)
            .eq(AppAuthClient::getDelFlag, "0"));
        if (client == null) {
            throw new ServiceException("创作端认证客户端不存在");
        }
        return client;
    }

    private List<AppRoleAdminVo> rolesForUser(long appUserId) {
        List<AppUserRole> relations = userRoleMapper.selectList(new LambdaQueryWrapper<AppUserRole>()
            .eq(AppUserRole::getUserId, appUserId)
            .orderByAsc(AppUserRole::getRoleId));
        if (relations.isEmpty()) {
            return List.of();
        }
        Set<Long> roleIds = relations.stream().map(AppUserRole::getRoleId).filter(Objects::nonNull)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (roleIds.isEmpty()) {
            return List.of();
        }
        Map<Long, AppRole> roles = roleMapper.selectList(new LambdaQueryWrapper<AppRole>()
                .in(AppRole::getRoleId, roleIds)
                .eq(AppRole::getDelFlag, "0"))
            .stream()
            .collect(java.util.stream.Collectors.toMap(AppRole::getRoleId, role -> role,
                (first, ignored) -> first, LinkedHashMap::new));
        return roleIds.stream().map(roles::get).filter(Objects::nonNull).map(this::toRoleVo).toList();
    }

    private List<AppSessionAdminVo> sessionsForUser(long appUserId) {
        AppSessionQueryDTO query = new AppSessionQueryDTO();
        query.setAppUserId(appUserId);
        return sessionService().page(query).getRows().stream().map(this::toSessionVo).toList();
    }

    private AppSessionSummaryDTO findSession(String sessionId) {
        if (sessionId == null || !sessionId.matches("[A-Za-z0-9-]{1,64}")) {
            throw new ServiceException("创作端会话编号格式不安全");
        }
        return sessionService().findBySessionId(sessionId)
            .orElseThrow(() -> new ServiceException("创作端会话不存在或已失效"));
    }

    private AppUserAdminVo toUserVo(AppUser user) {
        return new AppUserAdminVo(
            id(user.getUserId()),
            user.getUsername(),
            user.getDisplayName(),
            maskPhone(user.getPhoneNormalized()),
            maskEmail(user.getEmailNormalized()),
            statusValue(user.getStatus()),
            Boolean.TRUE.equals(user.getMustChangePassword()),
            id(user.getCredentialRevision()),
            id(user.getIdentityRevision()),
            id(user.getPermissionRevision()),
            user.getCreateTime(),
            user.getUpdateTime());
    }

    private AppRoleAdminVo toRoleVo(AppRole role) {
        List<String> permissionIds = rolePermissionMapper.selectList(new LambdaQueryWrapper<AppRolePermission>()
                .eq(AppRolePermission::getRoleId, role.getRoleId())
                .eq(AppRolePermission::getStatus, AppIdentityStatus.ACTIVE)
                .orderByAsc(AppRolePermission::getPermissionId))
            .stream()
            .map(AppRolePermission::getPermissionId)
            .filter(Objects::nonNull)
            .map(this::id)
            .toList();
        Long userReferenceCount = userRoleMapper.selectCount(new LambdaQueryWrapper<AppUserRole>()
            .eq(AppUserRole::getRoleId, role.getRoleId())
            .eq(AppUserRole::getStatus, AppIdentityStatus.ACTIVE));
        return new AppRoleAdminVo(
            id(role.getRoleId()),
            role.getRoleCode(),
            role.getRoleName(),
            role.getScopeType(),
            Boolean.TRUE.equals(role.getBuiltIn()),
            statusValue(role.getStatus()),
            id(role.getRoleRevision()),
            permissionIds,
            userReferenceCount == null ? 0L : userReferenceCount,
            role.getCreateTime(),
            role.getUpdateTime());
    }

    private AppRoleAdminVo toRoleVo(AppRoleDTO role) {
        List<String> permissionIds = rolePermissionMapper.selectList(new LambdaQueryWrapper<AppRolePermission>()
                .eq(AppRolePermission::getRoleId, role.roleId())
                .eq(AppRolePermission::getStatus, AppIdentityStatus.ACTIVE)
                .orderByAsc(AppRolePermission::getPermissionId))
            .stream()
            .map(AppRolePermission::getPermissionId)
            .filter(Objects::nonNull)
            .map(this::id)
            .toList();
        Long userReferenceCount = userRoleMapper.selectCount(new LambdaQueryWrapper<AppUserRole>()
            .eq(AppUserRole::getRoleId, role.roleId())
            .eq(AppUserRole::getStatus, AppIdentityStatus.ACTIVE));
        return new AppRoleAdminVo(
            id(role.roleId()),
            role.roleCode(),
            role.roleName(),
            role.scopeType(),
            Boolean.TRUE.equals(role.builtIn()),
            statusValue(role.status()),
            id(role.roleRevision()),
            permissionIds,
            userReferenceCount == null ? 0L : userReferenceCount,
            role.createTime(),
            role.updateTime());
    }

    private AppPermissionAdminVo toPermissionVo(AppPermission permission) {
        return new AppPermissionAdminVo(
            id(permission.getPermissionId()),
            permission.getPermissionCode(),
            permission.getPermissionName(),
            permission.getResourceType(),
            permission.getAction(),
            statusValue(permission.getStatus()),
            id(permission.getPermissionRevision()));
    }

    private AppAuthClientAdminVo toAuthClientVo(AppAuthClient client) {
        AppSessionQueryDTO query = new AppSessionQueryDTO();
        query.setClientId(client.getClientId());
        long activeSessionCount = sessionService().page(query).getTotal();
        return new AppAuthClientAdminVo(
            id(client.getId()),
            client.getClientId(),
            client.getClientKey(),
            client.getGrantTypes(),
            client.getAccessPaths(),
            client.getIpWhitelist(),
            client.getTokenTimeout(),
            client.getActiveTimeout(),
            statusValue(client.getStatus()),
            id(client.getClientRevision()),
            client.getClientSecretHash() != null && !client.getClientSecretHash().isBlank(),
            activeSessionCount,
            client.getCreateTime(),
            client.getUpdateTime());
    }

    private AppSessionAdminVo toSessionVo(AppSessionSummaryDTO session) {
        return new AppSessionAdminVo(
            session.sessionId(),
            id(session.appUserId()),
            session.clientId(),
            session.device(),
            session.lastActiveTime());
    }

    private AppLoginLogAdminVo toLoginLogVo(AppLoginLog log) {
        return new AppLoginLogAdminVo(
            id(log.getLoginLogId()),
            log.getAuthMethod() == null ? null : log.getAuthMethod().getValue(),
            log.getMaskedIdentifier(),
            log.getClientId(),
            log.getResultCode(),
            log.getFailureCategory(),
            id(log.getUserId()),
            log.getSessionId(),
            log.getIpAddress(),
            log.getDeviceSummary(),
            log.getRequestId(),
            log.getOccurredAt());
    }

    private AppSecurityAuditAdminVo toSecurityAuditVo(AppSecurityAudit audit) {
        return new AppSecurityAuditAdminVo(
            id(audit.getAuditId()),
            audit.getResourceType(),
            audit.getResourceId(),
            audit.getAction(),
            audit.getActorType() == null ? null : audit.getActorType().getValue(),
            id(audit.getActorId()),
            audit.getBeforeDigest(),
            audit.getAfterDigest(),
            audit.getReason(),
            audit.getRequestId(),
            audit.getIpAddress(),
            audit.getOccurredAt());
    }

    private <T> Page<T> page(PageQuery query) {
        return (query == null ? new PageQuery() : query).build();
    }

    private void copyPage(PageQuery source, AppSessionQueryDTO target) {
        PageQuery pageQuery = source == null ? new PageQuery() : source;
        target.setPageNum(pageQuery.getPageNum());
        target.setPageSize(pageQuery.getPageSize());
        target.setOrderByColumn(pageQuery.getOrderByColumn());
        target.setIsAsc(pageQuery.getIsAsc());
    }

    private long parseId(String value, String fieldName) {
        if (value == null || !value.matches("[1-9]\\d{0,18}")) {
            throw new ServiceException(fieldName + "不合法");
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new ServiceException(fieldName + "不合法", exception);
        }
    }

    private Set<Long> parseIds(Set<String> values, String fieldName) {
        if (values == null) {
            throw new ServiceException(fieldName + "集合不能为空");
        }
        Set<Long> parsed = new LinkedHashSet<>();
        for (String value : values) {
            parsed.add(parseId(value, fieldName));
        }
        return parsed;
    }

    private AppIdentityStatus parseStatus(String value) {
        if (value == null) {
            throw new ServiceException("创作端状态不能为空");
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "active" -> AppIdentityStatus.ACTIVE;
            case "inactive" -> AppIdentityStatus.INACTIVE;
            case "disabled" -> AppIdentityStatus.DISABLED;
            default -> throw new ServiceException("创作端状态不合法");
        };
    }

    private AppIdentityStatus parseOptionalStatus(String value) {
        return notBlank(value) ? parseStatus(value) : null;
    }

    private AppActorType parseOptionalActorType(String value) {
        if (!notBlank(value)) {
            return null;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "app_user" -> AppActorType.APP_USER;
            case "sys_user" -> AppActorType.SYS_USER;
            default -> throw new ServiceException("审计操作者类型不合法");
        };
    }

    private void validateTimeRange(LocalDateTime after, LocalDateTime before) {
        if (after != null && before != null && after.isAfter(before)) {
            throw new ServiceException("时间筛选范围不合法");
        }
    }

    private String requireAdminKickoutReason(String value) {
        if (value == null || !value.matches("[a-z][a-z0-9_]{0,63}")) {
            throw new ServiceException("强制下线原因编码不合法");
        }
        String normalized = value.trim();
        if (!AppSecurityAuditReason.isAdminKickoutCode(normalized)) {
            throw new ServiceException("强制下线原因编码不受支持");
        }
        return normalized;
    }

    private String generatePassword() {
        List<Character> characters = new ArrayList<>();
        characters.add('A');
        characters.add('a');
        characters.add('2');
        for (int index = characters.size(); index < 20; index++) {
            characters.add(PASSWORD_ALPHABET[secureRandom.nextInt(PASSWORD_ALPHABET.length)]);
        }
        for (int index = characters.size() - 1; index > 0; index--) {
            int replacement = secureRandom.nextInt(index + 1);
            Character current = characters.get(index);
            characters.set(index, characters.get(replacement));
            characters.set(replacement, current);
        }
        StringBuilder password = new StringBuilder(characters.size());
        characters.forEach(password::append);
        return password.toString();
    }

    private String maskPhone(String phone) {
        if (!notBlank(phone)) {
            return null;
        }
        String normalized = phone.trim();
        if (normalized.length() <= 4) {
            return "***";
        }
        return normalized.substring(0, Math.min(3, normalized.length() - 2)) + "****"
            + normalized.substring(normalized.length() - 2);
    }

    private String maskEmail(String email) {
        if (!notBlank(email)) {
            return null;
        }
        String normalized = email.trim();
        int separator = normalized.indexOf('@');
        if (separator <= 0) {
            return "***";
        }
        return normalized.substring(0, 1) + "***" + normalized.substring(separator);
    }

    private String statusValue(AppIdentityStatus status) {
        return status == null ? null : status.getValue();
    }

    private String id(Long value) {
        return value == null ? null : Long.toString(value);
    }

    private String id(long value) {
        return Long.toString(value);
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private String trim(String value) {
        return notBlank(value) ? value.trim() : null;
    }
}
