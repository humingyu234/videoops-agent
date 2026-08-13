package org.dromara.aivideo.identity.security;

/**
 * 由受信任注册校验适配器生成的不透明凭证。
 * 它不包含密码或一次性验证码；核心仅将其标识及客户端绑定传给 SPI。
 *
 * @param registrationGrantId 不透明的一次性凭证标识
 * @param clientId 凭证绑定的客户端标识
 */
public record AppSelfRegistrationGrant(String registrationGrantId, String clientId) {
}
