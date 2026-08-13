package org.dromara.aivideo.infra.oss;

import org.dromara.common.core.constant.CacheNames;
import org.dromara.common.json.utils.JsonUtils;
import org.dromara.common.oss.constant.OssConstant;
import org.dromara.common.oss.properties.OssProperties;
import org.dromara.common.redis.utils.CacheUtils;
import org.dromara.common.redis.utils.RedisUtils;

/** Publishes a validated OSS override to the RuoYi runtime caches. */
public class AiVideoOssConfigurationCache {

    public void put(String configKey, OssProperties properties) {
        CacheUtils.put(CacheNames.SYS_OSS_CONFIG, configKey, JsonUtils.toJsonString(properties));
        RedisUtils.setCacheObject(OssConstant.DEFAULT_CONFIG_KEY, configKey);
    }
}
