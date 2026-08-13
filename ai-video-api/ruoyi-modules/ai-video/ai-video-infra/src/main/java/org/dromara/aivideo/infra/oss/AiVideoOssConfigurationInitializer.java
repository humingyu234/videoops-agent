package org.dromara.aivideo.infra.oss;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.utils.StringUtils;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;

/** Applies the local OSS override after database-backed startup runners finish. */
@Slf4j
@RequiredArgsConstructor
public class AiVideoOssConfigurationInitializer implements ApplicationListener<ApplicationReadyEvent> {

    private static final String PRIVATE_ACCESS_POLICY = "0";

    private final AiVideoOssProperties properties;
    private final AiVideoOssConfigurationCache cache;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        initialize();
    }

    void initialize() {
        if (!properties.isEnabled()) {
            return;
        }
        requireText(properties.getConfigKey(), "config key");
        requireText(properties.getEndpoint(), "endpoint");
        requireText(properties.getAccessKey(), "access key");
        requireText(properties.getSecretKey(), "secret key");
        requireText(properties.getBucketName(), "bucket name");
        requireText(properties.getRegion(), "region");
        if (!PRIVATE_ACCESS_POLICY.equals(properties.getAccessPolicy())) {
            throw new IllegalStateException("AI video creator assets require a private OSS access policy (0)");
        }
        cache.put(properties.getConfigKey(), properties.toOssProperties());
        log.info("AI video OSS override initialized: key={}, endpoint={}, bucket={}, prefix={}",
            properties.getConfigKey(), properties.getEndpoint(), properties.getBucketName(), properties.getPrefix());
    }

    private void requireText(String value, String name) {
        if (StringUtils.isBlank(value)) {
            throw new IllegalStateException("AI video OSS " + name + " is not configured");
        }
    }
}
