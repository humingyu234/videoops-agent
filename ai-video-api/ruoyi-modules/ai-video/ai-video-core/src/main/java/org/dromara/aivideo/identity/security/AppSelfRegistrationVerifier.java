package org.dromara.aivideo.identity.security;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 在应用启动时解析可选的自注册凭证校验 SPI。
 * 未配置适配器时拒绝自注册；多个适配器会导致启动失败，避免校验行为依赖 Bean 顺序。
 */
@Component
public class AppSelfRegistrationVerifier {

    private final IAppSelfRegistrationVerificationService delegate;

    public AppSelfRegistrationVerifier(
        ObjectProvider<IAppSelfRegistrationVerificationService> verificationPorts) {
        List<IAppSelfRegistrationVerificationService> adapters = verificationPorts.orderedStream().toList();
        if (adapters.size() > 1) {
            throw new IllegalStateException("配置了多个 AppSelfRegistrationVerificationPort 适配器");
        }
        this.delegate = adapters.isEmpty() ? null : adapters.getFirst();
    }

    /**
     * 存在唯一受信任适配器时校验并一次性消费注册凭证；否则返回 {@code false}。
     */
    public boolean verifyAndConsume(AppSelfRegistrationVerificationRequest request) {
        return delegate != null && delegate.verifyAndConsume(request);
    }
}
