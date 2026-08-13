package org.dromara.aivideo.identity.security;

import org.dromara.common.core.exception.ServiceException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 解析唯一的找回验证码基础设施适配器。
 *
 * <p>未配置适配器或校验失败均拒绝找回；多个适配器会在启动时失败，避免安全行为依赖 Bean 顺序。</p>
 */
@Component
public class AppPasswordRecoveryVerifier {

    private final IAppPasswordRecoveryVerificationService delegate;

    public AppPasswordRecoveryVerifier(
        ObjectProvider<IAppPasswordRecoveryVerificationService> verificationPorts) {
        List<IAppPasswordRecoveryVerificationService> adapters = verificationPorts.orderedStream().toList();
        if (adapters.size() > 1) {
            throw new IllegalStateException("配置了多个 AppPasswordRecoveryVerificationPort 适配器");
        }
        this.delegate = adapters.isEmpty() ? null : adapters.getFirst();
    }

    /**
     * 原子校验并预留一次性找回验证码。
     *
     * @param request 不透明找回验证码请求
     * @return 服务端可信恢复预留
     */
    public AppPasswordRecoveryReservation reserve(AppPasswordRecoveryVerificationRequest request) {
        if (request == null || delegate == null) {
            throw new ServiceException("创作端找回密码验证不可用");
        }
        AppPasswordRecoveryReservation reservation = delegate.reserve(request);
        if (reservation == null) {
            throw new ServiceException("创作端找回密码验证未通过");
        }
        return reservation;
    }

    /**
     * 事务提交后完成验证码预留。Redis 清理异常不能回滚已提交的密码更新；旧挑战仍受修订号约束。
     */
    public void commit(AppPasswordRecoveryReservation reservation) {
        if (reservation == null || delegate == null) {
            return;
        }
        try {
            delegate.commit(reservation);
        } catch (RuntimeException ignored) {
            // 已提交的密码修订号会阻止旧挑战再次更新密码，Redis 到期前保持预留是安全的失败模式。
        }
    }

    /**
     * 事务失败时尽力释放预留，保留原始业务异常。
     */
    public void release(AppPasswordRecoveryReservation reservation) {
        if (reservation == null || delegate == null) {
            return;
        }
        try {
            delegate.release(reservation);
        } catch (RuntimeException ignored) {
            // Redis 不可用时保持预留直至过期，不能因清理失败掩盖原始失败。
        }
    }
}
