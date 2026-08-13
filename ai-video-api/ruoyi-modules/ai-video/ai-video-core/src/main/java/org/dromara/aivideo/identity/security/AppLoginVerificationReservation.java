package org.dromara.aivideo.identity.security;

/**
 * 已通过校验但尚未随登录会话与审计完成而消费的验证码预留。
 *
 * <p>预留编号仅供受信任的验证码适配器完成或释放 Redis 状态，不能离开服务端边界。</p>
 *
 * @param challengeId 不透明挑战编号
 * @param reservationId 不透明预留编号
 * @param grant 已验证登录凭证
 */
public record AppLoginVerificationReservation(String challengeId, String reservationId,
                                              AppLoginVerificationGrant grant) {

    public AppLoginVerificationReservation {
        if (challengeId == null || challengeId.isBlank() || reservationId == null || reservationId.isBlank()
            || grant == null) {
            throw new IllegalArgumentException("验证码登录预留凭证不完整");
        }
    }

    @Override
    public String toString() {
        return "AppLoginVerificationReservation[challengeId=***, reservationId=***, grant=" + grant + "]";
    }
}
