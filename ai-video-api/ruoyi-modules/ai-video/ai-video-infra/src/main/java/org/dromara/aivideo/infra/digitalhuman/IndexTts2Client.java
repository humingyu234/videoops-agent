package org.dromara.aivideo.infra.digitalhuman;

import org.dromara.aivideo.digitalhuman.dto.VoiceSynthesisRequestDTO;
import org.dromara.aivideo.digitalhuman.dto.VoiceSynthesisResultDTO;
import org.dromara.aivideo.digitalhuman.service.IVoiceSynthesisService;
import org.dromara.common.core.exception.ServiceException;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * IndexTTS2 声音克隆客户端。
 */
public final class IndexTts2Client implements IVoiceSynthesisService {

    static final int MAX_RESPONSE_BYTES = 32 * 1024 * 1024;

    private final DigitalHumanProviderProperties.IndexTts2 properties;
    private final HttpClient httpClient;

    public IndexTts2Client(DigitalHumanProviderProperties.IndexTts2 properties) {
        this.properties = Objects.requireNonNull(properties);
        this.httpClient = DigitalHumanHttpSupport.client(
            properties.getCaCertificate(), properties.isInsecureSkipTlsVerify());
    }

    @Override
    public VoiceSynthesisResultDTO synthesize(VoiceSynthesisRequestDTO request) {
        Objects.requireNonNull(request, "声音合成参数不能为空");
        DigitalHumanHttpSupport.MultipartBody body = DigitalHumanHttpSupport.multipart(List.of(
            DigitalHumanHttpSupport.text("text", request.text()),
            DigitalHumanHttpSupport.file("reference_audio", request.referenceAudioName(),
                request.referenceAudioType(), request.referenceAudio())));
        HttpRequest.Builder builder = HttpRequest.newBuilder()
            .uri(DigitalHumanHttpSupport.endpoint(properties.getBaseUrl(), "/v1/indextts2/clone"))
            .timeout(Duration.ofSeconds(300))
            .header("Content-Type", body.contentType())
            .header("Accept", "audio/wav")
            .header("X-API-Key", properties.getApiKey())
            .POST(HttpRequest.BodyPublishers.ofByteArray(body.content()));
        DigitalHumanHttpSupport.basic(builder, properties.getBasicUser(), properties.getBasicPassword());
        try {
            DigitalHumanHttpSupport.LimitedResponse response = DigitalHumanHttpSupport.sendLimited(
                httpClient, builder.build(), MAX_RESPONSE_BYTES, "声音供应商调用失败");
            String mediaType = response.headers().firstValue("Content-Type").orElse("");
            if (response.statusCode() != 200 || !mediaType.toLowerCase().startsWith("audio/wav")
                || response.body().length == 0) {
                throw new ServiceException("声音供应商调用失败");
            }
            return new VoiceSynthesisResultDTO(response.body(), "audio/wav", "wav");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ServiceException("声音供应商调用失败");
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof ServiceException serviceException) {
                throw serviceException;
            }
            throw new ServiceException("声音供应商调用失败");
        }
    }
}
