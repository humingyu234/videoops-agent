package org.dromara.aivideo.infra.oss;

import org.dromara.common.oss.client.OssClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

/** Registers the optional local OSS override used by both API starters. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AiVideoOssProperties.class)
public class AiVideoOssConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "aivideo.oss", name = "enabled", havingValue = "true")
    public AiVideoOssConfigurationInitializer aiVideoOssConfigurationInitializer(
        AiVideoOssProperties properties) {
        return new AiVideoOssConfigurationInitializer(properties);
    }

    @Bean(name = "aiVideoOssClient", destroyMethod = "close")
    @Lazy
    @ConditionalOnProperty(prefix = "aivideo.oss", name = "enabled", havingValue = "true")
    public OssClient aiVideoOssClient(AiVideoOssConfigurationInitializer initializer) {
        return initializer.initialize();
    }
}
