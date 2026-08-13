package org.dromara.aivideo.bootstrap;

import org.dromara.common.oss.properties.OssProperties;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("dev")
class CreatorOssConfigurationInitializerTest {

    @Test
    void loadsPrivateOssConfigurationForTheCreatorApi() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        CreatorOssConfigurationCache configurationCache = mock(CreatorOssConfigurationCache.class);
        when(jdbcTemplate.queryForMap(anyString())).thenReturn(ossRow("0"));

        OssProperties expected = new OssProperties();
        expected.setAccessKey("ruoyi");
        expected.setSecretKey("ruoyi123");
        expected.setBucketName("ruoyi");
        expected.setPrefix("");
        expected.setEndpoint("127.0.0.1:9000");
        expected.setDomainUrl("");
        expected.setIsHttps("N");
        expected.setRegion("");
        expected.setAccessPolicy("0");

        new CreatorOssConfigurationInitializer(jdbcTemplate, configurationCache).run(null);

        verify(configurationCache).put("minio", expected);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"1", "2"})
    void rejectsNonPrivateOssConfigurationWithoutCachingIt(String accessPolicy) {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        CreatorOssConfigurationCache configurationCache = mock(CreatorOssConfigurationCache.class);
        when(jdbcTemplate.queryForMap(anyString())).thenReturn(ossRow(accessPolicy));

        CreatorOssConfigurationInitializer initializer =
            new CreatorOssConfigurationInitializer(jdbcTemplate, configurationCache);

        assertThatThrownBy(() -> initializer.run(null))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("private");
        verifyNoInteractions(configurationCache);
    }

    private Map<String, Object> ossRow(String accessPolicy) {
        Map<String, Object> row = new HashMap<>();
        row.put("config_key", "minio");
        row.put("access_key", "ruoyi");
        row.put("secret_key", "ruoyi123");
        row.put("bucket_name", "ruoyi");
        row.put("prefix", "");
        row.put("endpoint", "127.0.0.1:9000");
        row.put("domain_url", "");
        row.put("is_https", "N");
        row.put("region", "");
        row.put("access_policy", accessPolicy);
        return row;
    }
}
