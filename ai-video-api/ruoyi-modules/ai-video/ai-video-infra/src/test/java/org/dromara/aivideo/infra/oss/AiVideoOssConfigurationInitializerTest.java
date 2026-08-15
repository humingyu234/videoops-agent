package org.dromara.aivideo.infra.oss;

import org.dromara.common.oss.client.OssClient;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("dev")
@ExtendWith(OutputCaptureExtension.class)
class AiVideoOssConfigurationInitializerTest {

    @Test
    void initializesProjectClientInProcess() throws Exception {
        AiVideoOssProperties properties = privateProjectProperties();

        try (OssClient client = new AiVideoOssConfigurationInitializer(properties).initialize()) {
            assertThat(client.clientId()).isEqualTo("videoops-agent-dev");
            assertThat(client.config().prefix()).contains("videoops-agent/dev");
            assertThat(client.isInitialized()).isTrue();
        }
    }

    @Test
    void rejectsPublicObjectPolicyForPrivateCreatorAssets() {
        AiVideoOssProperties properties = privateProjectProperties();
        properties.setAccessPolicy("2");

        assertThatThrownBy(() -> new AiVideoOssConfigurationInitializer(properties).initialize())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("private");
    }

    @Test
    void doesNotExposeCredentialsThroughPropertiesToString() {
        AiVideoOssProperties properties = privateProjectProperties();

        assertThat(properties.toString())
            .doesNotContain("sentinel-access-key")
            .doesNotContain("sentinel-secret-key");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "ai-video", "videoops-agent/dev/"})
    void rejectsEmptyOrLegacyNamespacesWithoutPublishingThem(String prefix) {
        AiVideoOssProperties properties = privateProjectProperties();
        properties.setPrefix(prefix);

        assertThatThrownBy(() -> new AiVideoOssConfigurationInitializer(properties).initialize())
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void omitsPrivateStorageDetailsFromInitializationLogs(CapturedOutput output) throws Exception {
        AiVideoOssProperties properties = privateProjectProperties();

        try (OssClient ignored = new AiVideoOssConfigurationInitializer(properties).initialize()) {
            assertThat(ignored.isInitialized()).isTrue();
        }

        assertThat(output).doesNotContain("sentinel-endpoint", "sentinel-bucket",
            "sentinel-access-key", "sentinel-secret-key");
    }

    private AiVideoOssProperties privateProjectProperties() {
        AiVideoOssProperties properties = new AiVideoOssProperties();
        properties.setEnabled(true);
        properties.setConfigKey("videoops-agent-dev");
        properties.setEndpoint("sentinel-endpoint");
        properties.setDomainUrl("");
        properties.setPrefix("videoops-agent/dev");
        properties.setAccessKey("sentinel-access-key");
        properties.setSecretKey("sentinel-secret-key");
        properties.setBucketName("sentinel-bucket");
        properties.setRegion("cn-shanghai");
        properties.setHttps(true);
        properties.setAccessPolicy("0");
        return properties;
    }
}
