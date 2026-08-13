package org.dromara.aivideo.identity.dto;

import org.dromara.aivideo.identity.domain.AppIdentityStatus;

/** 更新创作端角色的数据契约。 */
public record UpdateAppRoleDTO(long roleId, String roleName, AppIdentityStatus status, long expectedRoleRevision) {
}
