package org.dromara.aivideo.identity.dto;

/** 已通过策略校验的创作端客户端快照。 */
public record AppAuthClientSnapshotDTO(String clientId, long clientRevision) {
}
