package org.dromara.aivideo.infra.digitalhuman;

import org.dromara.aivideo.digitalhuman.service.IDigitalHumanMediaStorageService;
import org.dromara.aivideo.digitalhuman.service.IDigitalHumanVideoService;
import org.dromara.aivideo.digitalhuman.service.IVoiceSynthesisService;
import org.dromara.aivideo.identity.security.ConditionalOnAppSecurityEnabled;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

/**
 * 数字人供应商与私有媒体条件装配。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnAppSecurityEnabled
@EnableConfigurationProperties(DigitalHumanProviderProperties.class)
public class DigitalHumanProviderConfiguration {

    @Bean
    @Conditional(IndexTts2ConfiguredCondition.class)
    public IVoiceSynthesisService voiceSynthesisService(DigitalHumanProviderProperties properties) {
        return new IndexTts2Client(properties.getIndexTts2());
    }

    @Bean
    @Conditional(ComfyUiConfiguredCondition.class)
    public IDigitalHumanVideoService digitalHumanVideoService(DigitalHumanProviderProperties properties) {
        return new ComfyUiClient(properties.getComfyUi());
    }

    @Bean
    @Conditional(DigitalHumanStorageConfiguredCondition.class)
    public IDigitalHumanMediaStorageService digitalHumanMediaStorageService(
        DigitalHumanProviderProperties properties) {
        return new FileSystemDigitalHumanMediaStorageService(properties.getMediaRoot());
    }

    @Bean
    @ConditionalOnMissingBean(IVoiceSynthesisService.class)
    public IVoiceSynthesisService unavailableVoiceSynthesisService() {
        return request -> {
            throw new ServiceException("声音服务未配置");
        };
    }

    @Bean
    @ConditionalOnMissingBean(IDigitalHumanVideoService.class)
    public IDigitalHumanVideoService unavailableDigitalHumanVideoService() {
        return new IDigitalHumanVideoService() {
            @Override
            public String submit(org.dromara.aivideo.digitalhuman.dto.DigitalHumanVideoSubmitDTO request) {
                throw new ServiceException("视频服务未配置");
            }

            @Override
            public org.dromara.aivideo.digitalhuman.dto.DigitalHumanVideoPollDTO poll(String providerJobId) {
                throw new ServiceException("视频服务未配置");
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean(IDigitalHumanMediaStorageService.class)
    public IDigitalHumanMediaStorageService unavailableDigitalHumanMediaStorageService() {
        return new IDigitalHumanMediaStorageService() {
            @Override
            public org.dromara.aivideo.digitalhuman.dto.DigitalHumanStoredMediaDTO storeInput(
                Long jobId, String fileName, String mediaType, byte[] content) {
                throw new ServiceException("数字人媒体目录未配置");
            }

            @Override
            public org.dromara.aivideo.digitalhuman.dto.DigitalHumanStoredMediaDTO storeOutput(
                Long jobId, String fileName, String mediaType, byte[] content) {
                throw new ServiceException("数字人媒体目录未配置");
            }

            @Override
            public org.dromara.aivideo.digitalhuman.dto.DigitalHumanMediaContentDTO read(String key) {
                throw new ServiceException("数字人媒体目录未配置");
            }

            @Override
            public void delete(String key) {
                throw new ServiceException("数字人媒体目录未配置");
            }
        };
    }
}
