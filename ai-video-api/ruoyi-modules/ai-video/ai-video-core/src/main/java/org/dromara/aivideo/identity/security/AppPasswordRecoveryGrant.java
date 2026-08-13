package org.dromara.aivideo.identity.security;

/**
 * 找回验证码原子消费成功后由受信任适配器签发的内部恢复凭证。
 *
 * <p>目标用户、渠道和修订号都由验证码挑战快照提供，调用者不能自行构造同等来源的
 * HTTP 请求参数。联系方式本身不进入恢复凭证，避免它随 Redis 挑战或内部对象扩散。</p>
 *
 * @param userId 创作端用户编号
 * @param channel 已验证联系方式渠道
 * @param credentialRevision 挑战创建时的凭据修订号
 * @param identityRevision 挑战创建时的身份修订号
 */
public record AppPasswordRecoveryGrant(long userId, AppVerificationChannel channel,
                                       long credentialRevision, long identityRevision) {

    public AppPasswordRecoveryGrant {
        if (userId <= 0 || channel == null || credentialRevision <= 0 || identityRevision <= 0) {
            throw new IllegalArgumentException("找回密码恢复凭证不完整");
        }
    }

    @Override
    public String toString() {
        return "AppPasswordRecoveryGrant[userId=" + userId + ", channel=" + channel
            + ", credentialRevision=" + credentialRevision
            + ", identityRevision=" + identityRevision + "]";
    }
}
