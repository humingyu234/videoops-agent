package org.dromara.aivideo.infra.oss;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Registers the optional local OSS override used by both API starters. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AiVideoOssProperties.class)
public class AiVideoOssConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "aivideo.oss", name = "enabled", havingValue = "true")
    public AiVideoOssConfigurationCache aiVideoOssConfigurationCache() {
        return new AiVideoOssConfigurationCache();
    }

    @Bean
    @ConditionalOnProperty(prefix = "aivideo.oss", name = "enabled", havingValue = "true")
    public AiVideoOssConfigurationInitializer aiVideoOssConfigurationInitializer(
        AiVideoOssProperties properties,
        AiVideoOssConfigurationCache cache) {
        return new AiVideoOssConfigurationInitializer(properties, cache);
    }
}
