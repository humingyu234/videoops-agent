package org.dromara.aivideo.bootstrap;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.oss.properties.OssProperties;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Loads the creator API's default OSS client without assembling operating-side modules.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CreatorOssConfigurationInitializer implements ApplicationRunner {

    private static final String PRIVATE_ACCESS_POLICY = "0";
    private static final String DEFAULT_CONFIG_SQL = """
        SELECT config_key, access_key, secret_key, bucket_name, prefix,
               endpoint, domain_url, is_https, region, access_policy
        FROM sys_oss_config
        WHERE status = 'Y'
        ORDER BY oss_config_id
        LIMIT 1
        """;

    private final JdbcTemplate jdbcTemplate;
    private final CreatorOssConfigurationCache configurationCache;

    @Override
    public void run(ApplicationArguments args) {
        try {
            Map<String, Object> row = jdbcTemplate.queryForMap(DEFAULT_CONFIG_SQL);
            if (!PRIVATE_ACCESS_POLICY.equals(text(row, "access_policy"))) {
                throw new IllegalStateException("Creator uploads require a private OSS access policy (0)");
            }
            String configKey = text(row, "config_key");
            if (StringUtils.isBlank(configKey)) {
                log.warn("Creator OSS configuration is enabled but has no config key");
                return;
            }
            OssProperties properties = toProperties(row);
            configurationCache.put(configKey, properties);
            log.info("Creator OSS configuration initialized: {}", configKey);
        } catch (EmptyResultDataAccessException exception) {
            log.warn("No enabled OSS configuration is available for creator uploads");
        }
    }

    private OssProperties toProperties(Map<String, Object> row) {
        OssProperties properties = new OssProperties();
        properties.setAccessKey(text(row, "access_key"));
        properties.setSecretKey(text(row, "secret_key"));
        properties.setBucketName(text(row, "bucket_name"));
        properties.setPrefix(text(row, "prefix"));
        properties.setEndpoint(text(row, "endpoint"));
        properties.setDomainUrl(text(row, "domain_url"));
        properties.setIsHttps(text(row, "is_https"));
        properties.setRegion(text(row, "region"));
        properties.setAccessPolicy(text(row, "access_policy"));
        return properties;
    }

    private String text(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? "" : value.toString();
    }
}
