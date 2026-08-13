package org.dromara.aivideo.identity.security;

/**
 * 交给受信任自注册验证适配器的无敏感身份标识。
 *
 * <p>该对象故意不包含原始密码、验证码或恢复凭据；外部适配器只能依据已标准化的
 * 用户名、手机号和邮箱确认验证流程已经完成。</p>
 *
 * @param registrationGrantId 不透明的一次性注册凭证标识
 * @param clientId 注册凭证绑定的客户端标识
 * @param usernameNormalized 已标准化用户名
 * @param phoneNormalized 已标准化手机号，可为空
 * @param emailNormalized 已标准化邮箱，可为空
 */
public record AppSelfRegistrationVerificationRequest(
    String registrationGrantId,
    String clientId,
    String usernameNormalized,
    String phoneNormalized,
    String emailNormalized
) {
}
