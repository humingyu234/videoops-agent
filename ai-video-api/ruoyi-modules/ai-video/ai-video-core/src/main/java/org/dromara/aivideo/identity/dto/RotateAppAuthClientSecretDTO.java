package org.dromara.aivideo.identity.dto;

/** 轮换创作端认证客户端密钥的数据契约。 */
public record RotateAppAuthClientSecretDTO(long id, long expectedClientRevision) {
}
