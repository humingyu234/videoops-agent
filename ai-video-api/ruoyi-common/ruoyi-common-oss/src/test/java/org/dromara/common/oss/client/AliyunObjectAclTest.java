package org.dromara.common.oss.client;

import org.dromara.common.oss.config.OssClientConfig;
import org.dromara.common.oss.model.Options;
import org.dromara.common.oss.properties.OssProperties;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.model.ObjectCannedACL;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("dev")
class AliyunObjectAclTest {

    @Test
    void appliesPrivateObjectAclForAliyunPrivateStorage() {
        TestOssClient client = client("oss-cn-shanghai.aliyuncs.com", "0");

        PutObjectRequest request = client.requestWithConfiguredAcl();

        assertThat(request.acl()).isEqualTo(ObjectCannedACL.PRIVATE);
    }

    @Test
    void leavesNonAliyunUploadsWithoutCannedAcl() {
        TestOssClient client = client("127.0.0.1:9000", "0");

        PutObjectRequest request = client.requestWithConfiguredAcl();

        assertThat(request.acl()).isNull();
    }

    @Test
    void usesAliyunAtomicCreateOnlyHeader() {
        TestOssClient client = client("oss-cn-shanghai.aliyuncs.com", "0");

        PutObjectRequest request = client.requestWithConditionalUpload("*");

        assertThat(request.ifNoneMatch()).isNull();
        assertThat(request.overrideConfiguration()).isPresent();
        assertThat(request.overrideConfiguration().orElseThrow().headers())
            .containsEntry("x-oss-forbid-overwrite", java.util.List.of("true"));
    }

    @Test
    void usesStandardIfNoneMatchForOtherS3Providers() {
        TestOssClient client = client("127.0.0.1:9000", "0");

        PutObjectRequest request = client.requestWithConditionalUpload("*");

        assertThat(request.ifNoneMatch()).isEqualTo("*");
        assertThat(request.overrideConfiguration()).isEmpty();
    }

    private TestOssClient client(String endpoint, String accessPolicy) {
        OssProperties properties = new OssProperties();
        properties.setEndpoint(endpoint);
        properties.setIsHttps("Y");
        properties.setAccessPolicy(accessPolicy);
        return new TestOssClient(OssClientConfig.formProperties(properties));
    }

    private static final class TestOssClient extends AbstractOssClientImpl {

        private TestOssClient(OssClientConfig config) {
            super("test", config);
        }

        @Override
        void doInitialize() {
            // Request construction is exercised without opening a network client.
        }

        private PutObjectRequest requestWithConfiguredAcl() {
            PutObjectRequest.Builder builder = PutObjectRequest.builder()
                .bucket("bucket")
                .key("ai-video/portraits/test.png");
            applyConfiguredObjectAcl(builder);
            return builder.build();
        }

        private PutObjectRequest requestWithConditionalUpload(String ifNoneMatch) {
            PutObjectRequest.Builder builder = PutObjectRequest.builder()
                .bucket("bucket")
                .key("ai-video/renders/test.mp4");
            applyConditionalUpload(builder, Options.builder().setIfNoneMatch(ifNoneMatch));
            return builder.build();
        }
    }
}
