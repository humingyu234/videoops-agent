package org.dromara.aivideo.identity.dto;

import java.io.Serial;
import java.io.Serializable;

/**
 * 创作端用户、认证客户端和当前工作区的会话主体快照。
 *
 * @param appUserId 创作端用户编号
 * @param username 创作端用户名
 * @param clientId 认证客户端标识
 * @param credentialRevision 凭据修订号
 * @param identityRevision 身份修订号
 * @param permissionRevision 权限修订号
 * @param clientRevision 认证客户端修订号
 * @param workspace 当前工作区快照
 */
public record AppPrincipalSnapshotDTO(
    Long appUserId,
    String username,
    String clientId,
    Long credentialRevision,
    Long identityRevision,
    Long permissionRevision,
    Long clientRevision,
    AppWorkspaceSessionSnapshotDTO workspace
) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
}
