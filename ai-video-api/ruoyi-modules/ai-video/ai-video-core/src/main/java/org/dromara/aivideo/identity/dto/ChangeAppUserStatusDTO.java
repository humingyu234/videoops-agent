package org.dromara.aivideo.identity.dto;

import org.dromara.aivideo.identity.domain.AppIdentityStatus;

/** 变更创作端用户状态的数据契约。 */
public record ChangeAppUserStatusDTO(long userId, AppIdentityStatus status, long expectedIdentityRevision) {
}
