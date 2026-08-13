package org.dromara.aivideo.identity.dto;

/** 认证成功后的会话快照基础数据。 */
public record AppAuthenticatedIdentityDTO(long userId, String username, boolean mustChangePassword,
                                          long credentialRevision, long identityRevision,
                                          long permissionRevision) {
}
