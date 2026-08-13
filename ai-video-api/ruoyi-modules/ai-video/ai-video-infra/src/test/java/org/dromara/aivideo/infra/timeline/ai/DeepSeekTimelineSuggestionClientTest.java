package org.dromara.aivideo.infra.timeline.ai;

import com.sun.net.httpserver.HttpServer;
import org.dromara.aivideo.infra.timeline.TimelineInfrastructureProperties;
import org.dromara.aivideo.timeline.dto.TimelineFancyTextSuggestionCommandDTO;
import org.dromara.aivideo.timeline.dto.TimelineImagePromptCommandDTO;
import org.dromara.aivideo.timeline.enums.FancyTextTemplateCode;
import org.dromara.aivideo.timeline.exception.TimelineExecutionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("dev")
class DeepSeekTimelineSuggestionClientTest {
    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void sendsOnlyBoundedImagePromptContextAndParsesStrictSuggestion() throws Exception {
        AtomicReference<String> request = new AtomicReference<>();
        startServer(request, content("""
            {"schemaVersion":"timeline-image-prompt-1","suggestions":[{
              "prompt":"cinematic city sunrise","negativePrompt":"watermark",
              "styleTags":["cinematic"],"reason":"matches the selected line"}]}
            """));

        var result = client().generateImagePrompt(imageCommand(), () -> false);

        assertThat(result.suggestions()).hasSize(1);
        assertThat(request.get()).contains("selected text", "cinematic")
            .doesNotContain("primaryAudio", "whisper", "subtitle", "project-1", "task-1");
    }

    @Test
    void rejectsUnknownFancyTemplateInsteadOfReturningApplicableSuggestion() throws Exception {
        startServer(new AtomicReference<>(), content("""
            {"schemaVersion":"timeline-fancy-text-1","suggestions":[{
              "sourceText":"selected","sourceStartOffset":4,"sourceEndOffset":12,
              "startMs":0,"durationMs":600,"templateCode":"unknown_template",
              "xRatio":0.5,"yRatio":0.5,"primaryColor":"#FFFFFF","accentColor":"#000000","reason":"x"}]}
            """));

        assertThatThrownBy(() -> client().suggestFancyText(fancyCommand(), () -> false))
            .isInstanceOf(TimelineExecutionException.class);
    }

    private DeepSeekTimelineSuggestionClient client() {
        TimelineInfrastructureProperties.Ai ai = new TimelineInfrastructureProperties.Ai();
        ai.setEnabled(true);
        ai.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        ai.setApiKey("test-key");
        ai.setModel("test-model");
        ai.setTimeout(Duration.ofSeconds(5));
        ai.setMaxResponseBytes(32 * 1024);
        return new DeepSeekTimelineSuggestionClient(ai, HttpClient.newHttpClient(), JsonMapper.builder().build());
    }

    private void startServer(AtomicReference<String> request, String response) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            request.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    private static String content(String modelContent) {
        return "{\"choices\":[{\"message\":{\"content\":\""
            + modelContent.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n") + "\"}}]}";
    }

    private static TimelineImagePromptCommandDTO imageCommand() {
        return new TimelineImagePromptCommandDTO("task-1", "project-1", "revision-1", 4, 17,
            "selected text", "before", "after", "9:16", "cinematic");
    }

    private static TimelineFancyTextSuggestionCommandDTO fancyCommand() {
        return new TimelineFancyTextSuggestionCommandDTO("task-1", "project-1", "revision-1", 4, 12,
            "selected", "before", "after", List.of(FancyTextTemplateCode.KEYWORD_POP));
    }
}
