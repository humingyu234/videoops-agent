package org.dromara.aivideo.identity.dto;

import org.dromara.aivideo.identity.domain.AppActorType;

/**
 * 追加创作端安全审计的命令。
 *
 * @param resourceType 被操作资源类型
 * @param resourceId 被操作资源编号
 * @param action 安全动作
 * @param actorType 操作者类型
 * @param actorId 操作者编号
 * @param beforeDigest 变更前安全摘要
 * @param afterDigest 变更后安全摘要
 * @param reason 操作原因
 */
public record AppSecurityAuditDTO(
    String resourceType,
    String resourceId,
    String action,
    AppActorType actorType,
    Long actorId,
    String beforeDigest,
    String afterDigest,
    String reason
) {
}
