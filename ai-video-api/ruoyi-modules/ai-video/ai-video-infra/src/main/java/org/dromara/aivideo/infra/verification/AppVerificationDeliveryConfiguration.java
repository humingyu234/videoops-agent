package org.dromara.aivideo.infra.verification;

import org.dromara.aivideo.identity.security.ConditionalOnAppSecurityEnabled;
import org.dromara.aivideo.identity.service.IAppVerificationDeliveryService;
import org.dromara.aivideo.infra.verification.provider.AppMailVerificationProvider;
import org.dromara.aivideo.infra.verification.provider.AppSmsVerificationProvider;
import org.dromara.aivideo.infra.verification.service.impl.AppMailVerificationDeliveryServiceImpl;
import org.dromara.aivideo.infra.verification.service.impl.AppSmsVerificationDeliveryServiceImpl;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

/**
 * 创作端验证码投递基础设施装配。
 *
 * <p>安全开关关闭或任一渠道配置不完整时，不注册对应端口，以便 core 按失效关闭策略处理。</p>
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnAppSecurityEnabled
@EnableConfigurationProperties(AppVerificationDeliveryProperties.class)
public class AppVerificationDeliveryConfiguration {

    /**
     * 仅在创作端安全开关和完整短信渠道配置同时满足时装配。
     */
    @Bean("appSmsVerificationDelivery")
    @ConditionalOnAppSecurityEnabled
    @Conditional(AppSmsVerificationDeliveryConfiguredCondition.class)
    public IAppVerificationDeliveryService appSmsVerificationDelivery(AppVerificationDeliveryProperties properties) {
        return new AppSmsVerificationDeliveryServiceImpl(new AppSmsVerificationProvider(properties));
    }

    /**
     * 仅在创作端安全开关和完整邮件渠道配置同时满足时装配。
     */
    @Bean("appMailVerificationDelivery")
    @ConditionalOnAppSecurityEnabled
    @Conditional(AppMailVerificationDeliveryConfiguredCondition.class)
    public IAppVerificationDeliveryService appMailVerificationDelivery(AppVerificationDeliveryProperties properties) {
        return new AppMailVerificationDeliveryServiceImpl(new AppMailVerificationProvider(properties));
    }
}
