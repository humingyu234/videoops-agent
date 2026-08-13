package org.dromara.aivideo.timeline.service.impl;

import org.dromara.aivideo.creation.dto.CreationAssetResolveDTO;
import org.dromara.aivideo.creation.enums.CreationAssetType;
import org.dromara.aivideo.creation.service.ICreationAssetService;
import org.dromara.aivideo.timeline.dto.TimelineDocumentDTO;
import org.dromara.aivideo.timeline.enums.TimelineAssetUsageType;
import org.dromara.aivideo.timeline.service.ISubtitleNormalizationService;
import org.dromara.aivideo.timeline.service.ITimelineDocumentService;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("dev")
class TimelineDocumentServiceImplTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    @Mock
    private ICreationAssetService assetService;
    @Mock
    private ISubtitleNormalizationService subtitleNormalizationService;

    @Test
    void validatesTheFrozenSevenElementDocumentAndBuildsOwnerCheckedAssetReferences() throws Exception {
        JsonNode timeline = fixture();
        when(subtitleNormalizationService.normalize(anyString(), any(), any(Integer.class), any(BigDecimal.class)))
            .thenAnswer(invocation -> new ISubtitleNormalizationService.NormalizationResult(
                invocation.getArgument(1), List.of()));
        when(assetService.resolveOwned(anyLong(), anyString(), any(TimelineAssetUsageType.class)))
            .thenAnswer(invocation -> resolved(invocation.getArgument(1), invocation.getArgument(2)));

        ITimelineDocumentService.ValidatedTimeline validated = service().validate(7L, context(), timeline);

        assertThat(validated.timeline()).isInstanceOf(TimelineDocumentDTO.class);
        assertThat(validated.contentHash()).matches("[0-9a-f]{64}");
        assertThat(validated.canonicalJson()).contains("timeline-1", "main_video", "background_music");
        assertThat(validated.assets()).hasSize(6);
        verify(assetService).resolveOwned(7L, "90071992547410003", TimelineAssetUsageType.BASE_VIDEO);
    }

    @Test
    void rejectsUnknownFieldsThroughTheFrozenSchemaBeforeAnyAssetLookup() throws Exception {
        ObjectNode timeline = (ObjectNode) fixture();
        timeline.put("storageKey", "private/forbidden.mp4");

        assertThatThrownBy(() -> service().validate(7L, context(), timeline))
            .isInstanceOfSatisfying(ServiceException.class, exception -> assertThat(exception.getCode()).isEqualTo(46605));
        verify(assetService, never()).resolveOwned(anyLong(), anyString(), any(TimelineAssetUsageType.class));
    }

    @Test
    void rejectsDuplicateElementIdsAndWrongStableTrackOrderAfterSchemaValidation() throws Exception {
        ObjectNode duplicate = (ObjectNode) fixture();
        ((ObjectNode) duplicate.path("tracks").get(1).path("elements").get(0))
            .put("elementId", "fancy_0001");
        when(subtitleNormalizationService.normalize(anyString(), any(), any(Integer.class), any(BigDecimal.class)))
            .thenAnswer(invocation -> new ISubtitleNormalizationService.NormalizationResult(invocation.getArgument(1), List.of()));

        assertThatThrownBy(() -> service().validate(7L, context(), duplicate))
            .isInstanceOfSatisfying(ServiceException.class, exception -> assertThat(exception.getCode()).isEqualTo(46605));

        ObjectNode outOfOrder = (ObjectNode) fixture();
        ((ObjectNode) outOfOrder.path("tracks").get(0)).put("order", 4);
        assertThatThrownBy(() -> service().validate(7L, context(), outOfOrder))
            .isInstanceOf(ServiceException.class);
    }

    @Test
    void rejectsDeletingTheEntireSubtitleTrackForANonEmptyProjectScript() throws Exception {
        ObjectNode timeline = (ObjectNode) fixture();
        ((tools.jackson.databind.node.ArrayNode) timeline.path("tracks")).remove(1);
        when(subtitleNormalizationService.normalize(anyString(), any(), any(Integer.class), any(BigDecimal.class)))
            .thenThrow(new ServiceException("subtitle text is incomplete", 46607));

        assertThatThrownBy(() -> service().validate(7L, context(), timeline))
            .isInstanceOfSatisfying(ServiceException.class, exception -> assertThat(exception.getCode()).isEqualTo(46607));
        verify(subtitleNormalizationService).normalize(anyString(), any(), any(Integer.class), any(BigDecimal.class));
    }

    @Test
    void rejectsClientMediaDurationThatDoesNotMatchTheServerProbe() throws Exception {
        ObjectNode timeline = (ObjectNode) fixture();
        ((ObjectNode) timeline.path("tracks").get(5).path("elements").get(0)).put("sourceDurationMs", 30_001);
        when(subtitleNormalizationService.normalize(anyString(), any(), any(Integer.class), any(BigDecimal.class)))
            .thenAnswer(invocation -> new ISubtitleNormalizationService.NormalizationResult(
                invocation.getArgument(1), List.of()));
        when(assetService.resolveOwned(anyLong(), anyString(), any(TimelineAssetUsageType.class)))
            .thenAnswer(invocation -> resolved(invocation.getArgument(1), invocation.getArgument(2)));

        assertThatThrownBy(() -> service().validate(7L, context(), timeline))
            .isInstanceOfSatisfying(ServiceException.class, exception -> assertThat(exception.getCode()).isEqualTo(46606));
    }

    private TimelineDocumentServiceImpl service() {
        return new TimelineDocumentServiceImpl(assetService, subtitleNormalizationService, jsonMapper);
    }

    private ITimelineDocumentService.ProjectContext context() {
        return new ITimelineDocumentService.ProjectContext("90071992547409931", "90071992547410003",
            "90071992547410004", "欢迎使用 AI 视频！三步做出专业视频。", 30_000L, 1080, 1920, 30);
    }

    private CreationAssetResolveDTO resolved(String assetId, TimelineAssetUsageType usageType) {
        CreationAssetType type = switch (usageType) {
            case BASE_VIDEO, PIP_VIDEO -> CreationAssetType.VIDEO;
            case IMAGE -> CreationAssetType.IMAGE;
            default -> CreationAssetType.AUDIO;
        };
        long durationMs = switch (assetId) {
            case "90071992547410002" -> 5_000L;
            case "90071992547410005" -> 12_000L;
            case "90071992547410006" -> 1_000L;
            default -> 30_000L;
        };
        return new CreationAssetResolveDTO(assetId, "application/octet-stream",
            "a".repeat(64), type, usageType, 10L, durationMs, 1080, 1920,
            type == CreationAssetType.VIDEO, type == CreationAssetType.AUDIO);
    }

    private JsonNode fixture() throws Exception {
        try (InputStream input = getClass().getClassLoader()
            .getResourceAsStream("contracts/creation-timeline/timeline-draft.example.json")) {
            return jsonMapper.readTree(input).required("timeline");
        }
    }
}
