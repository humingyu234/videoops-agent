package org.dromara.aivideo.infra.voice;

import lombok.Data;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.UUID;

@Data
@Component
@ConfigurationProperties(prefix = "aivideo.whisper")
public class WhisperProperties {
    private boolean enabled = true;
    private String baseUrl = "http://127.0.0.1:18181";
    private String internalToken = "";
    private String allowedHosts = "127.0.0.1,localhost";
    private String workerId = "java-" + UUID.randomUUID();
    private Duration timeout = Duration.ofMinutes(10);

    public URI validatedBaseUri() {
        URI uri = URI.create(baseUrl);
        String host = uri.getHost();
        boolean hostAllowed = host != null
            && Arrays.stream(allowedHosts.split(","))
            .map(String::trim)
            .anyMatch(item -> item.equalsIgnoreCase(host));
        if (!"http".equalsIgnoreCase(uri.getScheme()) || uri.getPort() != 18181 || !hostAllowed) {
            throw new ServiceException("Whisper Worker 地址不在允许列表中", 46405);
        }
        return uri;
    }
}
