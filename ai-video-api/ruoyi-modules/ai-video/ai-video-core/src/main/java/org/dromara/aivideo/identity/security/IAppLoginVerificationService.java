package org.dromara.aivideo.identity.security;

/**
 * 创作端验证码登录的受信任基础设施端口。
 *
 * <p>成功登录的会话签发和登录审计完成后才可消费挑战；失败时必须释放预留以允许用户重试。</p>
 */
public interface IAppLoginVerificationService {

    /**
     * 原子校验并预留登录挑战。
     *
     * @param request 不透明挑战编号、验证码和已验证客户端
     * @return 成功时返回内部预留；失败时返回 {@code null}
     */
    AppLoginVerificationReservation reserve(AppLoginVerificationRequest request);

    /**
     * 成功签发会话且写入登录审计后消费挑战。
     *
     * @param reservation 已验证预留
     */
    void commit(AppLoginVerificationReservation reservation);

    /**
     * 登录链路失败时释放挑战预留。
     *
     * @param reservation 已验证预留
     */
    void release(AppLoginVerificationReservation reservation);
}
