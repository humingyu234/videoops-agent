package org.dromara.aivideo.infra.oss;

import org.dromara.common.oss.properties.OssProperties;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@Tag("dev")
class AiVideoOssConfigurationInitializerTest {

    @Test
    void publishesPrivateAliyunConfigurationFromLocalDevelopmentYaml() {
        AiVideoOssProperties properties = privateAliyunProperties();
        AiVideoOssConfigurationCache cache = mock(AiVideoOssConfigurationCache.class);
        OssProperties expected = new OssProperties();
        expected.setEndpoint("oss-cn-shanghai.aliyuncs.com");
        expected.setDomainUrl("");
        expected.setPrefix("ai-video");
        expected.setAccessKey("configured-access-key");
        expected.setSecretKey("configured-secret-key");
        expected.setBucketName("qc-test-01");
        expected.setRegion("cn-shanghai");
        expected.setIsHttps("Y");
        expected.setAccessPolicy("0");

        new AiVideoOssConfigurationInitializer(properties, cache).initialize();

        verify(cache).put("aliyun", expected);
    }

    @Test
    void rejectsPublicObjectPolicyForPrivateCreatorAssets() {
        AiVideoOssProperties properties = privateAliyunProperties();
        properties.setAccessPolicy("2");
        AiVideoOssConfigurationCache cache = mock(AiVideoOssConfigurationCache.class);

        assertThatThrownBy(() -> new AiVideoOssConfigurationInitializer(properties, cache).initialize())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("private");
        verifyNoInteractions(cache);
    }

    @Test
    void doesNotExposeCredentialsThroughPropertiesToString() {
        AiVideoOssProperties properties = privateAliyunProperties();

        assertThat(properties.toString())
            .doesNotContain("configured-access-key")
            .doesNotContain("configured-secret-key");
    }

    private AiVideoOssProperties privateAliyunProperties() {
        AiVideoOssProperties properties = new AiVideoOssProperties();
        properties.setEnabled(true);
        properties.setConfigKey("aliyun");
        properties.setEndpoint("oss-cn-shanghai.aliyuncs.com");
        properties.setDomainUrl("");
        properties.setPrefix("ai-video");
        properties.setAccessKey("configured-access-key");
        properties.setSecretKey("configured-secret-key");
        properties.setBucketName("qc-test-01");
        properties.setRegion("cn-shanghai");
        properties.setHttps(true);
        properties.setAccessPolicy("0");
        return properties;
    }
}
