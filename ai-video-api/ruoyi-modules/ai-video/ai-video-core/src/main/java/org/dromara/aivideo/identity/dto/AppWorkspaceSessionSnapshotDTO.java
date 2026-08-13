package org.dromara.aivideo.identity.dto;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import java.util.Set;

/**
 * 创作端当前工作区的会话快照。
 *
 * @param workspaceKey 不可逆的工作区稳定键
 * @param workspaceType 工作区类型
 * @param tenantId 工作区所属租户编号
 * @param ownerType 工作区所有者类型
 * @param ownerId 工作区所有者编号
 * @param billingSubjectType 计费主体类型
 * @param billingSubjectId 计费主体编号
 * @param roleCode 当前工作区角色编码
 * @param permissions 当前工作区权限编码集合
 * @param workspaceRevision 工作区修订号
 * @param membershipRevision 成员关系修订号；个人工作区为空
 */
public record AppWorkspaceSessionSnapshotDTO(
    String workspaceKey,
    String workspaceType,
    Long tenantId,
    String ownerType,
    Long ownerId,
    String billingSubjectType,
    Long billingSubjectId,
    String roleCode,
    Set<String> permissions,
    Long workspaceRevision,
    Long membershipRevision
) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 冻结权限集合，防止令牌会话外部持有的可变集合改变授权结果。
     */
    public AppWorkspaceSessionSnapshotDTO {
        permissions = Set.copyOf(Objects.requireNonNull(permissions, "工作区权限集合不能为空"));
    }
}
