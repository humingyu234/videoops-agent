package org.dromara.aivideo.identity.dto;

import org.dromara.aivideo.identity.domain.AppIdentityStatus;

import java.time.LocalDateTime;

/** 创作端角色的跨模块只读数据。 */
public record AppRoleDTO(Long roleId, String roleCode, String roleName, String scopeType, Boolean builtIn,
                         AppIdentityStatus status, Long roleRevision, LocalDateTime createTime,
                         LocalDateTime updateTime) {
}
