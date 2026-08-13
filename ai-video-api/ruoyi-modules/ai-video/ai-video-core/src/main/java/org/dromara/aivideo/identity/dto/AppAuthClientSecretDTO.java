package org.dromara.aivideo.identity.dto;

/** 管理端创建或换密后一次性返回的客户端凭据。 */
public record AppAuthClientSecretDTO(long id, String clientId, String clientKey, String clientSecret) {

    @Override
    public String toString() {
        return "AppAuthClientSecret[id=" + id + ", clientId=" + clientId + ", clientKey=" + clientKey
            + ", clientSecret=***]";
    }
}
