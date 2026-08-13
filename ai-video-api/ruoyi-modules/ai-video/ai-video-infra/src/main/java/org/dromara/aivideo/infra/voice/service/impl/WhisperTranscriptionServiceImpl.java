package org.dromara.aivideo.infra.voice.service.impl;

import org.dromara.aivideo.asset.dto.AssetDTO;
import org.dromara.aivideo.infra.voice.WhisperProperties;
import org.dromara.aivideo.infra.voice.client.WhisperTranscriptionException;
import org.dromara.aivideo.infra.voice.client.WhisperTranscriptionResponse;
import org.dromara.aivideo.voice.dto.VoiceTranscriptCueDTO;
import org.dromara.aivideo.voice.dto.VoiceTranscriptionLeaseDTO;
import org.dromara.aivideo.voice.dto.VoiceTranscriptionResultDTO;
import org.dromara.aivideo.voice.dto.WhisperTranscriptionInputDTO;
import org.dromara.aivideo.voice.service.IWhisperTranscriptionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.io.InputStream;
import java.time.Duration;
import java.util.List;

@Service
public class WhisperTranscriptionServiceImpl implements IWhisperTranscriptionService {
    private final WhisperProperties properties;
    private final RestClient restClient;

    @Autowired
    public WhisperTranscriptionServiceImpl(WhisperProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(properties.getTimeout());
        this.restClient = RestClient.builder().baseUrl(properties.validatedBaseUri().toString())
            .requestFactory(requestFactory).build();
    }

    WhisperTranscriptionServiceImpl(WhisperProperties properties, RestClient restClient) {
        this.properties = properties;
        this.restClient = restClient;
    }

    @Override
    public VoiceTranscriptionResultDTO transcribe(VoiceTranscriptionLeaseDTO lease, AssetDTO asset, InputStream input) {
        return transcribe(lease.requestId(), asset.originalName(), asset.contentType(), asset.fileSize(), input);
    }

    /**
     * C0 timeline callers carry only the media metadata required by Whisper; they must never
     * manufacture a legacy lease or tenant/workspace values merely to invoke transcription.
     */
    @Override
    public VoiceTranscriptionResultDTO transcribe(WhisperTranscriptionInputDTO inputMetadata, InputStream input) {
        if (inputMetadata == null || inputMetadata.requestId() == null || inputMetadata.requestId().isBlank()
            || inputMetadata.originalName() == null || inputMetadata.originalName().isBlank()
            || inputMetadata.contentType() == null || inputMetadata.contentType().isBlank()
            || inputMetadata.fileSize() < 0) {
            throw new WhisperTranscriptionException("WHISPER_INPUT_INVALID", "转写输入无效", false);
        }
        return transcribe(inputMetadata.requestId(), inputMetadata.originalName(), inputMetadata.contentType(),
            inputMetadata.fileSize(), input);
    }

    private VoiceTranscriptionResultDTO transcribe(String requestId, String originalName, String contentType,
                                                    long fileSize, InputStream input) {
        InputStreamResource resource = new InputStreamResource(input) {
            @Override public String getFilename() { return originalName; }
            @Override public long contentLength() { return fileSize; }
        };
        MultipartBodyBuilder body = new MultipartBodyBuilder();
        body.part("file", resource, MediaType.parseMediaType(contentType)).filename(originalName);
        body.part("requestId", requestId);
        body.part("language", "zh");
        body.part("wordTimestamps", "true");
        try {
            WhisperTranscriptionResponse response = restClient.post().uri("/internal/v1/transcriptions")
                .header("X-Internal-Token", properties.getInternalToken())
                .contentType(MediaType.MULTIPART_FORM_DATA).body(body.build())
                .retrieve().body(WhisperTranscriptionResponse.class);
            if (response == null || !requestId.equals(response.requestId())
                || response.text() == null || response.text().isBlank()
                || response.durationMillis() == null || response.durationMillis() < 0) {
                throw new WhisperTranscriptionException("WHISPER_PROTOCOL_INVALID", "Whisper 返回协议无效", false);
            }
            List<VoiceTranscriptCueDTO> timeline = validateTimeline(response.words(), response.durationMillis());
            return new VoiceTranscriptionResultDTO(response.requestId(), response.text(),
                response.language(), response.durationMillis(), timeline);
        } catch (WhisperTranscriptionException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            boolean retryable = exception.getStatusCode().value() == 503
                || exception.getStatusCode().value() == 429 || exception.getStatusCode().is5xxServerError();
            throw new WhisperTranscriptionException("WHISPER_HTTP_" + exception.getStatusCode().value(),
                retryable ? "本地 Whisper 暂时不可用" : "声音文件无法转写", retryable);
        } catch (RuntimeException exception) {
            throw new WhisperTranscriptionException("WHISPER_UNAVAILABLE", "本地 Whisper 暂时不可用", true);
        }
    }

    private List<VoiceTranscriptCueDTO> validateTimeline(
        List<WhisperTranscriptionResponse.WhisperWordResponse> words, long durationMillis) {
        if (words == null || words.isEmpty()) {
            throw new WhisperTranscriptionException("WHISPER_PROTOCOL_INVALID", "Whisper 返回协议无效", false);
        }
        return words.stream().map(word -> {
            if (word == null || word.text() == null || word.text().isBlank()
                || word.startMillis() == null || word.endMillis() == null
                || word.startMillis() < 0 || word.endMillis() < word.startMillis()
                || word.endMillis() > durationMillis) {
                throw new WhisperTranscriptionException("WHISPER_PROTOCOL_INVALID", "Whisper 返回协议无效", false);
            }
            return new VoiceTranscriptCueDTO(word.text().trim(), word.startMillis(), word.endMillis());
        }).toList();
    }
}
