package org.dromara.aivideo.identity.dto;

/**
 * 微信小程序授权命令。
 *
 * @param authorizationCode 微信小程序一次性授权码
 */
public record AppMiniProgramAuthorizationDTO(String authorizationCode) implements AppExternalIdentityRequestDTO {

    public AppMiniProgramAuthorizationDTO {
        if (authorizationCode == null || authorizationCode.isBlank()) {
            throw new IllegalArgumentException("小程序授权参数无效");
        }
    }

    @Override
    public String toString() {
        return "AppMiniProgramAuthorizationCommand[authorizationCode=***]";
    }
}
