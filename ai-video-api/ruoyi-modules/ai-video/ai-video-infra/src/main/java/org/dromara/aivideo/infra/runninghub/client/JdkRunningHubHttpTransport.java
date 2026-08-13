package org.dromara.aivideo.infra.runninghub.client;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** 不跟随重定向且按字节上限读取响应的 JDK HTTP 传输。 */
final class JdkRunningHubHttpTransport implements RunningHubHttpTransport {

    private static final int BUFFER_BYTES = 8192;

    private final HttpClient httpClient;

    JdkRunningHubHttpTransport() {
        this(HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build());
    }

    JdkRunningHubHttpTransport(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public Response send(HttpRequest request, int maxResponseBytes) throws IOException, InterruptedException {
        HttpResponse<InputStream> response = httpClient.send(
            request, HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream input = response.body()) {
            return new Response(response.statusCode(), readLimited(input, maxResponseBytes));
        }
    }

    private byte[] readLimited(InputStream input, int maxResponseBytes) throws IOException {
        if (maxResponseBytes <= 0) {
            throw new IllegalArgumentException("响应上限必须为正数");
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(maxResponseBytes, BUFFER_BYTES));
        byte[] buffer = new byte[BUFFER_BYTES];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > maxResponseBytes) {
                throw new IOException("RunningHub 响应超过大小限制");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }
}
