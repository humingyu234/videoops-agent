package org.dromara.aivideo.infra.voice.service.impl;

import com.sun.net.httpserver.HttpServer;
import org.dromara.aivideo.asset.dto.AssetDTO;
import org.dromara.aivideo.infra.voice.WhisperProperties;
import org.dromara.aivideo.voice.dto.VoiceTranscriptCueDTO;
import org.dromara.aivideo.voice.dto.VoiceTranscriptionLeaseDTO;
import org.dromara.aivideo.voice.dto.VoiceTranscriptionResultDTO;
import org.dromara.aivideo.voice.dto.WhisperTranscriptionInputDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.ByteArrayInputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("dev")
class WhisperTranscriptionServiceImplTest {
    private HttpServer server;
    private AtomicReference<String> requestBody;
    private WhisperTranscriptionServiceImpl service;

    @BeforeEach
    void setUp() throws Exception {
        requestBody = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/v1/transcriptions", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = """
                {"requestId":"1:1:1","text":"微信公众号","language":"zh","durationMillis":1000,
                 "words":[{"text":"微信","startMillis":120,"endMillis":480}]}
                """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        WhisperProperties properties = new WhisperProperties();
        properties.setInternalToken("test-token");
        properties.setTimeout(Duration.ofSeconds(5));
        RestClient restClient = RestClient.builder()
            .baseUrl("http://127.0.0.1:" + server.getAddress().getPort()).build();
        service = new WhisperTranscriptionServiceImpl(properties, restClient);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void requestsAndMapsWhisperWordTimestamps() {
        VoiceTranscriptionLeaseDTO lease = new VoiceTranscriptionLeaseDTO(
            "1", "2", 1L, "personal:1", 1L, "1:1:1", "worker", 1L, 1);
        AssetDTO asset = new AssetDTO("2", "ready", null, "sample.wav", "audio/wav",
            "wav", null, null, 4L, null);

        VoiceTranscriptionResultDTO result = service.transcribe(lease, asset,
            new ByteArrayInputStream(new byte[]{1, 2, 3, 4}));

        assertThat(requestBody.get()).contains("name=\"wordTimestamps\"").contains("true");
        assertThat(result.transcriptTimeline()).containsExactly(
            new VoiceTranscriptCueDTO("微信", 120L, 480L));
    }

    @Test
    void supportsTimelineGenericInputWithoutConstructingLegacyLease() {
        VoiceTranscriptionResultDTO result = service.transcribe(
            new WhisperTranscriptionInputDTO("1:1:1", "timeline-primary-audio.wav", "audio/wav", 4L),
            new ByteArrayInputStream(new byte[]{1, 2, 3, 4}));

        assertThat(requestBody.get()).contains("timeline-primary-audio.wav")
            .contains("name=\"requestId\"").contains("1:1:1");
        assertThat(result.transcriptTimeline()).hasSize(1);
    }
}
