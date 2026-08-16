package org.dromara.aivideo.timeline.service.impl;

import org.dromara.aivideo.creation.domain.CreationProject;
import org.dromara.aivideo.creation.dto.CreationAssetDTO;
import org.dromara.aivideo.creation.enums.CreationAssetStatus;
import org.dromara.aivideo.creation.enums.CreationAssetType;
import org.dromara.aivideo.creation.enums.CreationAssetUsageOrigin;
import org.dromara.aivideo.creation.mapper.CreationProjectMapper;
import org.dromara.aivideo.creation.service.CreationMediaHandle;
import org.dromara.aivideo.creation.service.ICreationAssetService;
import org.dromara.aivideo.task.dto.AiTaskDTO;
import org.dromara.aivideo.timeline.domain.TimelineVersion;
import org.dromara.aivideo.timeline.dto.TimelineCanvasDTO;
import org.dromara.aivideo.timeline.dto.TimelineDocumentDTO;
import org.dromara.aivideo.timeline.dto.TimelineMediaProbeDTO;
import org.dromara.aivideo.timeline.dto.TimelineMediaQualityInspectionDTO;
import org.dromara.aivideo.timeline.dto.TimelineOutputQualityDTO;
import org.dromara.aivideo.timeline.dto.TimelineSubtitleElementDTO;
import org.dromara.aivideo.timeline.dto.TimelineTrackDTO;
import org.dromara.aivideo.timeline.enums.TimelineElementType;
import org.dromara.aivideo.timeline.enums.TimelineTrackArea;
import org.dromara.aivideo.timeline.enums.TimelineTrackType;
import org.dromara.aivideo.timeline.mapper.TimelineVersionMapper;
import org.dromara.aivideo.timeline.service.ITimelineMediaRenderService;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("dev")
class TimelineOutputQualityServiceImplTest {

    private static final long ACTOR_ID = 7L;
    private static final String SCRIPT = "大家好，世界！";
    private static final List<String> CODES = List.of(
        "media.playable", "media.container_codec", "media.video_dimensions", "media.audio_present",
        "media.duration", "content.script_integrity", "content.must_include", "content.prohibited",
        "subtitle.text_integrity", "subtitle.safe_area", "subtitle.timing",
        "perceptual.identity_similarity", "perceptual.lip_sync", "perceptual.voice_consistency",
        "perceptual.visual_stability", "style.tone_match"
    );

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    @Mock
    private CreationProjectMapper projectMapper;
    @Mock
    private TimelineVersionMapper versionMapper;
    @Mock
    private ICreationAssetService assetService;
    @Mock
    private ITimelineMediaRenderService mediaRenderService;
    @Mock
    private CreationMediaHandle mediaHandle;

    private CreationProject project;
    private TimelineVersion version;

    @BeforeEach
    void setUp() throws Exception {
        project = project(SCRIPT);
        version = version(timeline(new BigDecimal("0.05"),
            subtitle("s1", 0, 500, 0, 4, "大家好，", "大 家 好！"),
            subtitle("s2", 500, 1_000, 4, 7, "世界！", "世，界")));
        when(projectMapper.selectOne(any())).thenAnswer(ignored -> project);
        when(versionMapper.selectOne(any())).thenAnswer(ignored -> version);
        lenient().when(assetService.openOwnedTimelineRenderOutput(ACTOR_ID, "201", "401"))
            .thenReturn(mediaHandle);
        lenient().when(mediaRenderService.inspectQuality(mediaHandle)).thenReturn(inspection(probe(), true));
    }

    @Test
    void returnsOrderedNinePassesAndSevenExplicitReviewsWithoutBlackBoxScore() throws Exception {
        TimelineOutputQualityDTO result = service().evaluate(ACTOR_ID, task(), asset());

        assertThat(result.taskId()).isEqualTo("201");
        assertThat(result.assetId()).isEqualTo("401");
        assertThat(result.inputVersionId()).isEqualTo("301");
        assertThat(result.timelineContentHash()).isEqualTo(version.getContentHash());
        assertThat(result.criteria()).extracting(TimelineOutputQualityDTO.Criterion::code).containsExactlyElementsOf(CODES);
        assertThat(result.criteria()).filteredOn(criterion -> criterion.verdict() == TimelineOutputQualityDTO.Verdict.PASS)
            .hasSize(9).allMatch(criterion -> criterion.confidence() == TimelineOutputQualityDTO.Confidence.HIGH);
        assertThat(result.criteria()).filteredOn(criterion -> criterion.verdict() == TimelineOutputQualityDTO.Verdict.REVIEW)
            .hasSize(7).allMatch(criterion -> criterion.confidence() == TimelineOutputQualityDTO.Confidence.LOW);
        assertThat(criterion(result, "content.must_include").evidence())
            .containsEntry("configured", 0).containsEntry("reason", "policy_unconfigured");
        assertThat(criterion(result, "content.prohibited").evidence())
            .containsEntry("configured", 0).containsEntry("reason", "policy_unconfigured");
        String serialized = jsonMapper.writeValueAsString(result);
        assertThat(serialized).doesNotContain("\"score\"");
        assertThat(result.criteria()).extracting(TimelineOutputQualityDTO.Criterion::layer)
            .containsOnly(TimelineOutputQualityDTO.Layer.MEDIA, TimelineOutputQualityDTO.Layer.CONTENT_LAYOUT,
                TimelineOutputQualityDTO.Layer.PERCEPTUAL);
        assertThat(result.criteria()).filteredOn(criterion -> criterion.layer() == TimelineOutputQualityDTO.Layer.MEDIA)
            .hasSize(5);
        assertThat(result.criteria())
            .filteredOn(criterion -> criterion.layer() == TimelineOutputQualityDTO.Layer.CONTENT_LAYOUT).hasSize(6);
        assertThat(result.criteria())
            .filteredOn(criterion -> criterion.layer() == TimelineOutputQualityDTO.Layer.PERCEPTUAL).hasSize(5);
        assertThat(serialized).contains("\"layer\":\"media\"", "\"layer\":\"content_layout\"",
            "\"layer\":\"perceptual\"");
    }

    @Test
    void isolatesEachDeterministicMediaFailureAndRequiresAnExactMp4Token() {
        when(mediaRenderService.inspectQuality(mediaHandle)).thenReturn(inspection(probe(), false));
        assertOnlyFailed(service().evaluate(ACTOR_ID, task(), asset()), "media.playable");

        when(mediaRenderService.inspectQuality(mediaHandle)).thenReturn(inspection(
            probe("notmp4", "h264", "aac", 1080, 1920, 30, true, 1_000), true));
        assertOnlyFailed(service().evaluate(ACTOR_ID, task(), asset()), "media.container_codec");

        when(mediaRenderService.inspectQuality(mediaHandle)).thenReturn(inspection(
            probe("mov,mp4", "h264", "aac", 1920, 1080, 30, true, 1_000), true));
        assertOnlyFailed(service().evaluate(ACTOR_ID, task(), asset()), "media.video_dimensions");

        when(mediaRenderService.inspectQuality(mediaHandle)).thenReturn(inspection(
            probe("mov,mp4", "h264", "aac", 1080, 1920, 30, false, 1_000), true));
        assertOnlyFailed(service().evaluate(ACTOR_ID, task(), asset()), "media.audio_present");
    }

    @Test
    void acceptsDurationDeltaAtTwoHundredFiftyMillisecondsAndRejectsTwoHundredFiftyOne() {
        when(mediaRenderService.inspectQuality(mediaHandle)).thenReturn(inspection(
            probe("mov,mp4", "h264", "aac", 1080, 1920, 30, true, 1_250), true));
        assertThat(criterion(service().evaluate(ACTOR_ID, task(), asset(1_000L)), "media.duration").verdict())
            .isEqualTo(TimelineOutputQualityDTO.Verdict.PASS);

        when(mediaRenderService.inspectQuality(mediaHandle)).thenReturn(inspection(
            probe("mov,mp4", "h264", "aac", 1080, 1920, 30, true, 1_251), true));
        assertThat(criterion(service().evaluate(ACTOR_ID, task(), asset(1_000L)), "media.duration").verdict())
            .isEqualTo(TimelineOutputQualityDTO.Verdict.FAIL);
    }

    @Test
    void emitsPlayableFailureAndReviewsOtherMediaFactsWhenOpeningOrDecodingThrows() {
        when(mediaRenderService.inspectQuality(mediaHandle)).thenThrow(new IllegalStateException("private path"));

        TimelineOutputQualityDTO result = service().evaluate(ACTOR_ID, task(), asset());

        assertThat(criterion(result, "media.playable").verdict()).isEqualTo(TimelineOutputQualityDTO.Verdict.FAIL);
        assertThat(result.criteria().subList(1, 5))
            .allMatch(item -> item.verdict() == TimelineOutputQualityDTO.Verdict.REVIEW);
        assertThat(result.criteria().subList(0, 5).toString()).doesNotContain("private path");
    }

    @Test
    void reviewsAllMediaCriteriaWhenNoRendererIsConfiguredWithoutOpeningTheAsset() {
        TimelineOutputQualityDTO result = service(null, TimelineOutputQualityServiceImpl.Policy.empty())
            .evaluate(ACTOR_ID, task(), asset());

        assertThat(result.criteria().subList(0, 5))
            .allMatch(item -> item.verdict() == TimelineOutputQualityDTO.Verdict.REVIEW
                && item.confidence() == TimelineOutputQualityDTO.Confidence.LOW);
        verify(assetService, never()).openOwnedTimelineRenderOutput(any(Long.class), any(), any());
    }

    @Test
    void evaluatesConfiguredRequiredAndProhibitedPhrasesWithoutExposingTheirText() {
        TimelineOutputQualityDTO missing = service(mediaRenderService,
            new TimelineOutputQualityServiceImpl.Policy(List.of("产品"), List.of()))
            .evaluate(ACTOR_ID, task(), asset());
        assertOnlyFailed(missing, "content.must_include");
        assertThat(criterion(missing, "content.must_include").evidence().toString()).doesNotContain("产品");

        TimelineOutputQualityDTO prohibited = service(mediaRenderService,
            new TimelineOutputQualityServiceImpl.Policy(List.of(), List.of("世界")))
            .evaluate(ACTOR_ID, task(), asset());
        assertOnlyFailed(prohibited, "content.prohibited");
        assertThat(criterion(prohibited, "content.prohibited").evidence().toString()).doesNotContain("世界");
    }

    @Test
    void distinguishesExactSourceCoverageFromEquivalentSubtitlePresentation() throws Exception {
        version = version(timeline(new BigDecimal("0.05"),
            subtitle("s1", 0, 500, 0, 3, "大家好", "大 家 好！"),
            subtitle("s2", 500, 1_000, 4, 7, "世界！", "世，界")));

        TimelineOutputQualityDTO result = service().evaluate(ACTOR_ID, task(), asset());

        assertOnlyFailed(result, "content.script_integrity");
        assertThat(criterion(result, "subtitle.text_integrity").verdict())
            .isEqualTo(TimelineOutputQualityDTO.Verdict.PASS);
    }

    @Test
    void acceptsNfcWhitespaceAndPunctuationButRejectsOneSemanticCodePoint() throws Exception {
        project = project("Café，开始。");
        version = version(timeline(new BigDecimal("0.05"),
            subtitle("s1", 0, 500, 0, 5, "Café，", "C a f é！"),
            subtitle("s2", 500, 1_000, 5, 8, "开始。", "开，始")));
        TimelineOutputQualityDTO equivalent = service().evaluate(ACTOR_ID, task(), asset());
        assertThat(criterion(equivalent, "content.script_integrity").verdict())
            .isEqualTo(TimelineOutputQualityDTO.Verdict.PASS);
        assertThat(criterion(equivalent, "subtitle.text_integrity").verdict())
            .isEqualTo(TimelineOutputQualityDTO.Verdict.PASS);

        version = version(timeline(new BigDecimal("0.05"),
            subtitle("s1", 0, 500, 0, 5, "Café，", "Cafè"),
            subtitle("s2", 500, 1_000, 5, 8, "开始。", "开始")));
        TimelineOutputQualityDTO changed = service().evaluate(ACTOR_ID, task(), asset());
        assertThat(criterion(changed, "subtitle.text_integrity").verdict())
            .isEqualTo(TimelineOutputQualityDTO.Verdict.FAIL);
    }

    @Test
    void rejectsSafeMarginDriftInvalidAlignmentOneMillisecondOverlapAndOverflow() throws Exception {
        version = version(timeline(new BigDecimal("0.049"),
            subtitle("s1", 0, 500, 0, 4, "大家好，", "大家好"),
            subtitle("s2", 500, 1_000, 4, 7, "世界！", "世界")));
        assertOnlyFailed(service().evaluate(ACTOR_ID, task(), asset()), "subtitle.safe_area");

        version = version(timeline(new BigDecimal("0.05"),
            subtitle("s1", 0, 500, 0, 4, "大家好，", "大家好", "lower", "justify"),
            subtitle("s2", 500, 1_000, 4, 7, "世界！", "世界")));
        assertOnlyFailed(service().evaluate(ACTOR_ID, task(), asset()), "subtitle.safe_area");

        version = version(timeline(new BigDecimal("0.05"),
            subtitle("s1", 0, 500, 0, 4, "大家好，", "大家好"),
            subtitle("s2", 499, 1_000, 4, 7, "世界！", "世界")));
        assertOnlyFailed(service().evaluate(ACTOR_ID, task(), asset()), "subtitle.timing");

        version = version(timeline(new BigDecimal("0.05"),
            subtitle("s1", 0, 500, 0, 4, "大家好，", "大家好"),
            subtitle("s2", 500, 1_001, 4, 7, "世界！", "世界")));
        assertOnlyFailed(service().evaluate(ACTOR_ID, task(), asset()), "subtitle.timing");
    }

    @Test
    void failsClosedBeforeMediaForCrossOwnerOrWrongVersionReason() {
        project.setOwnerUserId(8L);
        assertThatThrownBy(() -> service().evaluate(ACTOR_ID, task(), asset()))
            .isInstanceOfSatisfying(ServiceException.class, exception -> assertThat(exception.getCode()).isEqualTo(46601));
        verify(assetService, never()).openOwnedTimelineRenderOutput(any(Long.class), any(), any());

        project.setOwnerUserId(ACTOR_ID);
        version.setVersionReason("manual_save");
        assertThatThrownBy(() -> service().evaluate(ACTOR_ID, task(), asset()))
            .isInstanceOfSatisfying(ServiceException.class, exception -> assertThat(exception.getCode()).isEqualTo(46608));
    }

    @Test
    void acceptsEquivalentPhysicalJsonButRejectsSemanticChangeAgainstCanonicalHash() throws Exception {
        String canonicalJson = version.getContentJson();
        version.setContentJson(jsonMapper.writerWithDefaultPrettyPrinter()
            .writeValueAsString(jsonMapper.readTree(canonicalJson)));

        TimelineOutputQualityDTO result = service().evaluate(ACTOR_ID, task(), asset());

        assertThat(result.timelineContentHash()).isEqualTo(version.getContentHash());
        assertThat(result.criteria()).noneMatch(criterion -> criterion.verdict() == TimelineOutputQualityDTO.Verdict.FAIL);

        ObjectNode changed = (ObjectNode) jsonMapper.readTree(canonicalJson);
        ((ObjectNode) changed.path("canvas")).put("width", 720);
        version.setContentJson(jsonMapper.writeValueAsString(changed));
        assertThatThrownBy(() -> service().evaluate(ACTOR_ID, task(), asset()))
            .isInstanceOfSatisfying(ServiceException.class, exception -> assertThat(exception.getCode()).isEqualTo(46605));
    }

    private TimelineOutputQualityServiceImpl service() {
        return service(mediaRenderService, TimelineOutputQualityServiceImpl.Policy.empty());
    }

    private TimelineOutputQualityServiceImpl service(ITimelineMediaRenderService renderer,
                                                      TimelineOutputQualityServiceImpl.Policy policy) {
        return new TimelineOutputQualityServiceImpl(projectMapper, versionMapper, assetService, renderer,
            jsonMapper, policy);
    }

    private CreationProject project(String script) {
        CreationProject value = new CreationProject();
        value.setProjectId(101L);
        value.setOwnerUserId(ACTOR_ID);
        value.setScriptTextSnapshot(script);
        value.setCanvasWidth(1080);
        value.setCanvasHeight(1920);
        value.setFrameRate(30);
        value.setDurationMs(1_000L);
        value.setDelFlag("0");
        return value;
    }

    private TimelineVersion version(TimelineDocumentDTO timeline) throws Exception {
        String canonical = jsonMapper.writeValueAsString(timeline);
        TimelineVersion value = new TimelineVersion();
        value.setTimelineVersionId(301L);
        value.setProjectId(101L);
        value.setOwnerUserId(ACTOR_ID);
        value.setVersionReason("render_input");
        value.setContentJson(canonical);
        value.setContentHash(digest(canonical));
        value.setDurationMs(timeline.canvas().durationMs());
        return value;
    }

    private TimelineDocumentDTO timeline(BigDecimal safeMargin, TimelineSubtitleElementDTO... subtitles) {
        return new TimelineDocumentDTO("timeline-1", new TimelineCanvasDTO(1080, 1920, 30, 1_000, safeMargin),
            List.of(new TimelineTrackDTO("subtitle", TimelineTrackType.SUBTITLE, TimelineTrackArea.TOP, 1,
                false, false, List.of(subtitles))));
    }

    private TimelineSubtitleElementDTO subtitle(String id, long startMs, long endMs, int sourceStart,
                                                 int sourceEnd, String source, String display) {
        return subtitle(id, startMs, endMs, sourceStart, sourceEnd, source, display, "lower", "center");
    }

    private TimelineSubtitleElementDTO subtitle(String id, long startMs, long endMs, int sourceStart,
                                                 int sourceEnd, String source, String display,
                                                 String anchor, String alignment) {
        return new TimelineSubtitleElementDTO(id, TimelineElementType.SUBTITLE, startMs, endMs, 1, true, false,
            "subtitle", source, display, sourceStart, sourceEnd, "font", "1.000", "a".repeat(64), 48,
            "#FFFFFFFF", false, null, true, "#000000FF", 2, anchor, alignment);
    }

    private AiTaskDTO task() {
        return new AiTaskDTO("201", "timeline_render", "success", "completed", "creation_project", "101",
            "101", "1", "301", "401", null, null, null, null, null, 100, false, false);
    }

    private CreationAssetDTO asset() {
        return asset(1_000L);
    }

    private CreationAssetDTO asset(Long durationMs) {
        return new CreationAssetDTO("401", "output.mp4", "video/mp4", "b".repeat(64), CreationAssetType.VIDEO,
            CreationAssetUsageOrigin.TIMELINE_RENDER_OUTPUT, CreationAssetStatus.READY, 1_024L, durationMs,
            1080, 1920, true, true, Instant.EPOCH);
    }

    private TimelineMediaProbeDTO probe() {
        return probe("mov,mp4,m4a,3gp,3g2,mj2", "h264", "aac", 1080, 1920, 30, true, 1_000);
    }

    private TimelineMediaProbeDTO probe(String format, String videoCodec, String audioCodec, Integer width,
                                        Integer height, Integer frameRate, boolean audio, long durationMs) {
        return new TimelineMediaProbeDTO("401", "video", format, durationMs, 1_024L, width, height, frameRate,
            22_050, 1, true, audio, videoCodec, audioCodec);
    }

    private TimelineMediaQualityInspectionDTO inspection(TimelineMediaProbeDTO probe, boolean decoded) {
        return new TimelineMediaQualityInspectionDTO(probe, decoded);
    }

    private void assertOnlyFailed(TimelineOutputQualityDTO result, String code) {
        assertThat(result.criteria().stream()
            .filter(item -> item.verdict() == TimelineOutputQualityDTO.Verdict.FAIL)
            .map(TimelineOutputQualityDTO.Criterion::code).toList()).containsExactly(code);
    }

    private TimelineOutputQualityDTO.Criterion criterion(TimelineOutputQualityDTO result, String code) {
        return result.criteria().stream().filter(item -> code.equals(item.code())).findFirst().orElseThrow();
    }

    private String digest(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
            .digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
