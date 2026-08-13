package org.dromara.aivideo.user.auth.domain.vo;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 创作端公开验证码挑战响应。
 *
 * <p>只包含后续提交所需的不透明挑战、脱敏目标和固定有效期，不返回验证码或账号存在状态。</p>
 *
 * @param challengeId 不透明挑战编号
 * @param maskedTarget 脱敏后的接收目标
 * @param expiresIn 有效秒数
 */
public record AppVerificationChallengeVo(
    @JsonProperty("challenge_id") String challengeId,
    @JsonProperty("masked_target") String maskedTarget,
    @JsonProperty("expires_in") long expiresIn
) {

    @Override
    public String toString() {
        return "AppVerificationChallengeVo[challengeId=***, maskedTarget=***, expiresIn=" + expiresIn + "]";
    }
}
