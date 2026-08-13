package org.dromara.common.oss.client;

import org.dromara.common.oss.config.OssClientConfig;
import org.dromara.common.oss.properties.OssProperties;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("dev")
class OssBufferedDownloadTest {

    @Test
    void buffersTheRemoteBodyAndDeletesTheTemporaryFileOnClose() throws Exception {
        TestOssClient client = client();
        AtomicReference<Path> downloadedFile = new AtomicReference<>();
        when(client.s3AsyncClient.getObject(any(GetObjectRequest.class), any(Path.class)))
            .thenAnswer(invocation -> {
                Path target = invocation.getArgument(1);
                downloadedFile.set(target);
                Files.write(target, new byte[] {1, 2, 3});
                return CompletableFuture.completedFuture(GetObjectResponse.builder().contentLength(3L).build());
            });

        try (InputStream input = client.doCustomBufferedDownload(
            request -> request.bucket("bucket").key("creation/input.mp4"), Duration.ofSeconds(30))) {
            assertThat(input.readAllBytes()).containsExactly(1, 2, 3);
            assertThat(downloadedFile.get()).exists();
        }

        assertThat(downloadedFile.get()).doesNotExist();
        assertThat(downloadedFile.get().getParent()).doesNotExist();
    }

    @Test
    void cancelsTheRemoteDownloadAndDeletesTemporaryFilesOnTimeout() {
        TestOssClient client = client();
        AtomicReference<Path> downloadedFile = new AtomicReference<>();
        CompletableFuture<GetObjectResponse> download = new CompletableFuture<>();
        when(client.s3AsyncClient.getObject(any(GetObjectRequest.class), any(Path.class)))
            .thenAnswer(invocation -> {
                downloadedFile.set(invocation.getArgument(1));
                return download;
            });

        assertThatThrownBy(() -> client.doCustomBufferedDownload(
            request -> request.bucket("bucket").key("creation/input.mp4"), Duration.ofMillis(50)))
            .isInstanceOf(RuntimeException.class);

        assertThat(download).isCancelled();
        assertThat(downloadedFile.get()).doesNotExist();
        assertThat(downloadedFile.get().getParent()).doesNotExist();
    }

    private static TestOssClient client() {
        OssProperties properties = new OssProperties();
        properties.setEndpoint("127.0.0.1:9000");
        properties.setIsHttps("N");
        TestOssClient client = new TestOssClient(OssClientConfig.formProperties(properties));
        client.s3AsyncClient = mock(S3AsyncClient.class);
        return client;
    }

    private static final class TestOssClient extends AbstractOssClientImpl {

        private TestOssClient(OssClientConfig config) {
            super("buffer-test", config);
        }

        @Override
        void doInitialize() {
            // Network client is injected by the test.
        }
    }
}
