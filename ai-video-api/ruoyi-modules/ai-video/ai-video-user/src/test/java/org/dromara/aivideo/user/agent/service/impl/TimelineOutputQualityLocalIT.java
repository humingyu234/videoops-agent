package org.dromara.aivideo.user.agent.service.impl;

import org.dromara.aivideo.agent.dto.AgentToolDTOs;
import org.dromara.aivideo.creation.domain.CreationProject;
import org.dromara.aivideo.creation.dto.CreationAssetDTO;
import org.dromara.aivideo.creation.dto.CreationAssetResolveDTO;
import org.dromara.aivideo.creation.enums.CreationAssetStatus;
import org.dromara.aivideo.creation.enums.CreationAssetType;
import org.dromara.aivideo.creation.enums.CreationAssetUsageOrigin;
import org.dromara.aivideo.creation.mapper.CreationProjectMapper;
import org.dromara.aivideo.creation.service.CreationMediaHandle;
import org.dromara.aivideo.creation.service.ICreationAssetService;
import org.dromara.aivideo.creation.service.ICreationProjectService;
import org.dromara.aivideo.digitalhuman.service.IDigitalHumanGenerationService;
import org.dromara.aivideo.digitalhuman.service.IDigitalHumanResourceGenerationService;
import org.dromara.aivideo.identity.dto.AppPrincipalSnapshotDTO;
import org.dromara.aivideo.identity.dto.AppWorkspaceSessionSnapshotDTO;
import org.dromara.aivideo.infra.timeline.TimelineInfrastructureProperties;
import org.dromara.aivideo.infra.timeline.render.FfmpegTimelineMediaRenderService;
import org.dromara.aivideo.task.dto.AiTaskDTO;
import org.dromara.aivideo.task.service.IAiTaskService;
import org.dromara.aivideo.timeline.domain.TimelineVersion;
import org.dromara.aivideo.timeline.dto.TimelineOutputQualityDTO;
import org.dromara.aivideo.timeline.mapper.TimelineVersionMapper;
import org.dromara.aivideo.timeline.service.ITimelineMediaRenderService;
import org.dromara.aivideo.timeline.service.impl.TimelineOutputQualityServiceImpl;
import org.dromara.aivideo.user.timeline.service.TimelineTaskApplicationService;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Local T1 artifact proof through the real typed tool and FFmpeg quality boundary. */
@Tag("dev")
class TimelineOutputQualityLocalIT {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @TempDir
    Path temporaryDirectory;

    @Test
    void inspectsCurrentT1OutputThroughTypedToolWithRealFfmpeg() throws Exception {
        Path contextPath = requiredFile("AIVIDEO_T5_CONTEXT_PATH");
        Path mediaPath = requiredFile("AIVIDEO_T5_MP4_PATH");
        Path ffmpegPath = requiredFile("AIVIDEO_TIMELINE_FFMPEG_PATH");
        Path ffprobePath = requiredFile("AIVIDEO_TIMELINE_FFPROBE_PATH");
        String[] facts = Files.readString(contextPath, StandardCharsets.UTF_8).strip().split("\t", -1);
        assertThat(facts).hasSize(20);

        String taskId = facts[0];
        long ownerId = Long.parseLong(facts[1]);
        String projectId = facts[2];
        String versionId = facts[3];
        String assetId = facts[4];
        String assetSha256 = facts[5];
        long assetBytes = Long.parseLong(facts[6]);
        long assetDurationMs = Long.parseLong(facts[7]);
        int width = Integer.parseInt(facts[8]);
        int height = Integer.parseInt(facts[9]);
        boolean hasVideo = "1".equals(facts[10]);
        boolean hasAudio = "1".equals(facts[11]);
        String script = decodeHex(facts[16]);
        String timelineHash = facts[17];
        String timelineJson = decodeHex(facts[18]);

        CreationProject project = new CreationProject();
        project.setProjectId(Long.valueOf(projectId));
        project.setOwnerUserId(ownerId);
        project.setScriptTextSnapshot(script);
        project.setDurationMs(Long.valueOf(facts[12]));
        project.setCanvasWidth(Integer.valueOf(facts[13]));
        project.setCanvasHeight(Integer.valueOf(facts[14]));
        project.setFrameRate(Integer.valueOf(facts[15]));
        TimelineVersion version = new TimelineVersion();
        version.setTimelineVersionId(Long.valueOf(versionId));
        version.setProjectId(Long.valueOf(projectId));
        version.setOwnerUserId(ownerId);
        version.setVersionReason(facts[19]);
        version.setContentHash(timelineHash);
        version.setContentJson(timelineJson);
        CreationAssetDTO asset = new CreationAssetDTO(assetId, "final.mp4", "video/mp4", assetSha256,
            CreationAssetType.VIDEO, CreationAssetUsageOrigin.TIMELINE_RENDER_OUTPUT, CreationAssetStatus.READY,
            assetBytes, assetDurationMs, width, height, hasVideo, hasAudio, Instant.EPOCH);
        AiTaskDTO task = new AiTaskDTO(taskId, "timeline_render", "success", "success", "creation_project",
            projectId, projectId, "1", versionId, assetId, null, null, "2026-08-16T00:00:00Z",
            "2026-08-16T00:00:00Z", null, 100, false, false);

        CreationProjectMapper projectMapper = mock(CreationProjectMapper.class);
        TimelineVersionMapper versionMapper = mock(TimelineVersionMapper.class);
        ICreationAssetService assetService = mock(ICreationAssetService.class);
        IAiTaskService taskService = mock(IAiTaskService.class);
        when(projectMapper.selectOne(any())).thenReturn(project);
        when(versionMapper.selectOne(any())).thenReturn(version);
        when(assetService.getOwnedTimelineRenderOutput(ownerId, taskId, assetId)).thenReturn(asset);
        CreationAssetResolveDTO mediaFacts = new CreationAssetResolveDTO(assetId, "video/mp4", assetSha256,
            CreationAssetType.VIDEO, null, assetBytes, assetDurationMs,
            width, height, hasVideo, hasAudio, CreationAssetUsageOrigin.TIMELINE_RENDER_OUTPUT);
        when(assetService.openOwnedTimelineRenderOutput(ownerId, taskId, assetId))
            .thenReturn(new FileHandle(mediaPath, mediaFacts));
        when(taskService.getOwned(any(), eq(taskId))).thenReturn(task);

        TimelineInfrastructureProperties properties = properties(ffmpegPath, ffprobePath);
        ITimelineMediaRenderService renderer = new FfmpegTimelineMediaRenderService(properties);
        @SuppressWarnings("unchecked")
        ObjectProvider<ITimelineMediaRenderService> rendererProvider = mock(ObjectProvider.class);
        when(rendererProvider.getIfAvailable()).thenReturn(renderer);
        TimelineOutputQualityServiceImpl qualityService = new TimelineOutputQualityServiceImpl(
            projectMapper, versionMapper, assetService, rendererProvider, JSON);
        AgentToolServiceImpl tools = new AgentToolServiceImpl(
            mock(IDigitalHumanResourceGenerationService.class), mock(IDigitalHumanGenerationService.class),
            mock(ICreationProjectService.class), assetService, mock(TimelineTaskApplicationService.class),
            taskService, qualityService);
        var arguments = JSON.createObjectNode().put("taskId", taskId);
        AgentToolDTOs.OutputInspectionResult result = (AgentToolDTOs.OutputInspectionResult) tools.execute(
            principal(ownerId), new AgentToolDTOs.Call("inspect_timeline_output", arguments));

        TimelineOutputQualityDTO quality = result.quality();
        assertThat(result).extracting(AgentToolDTOs.OutputInspectionResult::taskId,
            AgentToolDTOs.OutputInspectionResult::assetId, AgentToolDTOs.OutputInspectionResult::sha256)
            .containsExactly(taskId, assetId, assetSha256);
        assertThat(result.hasVideoStream()).isTrue();
        assertThat(result.hasAudioStream()).isTrue();
        assertThat(sha256(mediaPath)).isEqualTo(assetSha256);
        assertThat(quality).extracting(TimelineOutputQualityDTO::taskId, TimelineOutputQualityDTO::assetId,
            TimelineOutputQualityDTO::artifactSha256, TimelineOutputQualityDTO::inputVersionId,
            TimelineOutputQualityDTO::timelineContentHash)
            .containsExactly(taskId, assetId, assetSha256, versionId, timelineHash);
        List<String> expectedCodes = List.of("media.playable", "media.container_codec", "media.video_dimensions",
            "media.audio_present", "media.duration", "content.script_integrity", "content.must_include",
            "content.prohibited", "subtitle.text_integrity", "subtitle.safe_area", "subtitle.timing",
            "perceptual.identity_similarity", "perceptual.lip_sync", "perceptual.voice_consistency",
            "perceptual.visual_stability", "style.tone_match");
        List<String> actualCodes = quality.criteria().stream().map(TimelineOutputQualityDTO.Criterion::code).toList();
        assertThat(actualCodes).containsExactlyElementsOf(expectedCodes);
        assertThat(new LinkedHashSet<>(actualCodes)).hasSize(16);
        assertThat(quality.criteria()).filteredOn(criterion ->
            criterion.verdict() == TimelineOutputQualityDTO.Verdict.PASS).hasSize(9);
        assertThat(quality.criteria()).filteredOn(criterion ->
            criterion.verdict() == TimelineOutputQualityDTO.Verdict.REVIEW).hasSize(7);
        assertThat(quality.criteria()).filteredOn(criterion ->
            criterion.verdict() == TimelineOutputQualityDTO.Verdict.FAIL).isEmpty();
        assertThat(quality.criteria()).filteredOn(criterion ->
            criterion.layer() == TimelineOutputQualityDTO.Layer.MEDIA).hasSize(5);
        assertThat(quality.criteria()).filteredOn(criterion ->
            criterion.layer() == TimelineOutputQualityDTO.Layer.CONTENT_LAYOUT).hasSize(6);
        assertThat(quality.criteria()).filteredOn(criterion ->
            criterion.layer() == TimelineOutputQualityDTO.Layer.PERCEPTUAL).hasSize(5);
        Map<String, TimelineOutputQualityDTO.Criterion> byCode = new LinkedHashMap<>();
        quality.criteria().forEach(criterion -> byCode.put(criterion.code(), criterion));
        assertThat(byCode.get("media.playable").evidence()).containsEntry("fullyDecoded", true);
        assertThat(byCode.get("media.container_codec").evidence())
            .containsEntry("mp4Token", true).containsEntry("mimeVideoMp4", true)
            .containsEntry("videoCodec", "h264").containsEntry("audioCodec", "aac");
        assertThat(byCode.get("media.video_dimensions").evidence())
            .containsEntry("width", 1080).containsEntry("height", 1920).containsEntry("frameRate", 30);
        assertThat(byCode.get("media.audio_present").evidence())
            .containsEntry("probeAudioStream", true).containsEntry("assetAudioStream", true);
        assertThat(byCode.get("media.duration").evidence())
            .containsEntry("probeDurationMs", 25_800L).containsEntry("canvasDurationMs", 25_800L)
            .containsEntry("assetDurationMs", 25_800L).containsEntry("canvasDeltaMs", 0L)
            .containsEntry("assetDeltaMs", 0L);
        assertPolicyReview(byCode.get("content.must_include"));
        assertPolicyReview(byCode.get("content.prohibited"));
    }

    private TimelineInfrastructureProperties properties(Path ffmpeg, Path ffprobe) throws IOException {
        TimelineInfrastructureProperties properties = new TimelineInfrastructureProperties();
        properties.setEnabled(true);
        properties.setFfmpegPath(ffmpeg.toString());
        properties.setFfprobePath(ffprobe.toString());
        properties.setWorkRoot(Files.createDirectories(temporaryDirectory.resolve("quality-work")).toString());
        properties.setFontRoot(repositoryFile(
            "ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/resources/timeline/fonts").toString());
        properties.setProcessTimeout(Duration.ofSeconds(60));
        properties.setMaxOutputBytes(64L * 1024L * 1024L);
        return properties;
    }

    private static AppPrincipalSnapshotDTO principal(long ownerId) {
        Set<String> permissions = Set.of("aivideo:task:query", "aivideo:creation-asset:query");
        return new AppPrincipalSnapshotDTO(ownerId, "creator", "desktop", 1L, 1L, 1L, 1L,
            new AppWorkspaceSessionSnapshotDTO("workspace", "personal", 1L, "app_user", ownerId,
                "app_user", ownerId, "personal_creator", permissions, 1L, null));
    }

    private static Path requiredFile(String variable) {
        String value = System.getenv(variable);
        Assumptions.assumeTrue(value != null && !value.isBlank(), variable + " is not configured");
        Path path = Path.of(value).toAbsolutePath().normalize();
        Assumptions.assumeTrue(Files.isRegularFile(path), variable + " is unavailable");
        return path;
    }

    private static Path repositoryFile(String relativePath) {
        for (Path current = Path.of("").toAbsolutePath(); current != null; current = current.getParent()) {
            Path candidate = current.resolve(relativePath);
            if (Files.exists(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("required repository fixture is missing");
    }

    private static String decodeHex(String value) {
        return new String(HexFormat.of().parseHex(value), StandardCharsets.UTF_8);
    }

    private static String sha256(Path file) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
    }

    private static void assertPolicyReview(TimelineOutputQualityDTO.Criterion criterion) {
        assertThat(criterion.verdict()).isEqualTo(TimelineOutputQualityDTO.Verdict.REVIEW);
        assertThat(criterion.confidence()).isEqualTo(TimelineOutputQualityDTO.Confidence.LOW);
        assertThat(criterion.evidence()).containsEntry("configured", 0)
            .containsEntry("reason", "policy_unconfigured");
    }

    private record FileHandle(Path file, CreationAssetResolveDTO metadata) implements CreationMediaHandle {
        @Override public InputStream stream() {
            try {
                return Files.newInputStream(file);
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        }
        @Override public long offset() { return 0; }
        @Override public long length() { return metadata.sizeBytes(); }
        @Override public long totalSize() { return metadata.sizeBytes(); }
        @Override public void close() { }
    }
}
