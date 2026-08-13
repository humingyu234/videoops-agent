package org.dromara.aivideo.identity.dto;

/**
 * 可安全返回给客户端的验证码挑战摘要。
 *
 * @param challengeId 一次性挑战编号
 * @param maskedTarget 由提交目标计算出的脱敏联系方式
 * @param expiresInSeconds 剩余有效秒数
 */
public record AppVerificationChallengeDTO(String challengeId, String maskedTarget, long expiresInSeconds) {

    @Override
    public String toString() {
        return "AppVerificationChallenge[challengeId=***, maskedTarget=***, expiresInSeconds="
            + expiresInSeconds + "]";
    }
}
