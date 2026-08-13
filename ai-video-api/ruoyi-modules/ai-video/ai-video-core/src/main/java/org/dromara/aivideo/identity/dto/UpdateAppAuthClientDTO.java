package org.dromara.aivideo.identity.dto;

import org.dromara.aivideo.identity.domain.AppIdentityStatus;

/** 更新创作端认证客户端的数据契约。 */
public record UpdateAppAuthClientDTO(long id, String clientKey, String grantTypes, String accessPaths,
                                     String ipWhitelist, long tokenTimeout, long activeTimeout,
                                     AppIdentityStatus status, long expectedClientRevision) {
}
