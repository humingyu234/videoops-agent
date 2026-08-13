package org.dromara.aivideo.infra.digitalhuman;

import org.dromara.common.core.exception.ServiceException;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedTrustManager;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.Socket;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

final class DigitalHumanHttpSupport {

    private DigitalHumanHttpSupport() {
    }

    static HttpClient client(String caCertificate) {
        return client(caCertificate, false);
    }

    static HttpClient client(String caCertificate, boolean insecureSkipTlsVerify) {
        try {
            HttpClient.Builder builder = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10));
            if (insecureSkipTlsVerify) {
                builder.sslContext(insecureSslContext());
                SSLParameters parameters = new SSLParameters();
                parameters.setEndpointIdentificationAlgorithm("");
                builder.sslParameters(parameters);
            } else if (hasText(caCertificate)) {
                builder.sslContext(sslContext(Path.of(caCertificate)));
            }
            return builder.build();
        } catch (Exception exception) {
            throw new ServiceException("数字人供应商证书配置无效");
        }
    }

    private static SSLContext insecureSslContext() throws Exception {
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, new X509ExtendedTrustManager[] {insecureTrustManager()}, new SecureRandom());
        return context;
    }

    static X509ExtendedTrustManager insecureTrustManager() {
        return new X509ExtendedTrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) {
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) {
            }

            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket) {
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket) {
            }

            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine) {
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine) {
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        };
    }
    static URI endpoint(String baseUrl, String path) {
        return endpoint(baseUrl, path, List.of());
    }

    static URI endpoint(String baseUrl, String path, Collection<String> insecureHttpAllowedHosts) {
        try {
            URI base = URI.create(baseUrl);
            if (!("http".equalsIgnoreCase(base.getScheme()) || "https".equalsIgnoreCase(base.getScheme()))
                || base.getHost() == null || base.getUserInfo() != null || base.getQuery() != null
                || base.getFragment() != null) {
                throw new IllegalArgumentException();
            }
            if ("http".equalsIgnoreCase(base.getScheme()) && !isExplicitLoopback(base.getHost())
                && !isExplicitlyAllowedHost(base.getHost(), insecureHttpAllowedHosts)) {
                throw new IllegalArgumentException();
            }
            String normalized = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
            return URI.create(normalized + path);
        } catch (RuntimeException exception) {
            throw new ServiceException("数字人供应商地址配置无效");
        }
    }

    private static boolean isExplicitlyAllowedHost(String host, Collection<String> allowedHosts) {
        if (allowedHosts == null || allowedHosts.isEmpty()) {
            return false;
        }
        return allowedHosts.stream()
            .filter(DigitalHumanHttpSupport::hasText)
            .map(String::trim)
            .anyMatch(host::equalsIgnoreCase);
    }

    private static boolean isExplicitLoopback(String host) {
        String value = host.toLowerCase(java.util.Locale.ROOT);
        if ("localhost".equals(value) || "::1".equals(value) || "[::1]".equals(value)) {
            return true;
        }
        String[] octets = value.split("\\.", -1);
        if (octets.length != 4 || !"127".equals(octets[0])) {
            return false;
        }
        for (String octet : octets) {
            try {
                if (octet.isEmpty() || Integer.parseInt(octet) > 255) {
                    return false;
                }
            } catch (NumberFormatException exception) {
                return false;
            }
        }
        return true;
    }

    static void basic(HttpRequest.Builder request, String user, String password) {
        if (!hasText(user) && !hasText(password)) {
            return;
        }
        if (!hasText(user) || !hasText(password) || containsLineBreak(user) || containsLineBreak(password)) {
            throw new ServiceException("数字人供应商认证配置无效");
        }
        String value = Base64.getEncoder().encodeToString(
            (user + ':' + password).getBytes(StandardCharsets.UTF_8));
        request.header("Authorization", "Basic " + value);
    }

    static LimitedResponse sendLimited(HttpClient client, HttpRequest request, int maxBytes, String message)
        throws IOException, InterruptedException {
        if (maxBytes <= 0) {
            throw new ServiceException(message);
        }
        long startedAt = System.nanoTime();
        CompletableFuture<HttpResponse<byte[]>> exchange = client.sendAsync(
            request, response -> limitedBodySubscriber(response.headers(), maxBytes));
        try {
            HttpResponse<byte[]> response;
            if (request.timeout().isPresent()) {
                long timeoutNanos;
                try {
                    timeoutNanos = request.timeout().orElseThrow().toNanos();
                } catch (ArithmeticException exception) {
                    timeoutNanos = Long.MAX_VALUE;
                }
                long elapsedNanos = Math.max(0L, System.nanoTime() - startedAt);
                long remainingNanos = timeoutNanos - Math.min(timeoutNanos, elapsedNanos);
                if (remainingNanos <= 0L) {
                    throw new TimeoutException();
                }
                response = exchange.get(remainingNanos, TimeUnit.NANOSECONDS);
            } else {
                response = exchange.get();
            }
            return new LimitedResponse(response.statusCode(), response.headers(), response.body());
        } catch (TimeoutException exception) {
            exchange.cancel(true);
            throw new HttpTimeoutException("request timed out");
        } catch (InterruptedException exception) {
            exchange.cancel(true);
            throw exception;
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (hasResponseLimitCause(cause)) {
                throw new ServiceException(message);
            }
            if (cause instanceof IOException ioException) {
                throw ioException;
            }
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IOException(message, cause);
        }
    }

    private static HttpResponse.BodySubscriber<byte[]> limitedBodySubscriber(HttpHeaders headers, int maxBytes) {
        LimitedBodySubscriber subscriber = new LimitedBodySubscriber(maxBytes);
        try {
            if (headers.firstValue("Content-Length").isPresent()) {
                long declaredLength = headers.firstValueAsLong("Content-Length").orElseThrow();
                if (declaredLength < 0L || declaredLength > maxBytes) {
                    subscriber.reject();
                }
            }
        } catch (NumberFormatException exception) {
            subscriber.reject();
        }
        return subscriber;
    }

    private static boolean hasResponseLimitCause(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof ResponseLimitException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    static MultipartBody multipart(List<Part> parts) {
        String boundary = "----AiVideo" + UUID.randomUUID().toString().replace("-", "");
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            for (Part part : parts) {
                output.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.US_ASCII));
                String disposition = "Content-Disposition: form-data; name=\"" + part.name() + "\"";
                if (part.fileName() != null) {
                    disposition += "; filename=\"" + safeFileName(part.fileName()) + "\"";
                }
                output.write((disposition + "\r\n").getBytes(StandardCharsets.UTF_8));
                output.write(("Content-Type: " + part.mediaType() + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                output.write(part.content());
                output.write("\r\n".getBytes(StandardCharsets.US_ASCII));
            }
            output.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.US_ASCII));
            return new MultipartBody("multipart/form-data; boundary=" + boundary, output.toByteArray());
        } catch (IOException exception) {
            throw new ServiceException("构造数字人供应商请求失败");
        }
    }

    static Part text(String name, String value) {
        return new Part(name, null, "text/plain; charset=UTF-8", value.getBytes(StandardCharsets.UTF_8));
    }

    static Part file(String name, String fileName, String mediaType, byte[] content) {
        return new Part(name, fileName, mediaType, content);
    }

    static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static boolean containsLineBreak(String value) {
        return value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0;
    }

    private static String safeFileName(String value) {
        String normalized = value == null ? "upload.bin" : value.replace('\\', '/');
        normalized = normalized.substring(normalized.lastIndexOf('/') + 1)
            .replace('"', '_').replace('\r', '_').replace('\n', '_');
        return normalized.isBlank() ? "upload.bin" : normalized;
    }

    private static SSLContext sslContext(Path pemPath) throws Exception {
        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        Collection<? extends Certificate> certificates;
        try (InputStream input = Files.newInputStream(pemPath)) {
            certificates = factory.generateCertificates(input);
        }
        if (certificates.isEmpty()) {
            throw new IllegalArgumentException();
        }
        KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
        trustStore.load(null, null);
        int index = 0;
        for (Certificate certificate : certificates) {
            trustStore.setCertificateEntry("digital-human-ca-" + index++, certificate);
        }
        TrustManagerFactory managers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        managers.init(trustStore);
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, managers.getTrustManagers(), new SecureRandom());
        return context;
    }

    record Part(String name, String fileName, String mediaType, byte[] content) {
    }

    record MultipartBody(String contentType, byte[] content) {
    }

    record LimitedResponse(int statusCode, HttpHeaders headers, byte[] body) {
    }

    private static final class LimitedBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {

        private final int maxBytes;
        private final ByteArrayOutputStream content;
        private final CompletableFuture<byte[]> body = new CompletableFuture<>();
        private Flow.Subscription subscription;
        private long receivedBytes;

        private LimitedBodySubscriber(int maxBytes) {
            this.maxBytes = maxBytes;
            this.content = new ByteArrayOutputStream(Math.min(maxBytes, 8192));
        }

        @Override
        public CompletionStage<byte[]> getBody() {
            return body;
        }

        @Override
        public void onSubscribe(Flow.Subscription value) {
            if (subscription != null) {
                value.cancel();
                return;
            }
            subscription = value;
            if (body.isDone()) {
                value.cancel();
            } else {
                value.request(1);
            }
        }

        @Override
        public void onNext(List<ByteBuffer> buffers) {
            if (body.isDone()) {
                subscription.cancel();
                return;
            }
            for (ByteBuffer buffer : buffers) {
                int length = buffer.remaining();
                if (length > maxBytes - receivedBytes) {
                    reject();
                    return;
                }
                byte[] chunk = new byte[length];
                buffer.get(chunk);
                content.writeBytes(chunk);
                receivedBytes += length;
            }
            subscription.request(1);
        }

        @Override
        public void onError(Throwable throwable) {
            body.completeExceptionally(throwable);
        }

        @Override
        public void onComplete() {
            body.complete(content.toByteArray());
        }

        private void reject() {
            if (subscription != null) {
                subscription.cancel();
            }
            body.completeExceptionally(new ResponseLimitException());
        }
    }

    private static final class ResponseLimitException extends RuntimeException {
    }
}
