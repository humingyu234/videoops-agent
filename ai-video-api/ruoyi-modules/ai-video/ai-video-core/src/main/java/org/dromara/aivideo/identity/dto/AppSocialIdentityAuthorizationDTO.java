package org.dromara.aivideo.identity.dto;

/**
 * 社交平台回调授权命令。
 *
 * @param provider 第三方来源白名单键
 * @param authorizationCode 外部平台一次性授权码
 * @param state 外部平台回调状态令牌
 */
public record AppSocialIdentityAuthorizationDTO(String provider, String authorizationCode,
                                                    String state) implements AppExternalIdentityRequestDTO {

    public AppSocialIdentityAuthorizationDTO {
        if (isBlank(provider) || isBlank(authorizationCode) || isBlank(state)) {
            throw new IllegalArgumentException("第三方授权参数无效");
        }
    }

    @Override
    public String toString() {
        return "AppSocialIdentityAuthorizationCommand[provider=" + provider + ", authorizationCode=***, state=***]";
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
