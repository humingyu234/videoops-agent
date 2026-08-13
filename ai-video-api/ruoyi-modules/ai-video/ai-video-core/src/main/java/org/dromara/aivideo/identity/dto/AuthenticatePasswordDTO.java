package org.dromara.aivideo.identity.dto;

/** 密码认证数据契约。 */
public record AuthenticatePasswordDTO(String identifier, String password, String clientId) {

    @Override
    public String toString() {
        return "AuthenticatePassword[identifier=***, password=***, clientId=" + clientId + "]";
    }
}
