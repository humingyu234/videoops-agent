package org.dromara.aivideo.bootstrap;

import org.dromara.common.core.constant.CacheNames;
import org.dromara.common.json.utils.JsonUtils;
import org.dromara.common.oss.constant.OssConstant;
import org.dromara.common.oss.properties.OssProperties;
import org.dromara.common.redis.utils.CacheUtils;
import org.dromara.common.redis.utils.RedisUtils;
import org.springframework.stereotype.Component;

/**
 * Writes the creator API's OSS client configuration to the shared RuoYi caches.
 */
@Component
public class CreatorOssConfigurationCache {

    public void put(String configKey, OssProperties properties) {
        CacheUtils.put(CacheNames.SYS_OSS_CONFIG, configKey, JsonUtils.toJsonString(properties));
        RedisUtils.setCacheObject(OssConstant.DEFAULT_CONFIG_KEY, configKey);
    }
}
