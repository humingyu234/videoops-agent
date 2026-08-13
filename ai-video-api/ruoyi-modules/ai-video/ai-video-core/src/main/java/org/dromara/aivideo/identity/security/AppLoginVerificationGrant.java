package org.dromara.aivideo.identity.security;

/**
 * 验证码登录原子校验成功后签发给内部认证链路的短期凭证。
 *
 * <p>目标联系方式不进入该对象；后续必须以用户与修订号重新核验身份状态。</p>
 *
 * @param userId 创作端用户编号
 * @param channel 已验证的联系方式渠道
 * @param credentialRevision 挑战创建时的凭据修订号
 * @param identityRevision 挑战创建时的身份修订号
 */
public record AppLoginVerificationGrant(long userId, AppVerificationChannel channel,
                                        long credentialRevision, long identityRevision) {

    public AppLoginVerificationGrant {
        if (userId <= 0 || channel == null || credentialRevision <= 0 || identityRevision <= 0) {
            throw new IllegalArgumentException("验证码登录凭证不完整");
        }
    }

    @Override
    public String toString() {
        return "AppLoginVerificationGrant[userId=" + userId + ", channel=" + channel
            + ", credentialRevision=" + credentialRevision + ", identityRevision=" + identityRevision + "]";
    }
}
