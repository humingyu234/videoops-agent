package org.dromara.aivideo.identity.dto;

import org.dromara.aivideo.identity.domain.AppIdentityStatus;

/** 创建创作端认证客户端的数据契约。 */
public record CreateAppAuthClientDTO(String clientKey, String grantTypes, String accessPaths, String ipWhitelist,
                                     long tokenTimeout, long activeTimeout, AppIdentityStatus status) {
}
