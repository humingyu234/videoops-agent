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
        expected.setAccessKey("sentinel-access");
        expected.setSecretKey("sentinel-secret");
        expected.setBucketName("sentinel-bucket");
        expected.setPrefix("videoops-agent/dev");
        expected.setEndpoint("sentinel-endpoint");
        expected.setDomainUrl("");
        expected.setIsHttps("N");
        expected.setRegion("");
        expected.setAccessPolicy("0");

        new CreatorOssConfigurationInitializer(jdbcTemplate, configurationCache).run(null);

        verify(configurationCache).put("videoops-agent-dev", expected);
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

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "ai-video", "videoops-agent/dev/"})
    void rejectsEmptyOrLegacyNamespacesWithoutCachingThem(String prefix) {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        CreatorOssConfigurationCache configurationCache = mock(CreatorOssConfigurationCache.class);
        Map<String, Object> row = ossRow("0");
        row.put("prefix", prefix);
        when(jdbcTemplate.queryForMap(anyString())).thenReturn(row);

        assertThatThrownBy(() -> new CreatorOssConfigurationInitializer(jdbcTemplate, configurationCache).run(null))
            .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(configurationCache);
    }

    private Map<String, Object> ossRow(String accessPolicy) {
        Map<String, Object> row = new HashMap<>();
        row.put("config_key", "videoops-agent-dev");
        row.put("access_key", "sentinel-access");
        row.put("secret_key", "sentinel-secret");
        row.put("bucket_name", "sentinel-bucket");
        row.put("prefix", "videoops-agent/dev");
        row.put("endpoint", "sentinel-endpoint");
        row.put("domain_url", "");
        row.put("is_https", "N");
        row.put("region", "");
        row.put("access_policy", accessPolicy);
        return row;
    }
}
