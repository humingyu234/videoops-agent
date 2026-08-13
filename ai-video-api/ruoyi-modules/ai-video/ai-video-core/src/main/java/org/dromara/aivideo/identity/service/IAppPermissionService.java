package org.dromara.aivideo.identity.service;

import org.dromara.aivideo.identity.dto.CreateAppRoleDTO;
import org.dromara.aivideo.identity.dto.AppRoleDTO;
import org.dromara.aivideo.identity.dto.UpdateAppRoleDTO;
import org.dromara.aivideo.identity.security.AppActorContext;

import java.util.Set;

/**
 * 解析和维护创作端角色权限的唯一应用服务。
 */
public interface IAppPermissionService {

    /**
     * 查询创作端用户当前生效的角色编码。
     *
     * @param userId 创作端用户编号
     * @return 当前生效的角色编码集合；没有映射时返回空集合
     */
    Set<String> roleCodes(long userId);

    /**
     * 查询创作端用户当前生效的权限编码。
     *
     * @param userId 创作端用户编号
     * @return 当前生效的权限编码集合；没有映射时返回空集合
     */
    Set<String> permissionCodes(long userId);

    /**
     * 创建非内置创作端角色。
     *
     * @param command 新角色命令
     * @param actor 已认证的运营端操作者
     * @return 已创建角色
     */
    default AppRoleDTO createRole(CreateAppRoleDTO command, AppActorContext actor) {
        throw new UnsupportedOperationException("当前权限服务未实现创作端角色管理");
    }

    /**
     * 更新创作端角色元数据和状态。
     *
     * @param command 角色更新命令
     * @param actor 已认证的运营端操作者
     */
    default void updateRole(UpdateAppRoleDTO command, AppActorContext actor) {
        throw new UnsupportedOperationException("当前权限服务未实现创作端角色管理");
    }

    /**
     * 替换创作端用户的个人作用域角色集合。
     *
     * @param userId 创作端用户编号
     * @param expectedPermissionRevision 预期权限修订号
     * @param roleIds 新的个人作用域角色编号集合
     * @param actor 已认证的运营端操作者
     */
    void replaceUserRoles(long userId, long expectedPermissionRevision, Set<Long> roleIds,
                          AppActorContext actor);

    /**
     * 替换创作端角色的权限集合。
     *
     * @param roleId 创作端角色编号
     * @param expectedRoleRevision 预期角色修订号
     * @param permissionIds 新的权限编号集合
     * @param actor 已认证的运营端操作者
     */
    void replaceRolePermissions(long roleId, long expectedRoleRevision, Set<Long> permissionIds,
                                AppActorContext actor);
}
