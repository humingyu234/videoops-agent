package org.dromara.aivideo.identity.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.dromara.aivideo.identity.service.IAppPermissionService;
import org.dromara.aivideo.identity.service.IAppSecurityAuditService;
import org.dromara.aivideo.identity.dto.AppRoleDTO;
import org.dromara.aivideo.identity.dto.CreateAppRoleDTO;
import org.dromara.aivideo.identity.dto.UpdateAppRoleDTO;
import org.dromara.aivideo.identity.event.AppSessionInvalidationEvent;
import org.dromara.aivideo.identity.domain.AppActorType;
import org.dromara.aivideo.identity.domain.AppIdentityStatus;
import org.dromara.aivideo.identity.domain.AppPermission;
import org.dromara.aivideo.identity.domain.AppRole;
import org.dromara.aivideo.identity.domain.AppRolePermission;
import org.dromara.aivideo.identity.domain.AppSecurityAuditReason;
import org.dromara.aivideo.identity.domain.AppUser;
import org.dromara.aivideo.identity.domain.AppUserRole;
import org.dromara.aivideo.identity.mapper.AppPermissionMapper;
import org.dromara.aivideo.identity.mapper.AppRoleMapper;
import org.dromara.aivideo.identity.mapper.AppRolePermissionMapper;
import org.dromara.aivideo.identity.mapper.AppUserMapper;
import org.dromara.aivideo.identity.mapper.AppUserRoleMapper;
import org.dromara.aivideo.identity.dto.AppSecurityAuditDTO;
import org.dromara.aivideo.identity.domain.AppSessionInvalidationReason;
import org.dromara.aivideo.identity.security.AppActorContext;
import org.dromara.aivideo.identity.security.AppIdentityOperation;
import org.dromara.aivideo.identity.security.AppIdentityOperationAuthorizer;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 创作端角色权限解析和修订失效的应用服务实现。
 */
@Service
public class AppPermissionServiceImpl implements IAppPermissionService {

    private static final int APP_ROLE_REVISION_CONFLICT_CODE = 46134;
    private static final String PERSONAL_SCOPE = "personal";
    private static final String APP_USER_RESOURCE = "app_user";
    private static final String APP_ROLE_RESOURCE = "app_role";

    private final AppUserMapper userMapper;
    private final AppRoleMapper roleMapper;
    private final AppPermissionMapper permissionMapper;
    private final AppUserRoleMapper userRoleMapper;
    private final AppRolePermissionMapper rolePermissionMapper;
    private final IAppSecurityAuditService securityAuditService;
    private final AppIdentityOperationAuthorizer operationAuthorizer;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 创建角色权限应用服务。
     *
     * @param userMapper 创作端用户数据访问器
     * @param roleMapper 创作端角色数据访问器
     * @param permissionMapper 创作端权限数据访问器
     * @param userRoleMapper 创作端用户角色关联数据访问器
     * @param rolePermissionMapper 创作端角色权限关联数据访问器
     * @param securityAuditService 创作端安全审计服务
     * @param operationAuthorizer 运营端操作授权器
     * @param eventPublisher 领域事件发布器
     */
    public AppPermissionServiceImpl(AppUserMapper userMapper,
                                    AppRoleMapper roleMapper,
                                    AppPermissionMapper permissionMapper,
                                    AppUserRoleMapper userRoleMapper,
                                    AppRolePermissionMapper rolePermissionMapper,
                                    IAppSecurityAuditService securityAuditService,
                                    AppIdentityOperationAuthorizer operationAuthorizer,
                                    ApplicationEventPublisher eventPublisher) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.permissionMapper = permissionMapper;
        this.userRoleMapper = userRoleMapper;
        this.rolePermissionMapper = rolePermissionMapper;
        this.securityAuditService = securityAuditService;
        this.operationAuthorizer = operationAuthorizer;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public Set<String> roleCodes(long userId) {
        if (userId <= 0) {
            return Set.of();
        }
        return immutableCodes(roleMapper.selectEffectiveRoleCodesByUserId(userId, LocalDateTime.now()));
    }

    @Override
    public Set<String> permissionCodes(long userId) {
        if (userId <= 0) {
            return Set.of();
        }
        return immutableCodes(permissionMapper.selectEffectivePermissionCodesByUserId(userId, LocalDateTime.now()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AppRoleDTO createRole(CreateAppRoleDTO command, AppActorContext actor) {
        requirePlatformOperation(actor, AppIdentityOperation.CREATE_APP_ROLE, 0L);
        if (command == null || command.status() == null) {
            throw new ServiceException("创作端角色参数不能为空");
        }
        String roleCode = normalizeRoleCode(command.roleCode());
        String roleName = trimRequired(command.roleName(), "角色名称");
        String scopeType = normalizeScopeType(command.scopeType());
        AppRole existing = roleMapper.selectOne(new LambdaQueryWrapper<AppRole>()
            .eq(AppRole::getRoleCode, roleCode)
            .eq(AppRole::getDelFlag, "0"));
        if (existing != null) {
            throw new ServiceException("创作端角色编码已存在");
        }

        AppRole role = new AppRole();
        role.setRoleCode(roleCode);
        role.setRoleName(roleName);
        role.setScopeType(scopeType);
        role.setBuiltIn(false);
        role.setRoleRevision(1L);
        role.setStatus(command.status());
        applyCreateActor(role, actor);
        if (roleMapper.insert(role) != 1 || role.getRoleId() == null) {
            throw new ServiceException("创作端角色创建失败");
        }
        securityAuditService.append(new AppSecurityAuditDTO(
            APP_ROLE_RESOURCE,
            Long.toString(role.getRoleId()),
            "role_created",
            actor.actorType(),
            actor.actorId(),
            null,
            "role_revision:1",
            AppSecurityAuditReason.ROLE_CREATION.code()));
        return toRoleDTO(role);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateRole(UpdateAppRoleDTO command, AppActorContext actor) {
        if (command == null || command.status() == null || command.expectedRoleRevision() <= 0) {
            throw revisionConflict();
        }
        requirePlatformOperation(actor, AppIdentityOperation.UPDATE_APP_ROLE, command.roleId());
        AppRole current = requireRole(command.roleId());
        String roleName = trimRequired(command.roleName(), "角色名称");
        int affectedRows = roleMapper.update(null, new LambdaUpdateWrapper<AppRole>()
            .eq(AppRole::getRoleId, command.roleId())
            .eq(AppRole::getDelFlag, "0")
            .eq(AppRole::getRoleRevision, command.expectedRoleRevision())
            .set(AppRole::getRoleName, roleName)
            .set(AppRole::getStatus, command.status())
            .set(AppRole::getUpdatedByType, actor.actorType())
            .set(AppRole::getUpdatedById, actor.actorId())
            .setSql("role_revision = role_revision + 1"));
        assertRevisionUpdated(affectedRows);
        securityAuditService.append(new AppSecurityAuditDTO(
            APP_ROLE_RESOURCE,
            Long.toString(command.roleId()),
            "role_updated",
            actor.actorType(),
            actor.actorId(),
            "role_revision:" + command.expectedRoleRevision(),
            "role_revision:" + (command.expectedRoleRevision() + 1),
            AppSecurityAuditReason.ROLE_METADATA_CHANGE.code()));

        if (current.getStatus() != command.status()) {
            Set<Long> affectedUserIds = new LinkedHashSet<>(
                userRoleMapper.selectCurrentEffectiveActiveUserIdsByRoleId(command.roleId(), LocalDateTime.now()));
            incrementAffectedUserPermissionRevisions(affectedUserIds, actor);
            if (!affectedUserIds.isEmpty()) {
                eventPublisher.publishEvent(AppSessionInvalidationEvent.forUsers(
                    affectedUserIds, AppSessionInvalidationReason.PERMISSION_CHANGED));
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void replaceUserRoles(long userId, long expectedPermissionRevision, Set<Long> roleIds,
                                 AppActorContext actor) {
        requirePlatformOperation(actor, AppIdentityOperation.REPLACE_USER_ROLES, userId);
        assertExpectedRevision(expectedPermissionRevision);
        Set<Long> requestedRoleIds = normalizeIds(roleIds, "角色编号集合");
        assertAssignablePersonalRoles(requestedRoleIds);

        int affectedRows = userMapper.update(null, new LambdaUpdateWrapper<AppUser>()
            .eq(AppUser::getUserId, userId)
            .eq(AppUser::getDelFlag, "0")
            .eq(AppUser::getPermissionRevision, expectedPermissionRevision)
            .set(AppUser::getUpdatedByType, actor.actorType())
            .set(AppUser::getUpdatedById, actor.actorId())
            .setSql("permission_revision = permission_revision + 1"));
        assertRevisionUpdated(affectedRows);

        userRoleMapper.delete(new LambdaQueryWrapper<AppUserRole>()
            .eq(AppUserRole::getUserId, userId));
        for (Long roleId : requestedRoleIds) {
            AppUserRole userRole = new AppUserRole();
            userRole.setUserId(userId);
            userRole.setRoleId(roleId);
            userRole.setStatus(AppIdentityStatus.ACTIVE);
            applyCreateActor(userRole, actor);
            if (userRoleMapper.insert(userRole) != 1) {
                throw new ServiceException("创作端用户角色替换失败");
            }
        }

        securityAuditService.append(new AppSecurityAuditDTO(
            APP_USER_RESOURCE,
            Long.toString(userId),
            "roles_replaced",
            actor.actorType(),
            actor.actorId(),
            "permission_revision:" + expectedPermissionRevision,
            "permission_revision:" + (expectedPermissionRevision + 1),
            AppSecurityAuditReason.USER_ROLE_REPLACEMENT.code()));
        eventPublisher.publishEvent(AppSessionInvalidationEvent.forUsers(
            Set.of(userId), AppSessionInvalidationReason.PERMISSION_CHANGED));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void replaceRolePermissions(long roleId, long expectedRoleRevision, Set<Long> permissionIds,
                                       AppActorContext actor) {
        requirePlatformOperation(actor, AppIdentityOperation.REPLACE_ROLE_PERMISSIONS, roleId);
        assertExpectedRevision(expectedRoleRevision);
        Set<Long> requestedPermissionIds = normalizeIds(permissionIds, "权限编号集合");
        assertAssignablePermissions(requestedPermissionIds);

        int affectedRows = roleMapper.update(null, new LambdaUpdateWrapper<AppRole>()
            .eq(AppRole::getRoleId, roleId)
            .eq(AppRole::getDelFlag, "0")
            .eq(AppRole::getRoleRevision, expectedRoleRevision)
            .set(AppRole::getUpdatedByType, actor.actorType())
            .set(AppRole::getUpdatedById, actor.actorId())
            .setSql("role_revision = role_revision + 1"));
        assertRevisionUpdated(affectedRows);

        rolePermissionMapper.delete(new LambdaQueryWrapper<AppRolePermission>()
            .eq(AppRolePermission::getRoleId, roleId));
        for (Long permissionId : requestedPermissionIds) {
            AppRolePermission rolePermission = new AppRolePermission();
            rolePermission.setRoleId(roleId);
            rolePermission.setPermissionId(permissionId);
            rolePermission.setStatus(AppIdentityStatus.ACTIVE);
            applyCreateActor(rolePermission, actor);
            if (rolePermissionMapper.insert(rolePermission) != 1) {
                throw new ServiceException("创作端角色权限替换失败");
            }
        }

        Set<Long> affectedUserIds = new LinkedHashSet<>(
            userRoleMapper.selectCurrentEffectiveActiveUserIdsByRoleId(roleId, LocalDateTime.now()));
        incrementAffectedUserPermissionRevisions(affectedUserIds, actor);
        securityAuditService.append(new AppSecurityAuditDTO(
            APP_ROLE_RESOURCE,
            Long.toString(roleId),
            "permissions_replaced",
            actor.actorType(),
            actor.actorId(),
            "role_revision:" + expectedRoleRevision,
            "role_revision:" + (expectedRoleRevision + 1),
            AppSecurityAuditReason.ROLE_PERMISSION_REPLACEMENT.code()));
        if (!affectedUserIds.isEmpty()) {
            eventPublisher.publishEvent(AppSessionInvalidationEvent.forUsers(
                affectedUserIds, AppSessionInvalidationReason.PERMISSION_CHANGED));
        }
    }

    private void incrementAffectedUserPermissionRevisions(Set<Long> userIds, AppActorContext actor) {
        if (userIds.isEmpty()) {
            return;
        }
        int affectedRows = userMapper.update(null, new LambdaUpdateWrapper<AppUser>()
            .in(AppUser::getUserId, userIds)
            .eq(AppUser::getStatus, AppIdentityStatus.ACTIVE)
            .eq(AppUser::getDelFlag, "0")
            .set(AppUser::getUpdatedByType, actor.actorType())
            .set(AppUser::getUpdatedById, actor.actorId())
            .setSql("permission_revision = permission_revision + 1"));
        if (affectedRows != userIds.size()) {
            throw new ServiceException("创作端用户权限修订更新冲突");
        }
    }

    private void assertAssignablePersonalRoles(Set<Long> roleIds) {
        if (roleIds.isEmpty()) {
            return;
        }
        List<AppRole> roles = roleMapper.selectList(new LambdaQueryWrapper<AppRole>()
            .in(AppRole::getRoleId, roleIds)
            .eq(AppRole::getScopeType, PERSONAL_SCOPE)
            .eq(AppRole::getStatus, AppIdentityStatus.ACTIVE)
            .eq(AppRole::getDelFlag, "0"));
        if (roles.size() != roleIds.size()) {
            throw new ServiceException("创作端个人角色不存在或不可用");
        }
    }

    private AppRole requireRole(long roleId) {
        if (roleId <= 0) {
            throw new ServiceException("创作端角色编号无效");
        }
        AppRole role = roleMapper.selectOne(new LambdaQueryWrapper<AppRole>()
            .eq(AppRole::getRoleId, roleId)
            .eq(AppRole::getDelFlag, "0"));
        if (role == null) {
            throw new ServiceException("创作端角色不存在");
        }
        return role;
    }

    private AppRoleDTO toRoleDTO(AppRole role) {
        return new AppRoleDTO(
            role.getRoleId(),
            role.getRoleCode(),
            role.getRoleName(),
            role.getScopeType(),
            role.getBuiltIn(),
            role.getStatus(),
            role.getRoleRevision(),
            role.getCreateTime(),
            role.getUpdateTime());
    }

    private String normalizeRoleCode(String roleCode) {
        String normalized = trimRequired(roleCode, "角色编码").toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z][a-z0-9_]{0,63}")) {
            throw new ServiceException("创作端角色编码格式不合法");
        }
        return normalized;
    }

    private String normalizeScopeType(String scopeType) {
        String normalized = trimRequired(scopeType, "角色作用域").toLowerCase(Locale.ROOT);
        if (!PERSONAL_SCOPE.equals(normalized) && !"organization".equals(normalized)) {
            throw new ServiceException("创作端角色作用域不合法");
        }
        return normalized;
    }

    private String trimRequired(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new ServiceException(fieldName + "不能为空");
        }
        return value.trim();
    }

    private void assertAssignablePermissions(Set<Long> permissionIds) {
        if (permissionIds.isEmpty()) {
            return;
        }
        List<AppPermission> permissions = permissionMapper.selectList(new LambdaQueryWrapper<AppPermission>()
            .in(AppPermission::getPermissionId, permissionIds)
            .eq(AppPermission::getStatus, AppIdentityStatus.ACTIVE));
        if (permissions.size() != permissionIds.size()) {
            throw new ServiceException("创作端权限不存在或不可用");
        }
    }

    private Set<Long> normalizeIds(Set<Long> ids, String fieldName) {
        if (ids == null || ids.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new ServiceException(fieldName + "不合法");
        }
        return new LinkedHashSet<>(ids);
    }

    private Set<String> immutableCodes(List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return Set.of();
        }
        LinkedHashSet<String> normalizedCodes = new LinkedHashSet<>();
        for (String code : codes) {
            if (code != null && !code.isBlank()) {
                normalizedCodes.add(code);
            }
        }
        return normalizedCodes.isEmpty() ? Set.of() : Collections.unmodifiableSet(normalizedCodes);
    }

    private void requirePlatformOperation(AppActorContext actor, AppIdentityOperation operation,
                                          long targetResourceId) {
        if (actor == null
            || actor.actorType() != AppActorType.SYS_USER
            || actor.actorId() <= 0
            || !operationAuthorizer.isAuthorized(actor, operation, targetResourceId)) {
            throw new ServiceException("运营端身份操作未获授权");
        }
    }

    private void assertExpectedRevision(long expectedRevision) {
        if (expectedRevision <= 0) {
            throw revisionConflict();
        }
    }

    private void assertRevisionUpdated(int affectedRows) {
        if (affectedRows != 1) {
            throw revisionConflict();
        }
    }

    private ServiceException revisionConflict() {
        return new ServiceException("创作端角色或权限修订已变化", APP_ROLE_REVISION_CONFLICT_CODE);
    }

    private void applyCreateActor(AppUserRole userRole, AppActorContext actor) {
        userRole.setCreatedByType(actor.actorType());
        userRole.setCreatedById(actor.actorId());
        userRole.setUpdatedByType(actor.actorType());
        userRole.setUpdatedById(actor.actorId());
    }

    private void applyCreateActor(AppRole role, AppActorContext actor) {
        role.setCreatedByType(actor.actorType());
        role.setCreatedById(actor.actorId());
        role.setUpdatedByType(actor.actorType());
        role.setUpdatedById(actor.actorId());
    }

    private void applyCreateActor(AppRolePermission rolePermission, AppActorContext actor) {
        rolePermission.setCreatedByType(actor.actorType());
        rolePermission.setCreatedById(actor.actorId());
        rolePermission.setUpdatedByType(actor.actorType());
        rolePermission.setUpdatedById(actor.actorId());
    }
}
