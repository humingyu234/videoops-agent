package org.dromara.aivideo.infra.oss;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.aivideo.asset.service.VideoOpsObjectKey;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.oss.client.DefaultOssClientImpl;
import org.dromara.common.oss.client.OssClient;
import org.dromara.common.oss.config.OssClientConfig;

/** Builds the process-local OSS client used by VideoOps asset operations. */
@Slf4j
@RequiredArgsConstructor
public class AiVideoOssConfigurationInitializer {

    private static final String PRIVATE_ACCESS_POLICY = "0";

    private final AiVideoOssProperties properties;

    OssClient initialize() {
        requireText(properties.getConfigKey(), "config key");
        requireText(properties.getEndpoint(), "endpoint");
        requireText(properties.getAccessKey(), "access key");
        requireText(properties.getSecretKey(), "secret key");
        requireText(properties.getBucketName(), "bucket name");
        requireText(properties.getRegion(), "region");
        VideoOpsObjectKey.requireProjectPrefix(properties.getPrefix());
        if (!PRIVATE_ACCESS_POLICY.equals(properties.getAccessPolicy())) {
            throw new IllegalStateException("AI video creator assets require a private OSS access policy (0)");
        }
        OssClient client = new DefaultOssClientImpl(properties.getConfigKey(),
            OssClientConfig.formProperties(properties.toOssProperties()));
        log.info("AI video OSS override initialized: key={}, namespaceConfigured=true",
            properties.getConfigKey());
        return client;
    }

    private void requireText(String value, String name) {
        if (StringUtils.isBlank(value)) {
            throw new IllegalStateException("AI video OSS " + name + " is not configured");
        }
    }
}
