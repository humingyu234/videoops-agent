package org.dromara.aivideo.identity.security;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 在应用启动时解析可选的平台操作授权 SPI。
 * 未配置适配器时拒绝授权；多个适配器会在应用对外服务前被拒绝。
 */
@Component
public class AppIdentityOperationAuthorizer {

    private final IAppIdentityOperationAuthorizationService delegate;

    public AppIdentityOperationAuthorizer(
        ObjectProvider<IAppIdentityOperationAuthorizationService> authorizationPorts) {
        List<IAppIdentityOperationAuthorizationService> adapters = authorizationPorts.orderedStream().toList();
        if (adapters.size() > 1) {
            throw new IllegalStateException("配置了多个 AppIdentityOperationAuthorizationPort 适配器");
        }
        this.delegate = adapters.isEmpty() ? null : adapters.getFirst();
    }

    /**
     * 仅在安装唯一受信任适配器时委托其完成授权判断。
     */
    public boolean isAuthorized(AppActorContext actor, AppIdentityOperation operation, long targetResourceId) {
        return delegate != null && delegate.isAuthorized(actor, operation, targetResourceId);
    }
}
