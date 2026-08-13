package org.dromara.aivideo.identity.security;

/**
 * 已通过验证码校验、但尚未随密码变更事务提交的短期预留。
 *
 * <p>预留编号仅用于受信任适配器内部完成或释放 Redis 状态，绝不进入日志、审计或 HTTP 响应。</p>
 *
 * @param challengeId 不透明验证码挑战编号
 * @param reservationId 不透明预留编号
 * @param grant 已验证的恢复凭证
 */
public record AppPasswordRecoveryReservation(String challengeId, String reservationId, AppPasswordRecoveryGrant grant) {

    public AppPasswordRecoveryReservation {
        if (challengeId == null || challengeId.isBlank() || reservationId == null || reservationId.isBlank() || grant == null) {
            throw new IllegalArgumentException("找回密码预留凭证不完整");
        }
    }

    @Override
    public String toString() {
        return "AppPasswordRecoveryReservation[challengeId=***, reservationId=***, grant=" + grant + "]";
    }
}
