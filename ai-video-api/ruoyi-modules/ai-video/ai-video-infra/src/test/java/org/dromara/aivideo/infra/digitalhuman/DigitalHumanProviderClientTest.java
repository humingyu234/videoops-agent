package org.dromara.aivideo.infra.digitalhuman;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.dromara.aivideo.digitalhuman.dto.DigitalHumanVideoPollDTO;
import org.dromara.aivideo.digitalhuman.dto.DigitalHumanVideoProviderStatus;
import org.dromara.aivideo.digitalhuman.dto.DigitalHumanVideoSubmitDTO;
import org.dromara.aivideo.digitalhuman.dto.VoiceSynthesisRequestDTO;
import org.dromara.aivideo.digitalhuman.dto.VoiceSynthesisResultDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.net.http.HttpRequest;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 数字人供应商客户端的真实 HTTP 边界测试。
 */
@Tag("dev")
class DigitalHumanProviderClientTest {

    private FakeProviderServer server;

    @BeforeEach
    void startServer() throws IOException {
        server = new FakeProviderServer();
    }

    @AfterEach
    void stopServer() {
        server.close();
    }

    @Test
    void permitsPlainHttpOnlyForLoopbackOrExplicitAuthority() {
        assertThat(DigitalHumanHttpSupport.endpoint("http://127.0.0.1:8189", "/health").getHost())
            .isEqualTo("127.0.0.1");
        assertThat(DigitalHumanHttpSupport.endpoint("http://localhost:8189", "/health").getHost())
            .isEqualTo("localhost");
        assertThat(DigitalHumanHttpSupport.endpoint("https://provider.example", "/health").getScheme())
            .isEqualTo("https");
        assertThat(DigitalHumanHttpSupport.endpoint("http://36.133.55.206:8189", "/health",
            List.of("36.133.55.206")).getHost()).isEqualTo("36.133.55.206");

        assertThatThrownBy(() -> DigitalHumanHttpSupport.endpoint(
            "http://192.0.2.10:39000", "/health"))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("地址配置无效");
        assertThatThrownBy(() -> DigitalHumanHttpSupport.endpoint(
            "http://36.133.55.206:8189", "/health", List.of("192.0.2.10")))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("地址配置无效");
    }

    @Test
    void rejectsProviderBodyThatExceedsTheConfiguredHardLimit() {
        assertThat(IndexTts2Client.MAX_RESPONSE_BYTES).isEqualTo(32 * 1024 * 1024);
        assertThat(ComfyUiClient.MAX_JSON_RESPONSE_BYTES).isEqualTo(1024 * 1024);
        assertThat(ComfyUiClient.MAX_VIDEO_RESPONSE_BYTES).isEqualTo(128 * 1024 * 1024);
        server.enqueueChunked(200, "application/octet-stream", new byte[] {1, 2, 3, 4, 5});
        HttpRequest request = HttpRequest.newBuilder()
            .uri(DigitalHumanHttpSupport.endpoint(server.baseUrl(), "/oversized"))
            .GET()
            .build();

        assertThatThrownBy(() -> DigitalHumanHttpSupport.sendLimited(
            DigitalHumanHttpSupport.client(null), request, 4, "供应商响应过大"))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("供应商响应过大");
    }

    @Test
    void rejectsDeclaredProviderBodyBeforeReadingIt() {
        server.enqueue(200, "application/octet-stream", new byte[] {1, 2, 3, 4, 5});
        HttpRequest request = HttpRequest.newBuilder()
            .uri(DigitalHumanHttpSupport.endpoint(server.baseUrl(), "/declared-oversized"))
            .GET()
            .build();

        assertThatThrownBy(() -> DigitalHumanHttpSupport.sendLimited(
            DigitalHumanHttpSupport.client(null), request, 4, "供应商响应过大"))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("供应商响应过大");
    }

    @Test
    void enforcesRequestTimeoutUntilTheProviderBodyIsFullyRead() {
        server.enqueueSlowBody(200, "application/octet-stream", new byte[] {1, 2}, 500);
        HttpRequest request = HttpRequest.newBuilder()
            .uri(DigitalHumanHttpSupport.endpoint(server.baseUrl(), "/slow-body"))
            .timeout(Duration.ofMillis(100))
            .GET()
            .build();

        assertThatThrownBy(() -> DigitalHumanHttpSupport.sendLimited(
            DigitalHumanHttpSupport.client(null), request, 8, "供应商响应超时"))
            .isInstanceOf(HttpTimeoutException.class);
    }

    @Test
    void sendsIndexTtsMultipartAndBothAuthenticationHeadersThenReturnsWav() throws Exception {
        byte[] wav = "RIFF-unit-test-WAVE".getBytes(StandardCharsets.US_ASCII);
        server.enqueue(200, "audio/wav", wav);
        DigitalHumanProviderProperties.IndexTts2 properties = indexTtsProperties();
        IndexTts2Client client = new IndexTts2Client(properties);

        VoiceSynthesisResultDTO result = client.synthesize(new VoiceSynthesisRequestDTO(
            "approved script", "reference.wav", "audio/wav",
            "REFERENCE-AUDIO".getBytes(StandardCharsets.US_ASCII)));

        RecordedRequest request = server.takeRequest();
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.path()).isEqualTo("/v1/indextts2/clone");
        assertThat(request.header("Content-Type")).startsWith("multipart/form-data; boundary=");
        assertThat(request.header("X-API-Key")).isEqualTo("unit-test-api-key");
        assertThat(request.header("Authorization")).isEqualTo(basic("voice-user", "voice-password"));
        assertThat(request.bodyText())
            .contains("name=\"text\"")
            .contains("approved script")
            .contains("name=\"reference_audio\"; filename=\"reference.wav\"")
            .contains("Content-Type: audio/wav")
            .contains("REFERENCE-AUDIO");
        assertThat(result.audio()).containsExactly(wav);
        assertThat(result.mediaType()).isEqualTo("audio/wav");
        assertThat(result.fileExtension()).isEqualTo("wav");
    }

    @Test
    void doesNotLeakIndexTtsResponseOrCredentialsWhenProviderFails() {
        server.enqueue(502, "text/plain",
            "upstream exposed unit-test-api-key voice-password".getBytes(StandardCharsets.UTF_8));
        IndexTts2Client client = new IndexTts2Client(indexTtsProperties());

        assertThatThrownBy(() -> client.synthesize(new VoiceSynthesisRequestDTO(
            "approved script", "reference.wav", "audio/wav", new byte[] {1, 2, 3})))
            .isInstanceOf(RuntimeException.class)
            .hasMessageNotContaining("unit-test-api-key")
            .hasMessageNotContaining("voice-password")
            .hasMessageNotContaining("upstream exposed");
    }

    @Test
    void submitsNativeComfyWorkflowPollsHistoryAndDownloadsTheSelectedMp4() throws Exception {
        byte[] mp4 = "unit-test-mp4".getBytes(StandardCharsets.US_ASCII);
        server.enqueue(200, "application/json", """
            {
              "id":"8b7a9a57-2303-4ef5-9fc2-bf41713bd1fc",
              "nodes":[
                {
                  "id":284,"type":"LoadImage","mode":0,
                  "inputs":[
                    {"name":"image","type":"COMBO","link":null,"widget":{"name":"image"}},
                    {"name":"upload","type":"IMAGEUPLOAD","link":null,"widget":{"name":"upload"}}
                  ],
                  "widgets_values":["old-portrait.png","image"]
                },
                {
                  "id":125,"type":"LoadAudio","mode":0,
                  "inputs":[
                    {"name":"audio","type":"COMBO","link":null,"widget":{"name":"audio"}},
                    {"name":"audioUI","type":"AUDIO_UI","link":null,"widget":{"name":"audioUI"}},
                    {"name":"upload","type":"AUDIOUPLOAD","link":null,"widget":{"name":"upload"}}
                  ],
                  "widgets_values":["old-voice.wav",null,null]
                },
                {
                  "id":400,"type":"PortraitProducer","mode":0,"inputs":[],"widgets_values":[]
                },
                {
                  "id":308,"type":"JWInteger","mode":0,
                  "inputs":[{"name":"value","type":"INT","link":null,"widget":{"name":"value"}}],
                  "widgets_values":[3425]
                },
                {
                  "id":128,"type":"WanVideoSampler","mode":0,
                  "inputs":[
                    {"name":"steps","type":"INT","link":null,"widget":{"name":"steps"}},
                    {"name":"cfg","type":"FLOAT","link":null,"widget":{"name":"cfg"}},
                    {"name":"shift","type":"FLOAT","link":null,"widget":{"name":"shift"}},
                    {"name":"seed","type":"INT","link":null,"widget":{"name":"seed"}},
                    {"name":"force_offload","type":"BOOLEAN","link":null,"widget":{"name":"force_offload"}},
                    {"name":"scheduler","type":"COMBO","link":null,"widget":{"name":"scheduler"}}
                  ],
                  "widgets_values":[4,1.0,11.0,642471316954969,"fixed",false,"dpm++_sde"]
                },
                {
                  "id":131,"type":"VHS_VideoCombine","mode":0,
                  "inputs":[
                    {"name":"images","type":"IMAGE","link":501},
                    {"name":"audio","type":"AUDIO","link":502},
                    {"name":"frame_rate","type":"FLOAT","link":null,"widget":{"name":"frame_rate"}},
                    {"name":"save_output","type":"BOOLEAN","link":null,"widget":{"name":"save_output"}},
                    {"name":"pix_fmt","type":"COMBO","link":null,"widget":{"name":"pix_fmt"}},
                    {"name":"crf","type":"INT","link":null,"widget":{"name":"crf"}},
                    {"name":"save_metadata","type":"BOOLEAN","link":null,"widget":{"name":"save_metadata"}},
                    {"name":"trim_to_audio","type":"BOOLEAN","link":null,"widget":{"name":"trim_to_audio"}}
                  ],
                  "widgets_values":{"frame_rate":25,"save_output":true,"pix_fmt":"yuv420p",
                    "crf":19,"save_metadata":true,"trim_to_audio":false,
                    "videopreview":{"params":{"filename":"stale.mp4"}}}
                }
              ],
              "links":[
                [501,400,0,131,0,"IMAGE"],
                [502,125,0,131,1,"AUDIO"]
              ]
            }
            """.getBytes(StandardCharsets.UTF_8));
        server.enqueue(200, "application/json", """
            {"name":"portrait-upload.png","subfolder":"digital-human","type":"input"}
            """.getBytes(StandardCharsets.UTF_8));
        server.enqueue(200, "application/json", """
            {"name":"voice-upload.wav","subfolder":"digital-human","type":"input"}
            """.getBytes(StandardCharsets.UTF_8));
        server.enqueue(200, "application/json", """
            {"prompt_id":"prompt-42","number":7,"node_errors":{}}
            """.getBytes(StandardCharsets.UTF_8));
        server.enqueue(200, "application/json", """
            {
              "prompt-42": {
                "status": {"status_str":"success","completed":true,"messages":[]},
                "outputs": {
                  "101": {"images":[{"filename":"preview.png","subfolder":"preview","type":"temp"}]},
                  "307": {"gifs":[{"filename":"result clip.mp4","subfolder":"digital human/final","type":"output","format":"video/h264-mp4"}]}
                }
              }
            }
            """.getBytes(StandardCharsets.UTF_8));
        server.enqueue(200, "video/mp4", mp4);
        ComfyUiClient client = new ComfyUiClient(comfyProperties());

        String promptId = client.submit(new DigitalHumanVideoSubmitDTO(
            "portrait.png", "image/png", "PORTRAIT".getBytes(StandardCharsets.US_ASCII),
            "voice.wav", "audio/wav", oneSecondWave()));
        DigitalHumanVideoPollDTO result = client.poll(promptId);

        RecordedRequest workflow = server.takeRequest();
        assertThat(workflow.method()).isEqualTo("GET");
        assertThat(workflow.path()).isEqualTo("/api/userdata/workflows/数字人口播.json");
        assertThat(workflow.header("Authorization")).isEqualTo(basic("comfy-user", "comfy-password"));

        RecordedRequest portraitUpload = server.takeRequest();
        assertThat(portraitUpload.method()).isEqualTo("POST");
        assertThat(portraitUpload.path()).isEqualTo("/upload/image");
        assertThat(portraitUpload.header("Authorization")).isEqualTo(basic("comfy-user", "comfy-password"));
        assertThat(portraitUpload.bodyText())
            .contains("name=\"image\"; filename=\"digital-human-portrait-")
            .contains("Content-Type: image/png")
            .contains("PORTRAIT")
            .contains("name=\"subfolder\"")
            .contains("digital-human")
            .contains("name=\"type\"")
            .contains("input");

        RecordedRequest audioUpload = server.takeRequest();
        assertThat(audioUpload.method()).isEqualTo("POST");
        assertThat(audioUpload.path()).isEqualTo("/upload/image");
        assertThat(audioUpload.header("Authorization")).isEqualTo(basic("comfy-user", "comfy-password"));
        assertThat(audioUpload.bodyText())
            .contains("name=\"image\"; filename=\"digital-human-audio-")
            .contains("Content-Type: audio/wav")
            .contains("RIFF")
            .contains("WAVE");

        RecordedRequest submit = server.takeRequest();
        assertThat(submit.method()).isEqualTo("POST");
        assertThat(submit.path()).isEqualTo("/prompt");
        assertThat(submit.header("Content-Type")).startsWith("application/json");
        assertThat(submit.header("Authorization")).isEqualTo(basic("comfy-user", "comfy-password"));
        JsonNode prompt = JsonMapper.builder().build().readTree(submit.body()).path("prompt");
        assertThat(prompt.path("284").path("class_type").asString()).isEqualTo("LoadImage");
        assertThat(prompt.path("284").path("inputs").path("image").asString())
            .isEqualTo("digital-human/portrait-upload.png");
        assertThat(prompt.path("284").path("inputs").has("upload")).isFalse();
        assertThat(prompt.path("125").path("inputs").path("audio").asString())
            .isEqualTo("digital-human/voice-upload.wav");
        assertThat(prompt.path("125").path("inputs").has("audioUI")).isFalse();
        assertThat(prompt.path("131").path("inputs").path("images").toString()).isEqualTo("[\"400\",0]");
        assertThat(prompt.path("131").path("inputs").path("frame_rate").asInt()).isEqualTo(25);
        assertThat(prompt.path("131").path("inputs").path("pix_fmt").asString()).isEqualTo("yuv420p");
        assertThat(prompt.path("131").path("inputs").path("crf").asInt()).isEqualTo(19);
        assertThat(prompt.path("131").path("inputs").path("save_metadata").asBoolean()).isTrue();
        assertThat(prompt.path("131").path("inputs").path("trim_to_audio").asBoolean()).isFalse();
        assertThat(prompt.path("131").path("inputs").has("videopreview")).isFalse();
        assertThat(prompt.path("128").path("inputs").path("seed").asLong()).isEqualTo(642471316954969L);
        assertThat(prompt.path("128").path("inputs").path("force_offload").asBoolean()).isFalse();
        assertThat(prompt.path("128").path("inputs").path("scheduler").asString()).isEqualTo("dpm++_sde");
        assertThat(prompt.path("128").toString()).doesNotContain("fixed");
        assertThat(prompt.path("308").path("inputs").path("value").asInt()).isEqualTo(25);

        RecordedRequest history = server.takeRequest();
        assertThat(history.method()).isEqualTo("GET");
        assertThat(history.path()).isEqualTo("/history/prompt-42");
        assertThat(history.header("Authorization")).isEqualTo(basic("comfy-user", "comfy-password"));

        RecordedRequest view = server.takeRequest();
        assertThat(view.method()).isEqualTo("GET");
        assertThat(view.path()).isEqualTo("/view");
        assertThat(decodeQuery(view.rawQuery())).containsEntry("filename", "result clip.mp4")
            .containsEntry("subfolder", "digital human/final")
            .containsEntry("type", "output");
        assertThat(view.header("Authorization")).isEqualTo(basic("comfy-user", "comfy-password"));

        assertThat(promptId).isEqualTo("prompt-42");
        assertThat(result.status()).isEqualTo(DigitalHumanVideoProviderStatus.SUCCEEDED);
        assertThat(result.progress()).isEqualTo(100);
        assertThat(result.video()).containsExactly(mp4);
        assertThat(result.mediaType()).isEqualTo("video/mp4");
        assertThat(result.fileExtension()).isEqualTo("mp4");
        assertThat(result.failureCode()).isNull();
    }

    @Test
    void mapsEmptyHistoryToRunningAndTerminalErrorToFailedWithoutDownloading() throws Exception {
        server.enqueue(200, "application/json", "{}".getBytes(StandardCharsets.UTF_8));
        server.enqueue(200, "application/json", """
            {"prompt-42":{"status":{"status_str":"error","completed":false},"outputs":{}}}
            """.getBytes(StandardCharsets.UTF_8));
        ComfyUiClient client = new ComfyUiClient(comfyProperties());

        DigitalHumanVideoPollDTO running = client.poll("prompt-42");
        DigitalHumanVideoPollDTO failed = client.poll("prompt-42");

        assertThat(running.status()).isEqualTo(DigitalHumanVideoProviderStatus.RUNNING);
        assertThat(running.progress()).isBetween(1, 95);
        assertThat(running.video()).isNull();
        assertThat(failed.status()).isEqualTo(DigitalHumanVideoProviderStatus.FAILED);
        assertThat(failed.failureCode()).isEqualTo("COMFYUI_JOB_FAILED");
        assertThat(server.requestCount()).isEqualTo(2);
    }

    private DigitalHumanProviderProperties.IndexTts2 indexTtsProperties() {
        DigitalHumanProviderProperties.IndexTts2 properties = new DigitalHumanProviderProperties.IndexTts2();
        properties.setBaseUrl(server.baseUrl());
        properties.setApiKey("unit-test-api-key");
        properties.setBasicUser("voice-user");
        properties.setBasicPassword("voice-password");
        return properties;
    }

    private DigitalHumanProviderProperties.ComfyUi comfyProperties() {
        DigitalHumanProviderProperties.ComfyUi properties = new DigitalHumanProviderProperties.ComfyUi();
        properties.setBaseUrl(server.baseUrl());
        properties.setBasicUser("comfy-user");
        properties.setBasicPassword("comfy-password");
        properties.setWorkflowFile("数字人口播.json");
        properties.setWorkflowId("8b7a9a57-2303-4ef5-9fc2-bf41713bd1fc");
        return properties;
    }

    private static String basic(String user, String password) {
        String credentials = user + ':' + password;
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] oneSecondWave() {
        int sampleRate = 16_000;
        int dataLength = sampleRate * 2;
        ByteBuffer buffer = ByteBuffer.allocate(44 + dataLength).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put("RIFF".getBytes(StandardCharsets.US_ASCII));
        buffer.putInt(36 + dataLength);
        buffer.put("WAVEfmt ".getBytes(StandardCharsets.US_ASCII));
        buffer.putInt(16);
        buffer.putShort((short) 1);
        buffer.putShort((short) 1);
        buffer.putInt(sampleRate);
        buffer.putInt(sampleRate * 2);
        buffer.putShort((short) 2);
        buffer.putShort((short) 16);
        buffer.put("data".getBytes(StandardCharsets.US_ASCII));
        buffer.putInt(dataLength);
        return buffer.array();
    }

    private static Map<String, String> decodeQuery(String rawQuery) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String pair : rawQuery.split("&")) {
            String[] parts = pair.split("=", 2);
            values.put(URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
                URLDecoder.decode(parts.length == 2 ? parts[1] : "", StandardCharsets.UTF_8));
        }
        return values;
    }

    private record StubResponse(int status, String contentType, byte[] body,
                                long bodyDelayMillis, boolean chunked) {
    }

    private record RecordedRequest(String method, String path, String rawQuery,
                                   Map<String, List<String>> headers, byte[] body) {

        String header(String name) {
            return headers.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(name))
                .map(Map.Entry::getValue)
                .filter(values -> !values.isEmpty())
                .map(List::getFirst)
                .findFirst()
                .orElse(null);
        }

        String bodyText() {
            return new String(body, StandardCharsets.UTF_8);
        }
    }

    private static final class FakeProviderServer implements AutoCloseable {

        private final HttpServer server;
        private final Deque<StubResponse> responses = new ArrayDeque<>();
        private final BlockingQueue<RecordedRequest> requests = new LinkedBlockingQueue<>();

        private FakeProviderServer() throws IOException {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            server.createContext("/", this::handle);
            server.start();
        }

        String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        synchronized void enqueue(int status, String contentType, byte[] body) {
            responses.addLast(new StubResponse(status, contentType, body, 0, false));
        }

        synchronized void enqueueChunked(int status, String contentType, byte[] body) {
            responses.addLast(new StubResponse(status, contentType, body, 0, true));
        }

        synchronized void enqueueSlowBody(int status, String contentType, byte[] body, long bodyDelayMillis) {
            responses.addLast(new StubResponse(status, contentType, body, bodyDelayMillis, false));
        }

        RecordedRequest takeRequest() throws InterruptedException {
            RecordedRequest request = requests.poll(5, TimeUnit.SECONDS);
            assertThat(request).as("fake provider request").isNotNull();
            return request;
        }

        int requestCount() {
            return requests.size();
        }

        private void handle(HttpExchange exchange) throws IOException {
            byte[] requestBody = exchange.getRequestBody().readAllBytes();
            requests.add(new RecordedRequest(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                exchange.getRequestURI().getRawQuery(), copy(exchange.getRequestHeaders()), requestBody));
            StubResponse response;
            synchronized (this) {
                response = responses.pollFirst();
            }
            if (response == null) {
                response = new StubResponse(
                    500, "text/plain", "missing stub".getBytes(StandardCharsets.UTF_8), 0, false);
            }
            exchange.getResponseHeaders().set("Content-Type", response.contentType());
            exchange.sendResponseHeaders(response.status(), response.chunked() ? 0 : response.body().length);
            if (response.bodyDelayMillis() > 0 && response.body().length > 1) {
                exchange.getResponseBody().write(response.body(), 0, 1);
                exchange.getResponseBody().flush();
                try {
                    Thread.sleep(response.bodyDelayMillis());
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    exchange.close();
                    return;
                }
                exchange.getResponseBody().write(response.body(), 1, response.body().length - 1);
            } else {
                exchange.getResponseBody().write(response.body());
            }
            exchange.close();
        }

        private static Map<String, List<String>> copy(Headers headers) {
            Map<String, List<String>> copy = new LinkedHashMap<>();
            headers.forEach((key, values) -> copy.put(key, new ArrayList<>(values)));
            return copy;
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
