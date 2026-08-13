package org.dromara.aivideo.infra.identity;

import org.dromara.aivideo.identity.service.IAppExternalIdentityService;
import org.dromara.aivideo.identity.security.ConditionalOnAppSecurityEnabled;
import org.dromara.aivideo.infra.identity.provider.AppExternalIdentityReplayGuard;
import org.dromara.aivideo.infra.identity.provider.AppMiniProgramIdentityProvider;
import org.dromara.aivideo.infra.identity.provider.AppSocialIdentityProvider;
import org.dromara.aivideo.infra.identity.service.impl.AppMiniProgramExternalIdentityServiceImpl;
import org.dromara.aivideo.infra.identity.service.impl.AppSocialExternalIdentityServiceImpl;
import org.dromara.common.social.config.SocialAutoConfiguration;
import org.dromara.common.social.config.properties.SocialProperties;
import org.redisson.api.RedissonClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * 创作端外部身份适配器装配。
 *
 * <p>只有创作端运行时、共享回放保护和对应渠道配置均完整时才注册端口。</p>
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnAppSecurityEnabled
@EnableConfigurationProperties(AppExternalIdentityProperties.class)
@Import(SocialAutoConfiguration.class)
public class AppExternalIdentityConfiguration {

    /**
     * 注册社交授权适配器。
     */
    @Bean("appSocialIdentityGateway")
    @Conditional(AppSocialIdentityGatewayConfiguredCondition.class)
    public IAppExternalIdentityService appSocialIdentityGateway(AppExternalIdentityProperties properties,
                                                            SocialProperties socialProperties,
                                                            RedissonClient redissonClient) {
        AppSocialIdentityProvider provider = new AppSocialIdentityProvider(properties, socialProperties,
            new AppExternalIdentityReplayGuard(properties, redissonClient));
        return new AppSocialExternalIdentityServiceImpl(provider);
    }

    /**
     * 注册微信小程序授权适配器。
     */
    @Bean("appMiniProgramIdentityGateway")
    @Conditional(AppMiniProgramIdentityGatewayConfiguredCondition.class)
    public IAppExternalIdentityService appMiniProgramIdentityGateway(AppExternalIdentityProperties properties,
                                                                 RedissonClient redissonClient) {
        AppMiniProgramIdentityProvider provider = new AppMiniProgramIdentityProvider(properties,
            new AppExternalIdentityReplayGuard(properties, redissonClient));
        return new AppMiniProgramExternalIdentityServiceImpl(provider);
    }
}
