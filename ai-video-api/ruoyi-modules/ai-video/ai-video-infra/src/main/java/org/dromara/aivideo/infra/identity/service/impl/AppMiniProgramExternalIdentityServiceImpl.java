package org.dromara.aivideo.infra.identity.service.impl;

import org.dromara.aivideo.identity.domain.AppExternalIdentityChannel;
import org.dromara.aivideo.identity.dto.AppExternalIdentityDTO;
import org.dromara.aivideo.identity.dto.AppExternalIdentityRequestDTO;
import org.dromara.aivideo.identity.service.IAppExternalIdentityService;
import org.dromara.aivideo.infra.identity.provider.AppMiniProgramIdentityProvider;

import java.util.Objects;

/**
 * 小程序身份提供方的 RuoYi Service 实现门面。
 */
public class AppMiniProgramExternalIdentityServiceImpl implements IAppExternalIdentityService {

    private final AppMiniProgramIdentityProvider provider;

    public AppMiniProgramExternalIdentityServiceImpl(AppMiniProgramIdentityProvider provider) {
        this.provider = Objects.requireNonNull(provider);
    }

    @Override
    public AppExternalIdentityChannel channel() {
        return provider.channel();
    }

    @Override
    public AppExternalIdentityDTO exchange(AppExternalIdentityRequestDTO command) {
        return provider.exchange(command);
    }
}
