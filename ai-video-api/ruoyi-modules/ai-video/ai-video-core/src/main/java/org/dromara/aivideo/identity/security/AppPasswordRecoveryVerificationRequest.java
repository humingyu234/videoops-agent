package org.dromara.aivideo.identity.security;

/**
 * 交给受信任适配器的一次性找回验证码校验请求。
 *
 * <p>该对象绝不进入日志、审计或 HTTP 响应；其字符串形式会屏蔽挑战编号和验证码。</p>
 *
 * @param challengeId 不透明挑战编号
 * @param verificationCode 本次验证码
 * @param clientId 已验证的创作端客户端标识
 * @param clientRevision 已验证客户端当前修订号；客户端换密或停用后旧挑战不可继续消费
 */
public record AppPasswordRecoveryVerificationRequest(String challengeId, String verificationCode, String clientId,
                                                     long clientRevision) {

    @Override
    public String toString() {
        return "AppPasswordRecoveryVerificationRequest[challengeId=***, verificationCode=***, clientId="
            + clientId + ", clientRevision=" + clientRevision + "]";
    }
}
