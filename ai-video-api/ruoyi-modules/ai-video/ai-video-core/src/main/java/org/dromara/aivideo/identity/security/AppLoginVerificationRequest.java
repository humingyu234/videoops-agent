package org.dromara.aivideo.identity.security;

/**
 * 创作端短信或邮件验证码登录所需的一次性验证请求。
 *
 * <p>挑战编号和验证码仅能进入受信任的验证码适配器，不得写入日志、审计或 HTTP 响应。</p>
 *
 * @param challengeId 不透明挑战编号
 * @param verificationCode 本次提交的验证码
 * @param clientId 已验证的创作端客户端标识
 * @param clientRevision 已验证客户端的当前修订号
 */
public record AppLoginVerificationRequest(String challengeId, String verificationCode, String clientId,
                                          long clientRevision) {

    @Override
    public String toString() {
        return "AppLoginVerificationRequest[challengeId=***, verificationCode=***, clientId=" + clientId
            + ", clientRevision=" + clientRevision + "]";
    }
}
