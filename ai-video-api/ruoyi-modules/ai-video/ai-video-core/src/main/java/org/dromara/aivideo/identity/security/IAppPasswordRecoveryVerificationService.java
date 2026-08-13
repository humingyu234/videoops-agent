package org.dromara.aivideo.identity.security;

/**
 * 创作端找回验证码的受信任基础设施端口。
 *
 * <p>实现必须原子地比较、限制错误次数并预留挑战，不能使用“先读取、后删除”的非原子逻辑。
 * 密码、审计和会话事务提交后才可以完成预留；事务失败时必须释放预留。</p>
 */
public interface IAppPasswordRecoveryVerificationService {

    /**
     * 原子校验并短期预留找回验证码。
     *
     * @param request 不透明挑战编号、验证码与已验证客户端
     * @return 成功时的内部恢复预留；失败时返回 {@code null}
     */
    AppPasswordRecoveryReservation reserve(AppPasswordRecoveryVerificationRequest request);

    /**
     * 在密码变更所在事务已提交后完成预留，使挑战失效。
     *
     * @param reservation 已验证的恢复预留
     */
    void commit(AppPasswordRecoveryReservation reservation);

    /**
     * 在密码变更、审计或会话处理失败时释放预留，使原挑战仍可在有效期内重试。
     *
     * @param reservation 已验证的恢复预留
     */
    void release(AppPasswordRecoveryReservation reservation);
}
