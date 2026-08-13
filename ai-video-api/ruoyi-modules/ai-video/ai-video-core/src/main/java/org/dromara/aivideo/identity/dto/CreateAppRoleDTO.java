package org.dromara.aivideo.identity.dto;

import org.dromara.aivideo.identity.domain.AppIdentityStatus;

/** 创建非内置创作端角色的数据契约。 */
public record CreateAppRoleDTO(String roleCode, String roleName, String scopeType, AppIdentityStatus status) {
}
