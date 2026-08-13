package org.dromara.aivideo.identity.dto;

import org.dromara.aivideo.identity.domain.AppIdentityStatus;

/** 当前创作端身份状态快照。 */
public record AppIdentitySnapshotDTO(long userId, String username, AppIdentityStatus status,
                                     boolean mustChangePassword, long credentialRevision,
                                     long identityRevision, long permissionRevision) {
}
