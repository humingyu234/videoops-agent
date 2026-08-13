package org.dromara.aivideo.infra.verification.service.impl;

import org.dromara.aivideo.identity.dto.AppVerificationDeliveryDTO;
import org.dromara.aivideo.identity.security.AppVerificationChannel;
import org.dromara.aivideo.identity.service.IAppVerificationDeliveryService;
import org.dromara.aivideo.infra.verification.provider.AppMailVerificationProvider;

import java.util.Objects;

/**
 * 邮件验证码提供方的 RuoYi Service 实现门面。
 */
public class AppMailVerificationDeliveryServiceImpl implements IAppVerificationDeliveryService {

    private final AppMailVerificationProvider provider;

    public AppMailVerificationDeliveryServiceImpl(AppMailVerificationProvider provider) {
        this.provider = Objects.requireNonNull(provider);
    }

    @Override
    public AppVerificationChannel channel() {
        return provider.channel();
    }

    @Override
    public void deliver(AppVerificationDeliveryDTO command) {
        provider.deliver(command);
    }
}
