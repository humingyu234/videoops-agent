package org.dromara.aivideo.infra.timeline;

import org.dromara.aivideo.creation.dto.CreationAssetResolveDTO;
import org.dromara.aivideo.creation.enums.CreationAssetType;
import org.dromara.aivideo.creation.service.CreationMediaHandle;
import org.dromara.aivideo.infra.timeline.path.TimelinePathGuard;
import org.dromara.aivideo.infra.timeline.probe.FfprobeClient;
import org.dromara.aivideo.infra.timeline.probe.MediaProbe;
import org.dromara.aivideo.infra.timeline.process.JdkTimelineProcessExecutor;
import org.dromara.aivideo.infra.timeline.process.TimelineProcessRequest;
import org.dromara.aivideo.infra.timeline.process.TimelineProcessResult;
import org.dromara.aivideo.infra.timeline.render.FfmpegTimelineMediaRenderService;
import org.dromara.aivideo.timeline.dto.TimelineAssetReferenceDTO;
import org.dromara.aivideo.timeline.dto.TimelineDocumentDTO;
import org.dromara.aivideo.timeline.dto.TimelineMediaProbeDTO;
import org.dromara.aivideo.timeline.dto.TimelineOutputConfigDTO;
import org.dromara.aivideo.timeline.dto.TimelineRenderCommandDTO;
import org.dromara.aivideo.timeline.dto.TimelineRenderResultDTO;
import org.dromara.aivideo.timeline.dto.TimelineTrackDTO;
import org.dromara.aivideo.timeline.enums.TimelineAssetUsageType;
import org.dromara.aivideo.timeline.enums.TimelineOutputQuality;
import org.dromara.aivideo.timeline.service.TimelineRenderOutputHandle;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/** Local real-media gate. It consumes checked-in fixtures and never downloads or re-encodes them. */
@Tag("dev")
class TimelineRealMediaIT {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String FONT_REGISTRY_SHA256 = "2e0198557dc5a00c4cdde6eb970a3c2282c298f169c3f6bd7349c275156a9e33";

    @TempDir
    Path temporaryDirectory;

    @Test
    void rendersCheckedInMediaWithAllTimelineElementTypesAndVerifiesOutputFacts() throws Exception {
        Path ffmpeg = requiredExecutable("AIVIDEO_TIMELINE_FFMPEG_PATH");
        Path ffprobe = requiredExecutable("AIVIDEO_TIMELINE_FFPROBE_PATH");
        Path workRoot = Files.createDirectories(temporaryDirectory.resolve("approved-work"));
        FfmpegTimelineMediaRenderService renderer = new FfmpegTimelineMediaRenderService(
            properties(ffmpeg, ffprobe, workRoot));
        List<FileHandle> inputs = inputs();
        try {
            TimelineMediaProbeDTO primaryProbe = renderer.probe(handle("90071992547410004", resource("primary.wav"),
                CreationAssetType.AUDIO, TimelineAssetUsageType.PRIMARY_AUDIO, 3_000L,
                null, null, false, true));
            assertThat(primaryProbe.mediaType()).isEqualTo("audio");
            assertThat(primaryProbe.audioStream()).isTrue();
            TimelineRenderCommandDTO textCommand = command(document(true), "execution-text", "attempt-text");
            TimelineRenderOutputHandle rendered = renderer.render(textCommand, asHandles(inputs), ignored -> { }, () -> false);
            TimelineRenderResultDTO renderedMetadata = rendered.metadata();
            Path renderedFile = copyOutput(rendered, workRoot.resolve("with-text.mp4"));

            TimelineRenderOutputHandle withoutText = renderer.render(
                command(document(false), "execution-plain", "attempt-plain"), asHandles(inputs), ignored -> { }, () -> false);
            Path plainFile = copyOutput(withoutText, workRoot.resolve("without-text.mp4"));

            MediaProbe outputProbe = probeOutput(ffprobe, workRoot, renderedFile, renderedMetadata);
            assertThat(outputProbe.formatName().split(",")).contains("mp4");
            assertThat(outputProbe.videoCodec()).isEqualTo("h264");
            assertThat(outputProbe.audioCodec()).isEqualTo("aac");
            assertThat(outputProbe.width()).isEqualTo(1080);
            assertThat(outputProbe.height()).isEqualTo(1920);
            assertThat(outputProbe.frameRate()).isEqualTo(30);
            assertThat(outputProbe.videoStream()).isTrue();
            assertThat(outputProbe.audioStream()).isTrue();
            assertThat(Math.abs(outputProbe.durationMs() - 3_000L)).isLessThanOrEqualTo(100L);

            assertThat(frameDigest(ffmpeg, workRoot, renderedFile, "text-frame"))
                .isNotEqualTo(frameDigest(ffmpeg, workRoot, plainFile, "plain-frame"));
        } finally {
            inputs.forEach(FileHandle::close);
        }
    }

    private TimelineInfrastructureProperties properties(Path ffmpeg, Path ffprobe, Path workRoot) {
        TimelineInfrastructureProperties properties = new TimelineInfrastructureProperties();
        properties.setEnabled(true);
        properties.setFfmpegPath(ffmpeg.toString());
        properties.setFfprobePath(ffprobe.toString());
        properties.setWorkRoot(workRoot.toString());
        properties.setFontRoot(repositoryFile(
            "ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/resources/timeline/fonts").toString());
        properties.setProcessTimeout(Duration.ofSeconds(60));
        properties.setMaxOutputBytes(256L * 1024L * 1024L);
        properties.setPerUserConcurrencyLimit(1);
        properties.setSystemConcurrencyLimit(1);
        properties.setWorkerId("timeline-real-media-it");
        properties.setPollDelay(Duration.ofSeconds(1));
        properties.setRecoveryBatchLimit(1);
        properties.validate();
        return properties;
    }

    private static TimelineRenderCommandDTO command(TimelineDocumentDTO timeline, String executionId, String attemptId) {
        List<TimelineAssetReferenceDTO> references = List.of(
            reference("90071992547410003", TimelineAssetUsageType.BASE_VIDEO, "main_video_0001", resource("base-with-audio.mp4")),
            reference("90071992547410001", TimelineAssetUsageType.IMAGE, "image_0001", resource("overlay.png")),
            reference("90071992547410002", TimelineAssetUsageType.PIP_VIDEO, "pip_0001", resource("pip-silent.mp4")),
            reference("90071992547410005", TimelineAssetUsageType.BACKGROUND_MUSIC, "audio_bgm_0001", resource("bgm.wav")),
            reference("90071992547410006", TimelineAssetUsageType.SOUND_EFFECT, "audio_sfx_0001", resource("sfx.wav")));
        return new TimelineRenderCommandDTO("real-media-task", executionId, attemptId, "version-1",
            "timeline-fonts-1", FONT_REGISTRY_SHA256, timeline,
            new TimelineOutputConfigDTO("match_canvas", 30, TimelineOutputQuality.STANDARD), references);
    }

    private static TimelineAssetReferenceDTO reference(String assetId, TimelineAssetUsageType usageType,
                                                        String elementId, Path file) {
        try {
            return new TimelineAssetReferenceDTO(assetId, usageType, List.of(elementId), sha256(file), Files.size(file));
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static TimelineDocumentDTO document(boolean includeText) throws IOException {
        ObjectNode timeline = (ObjectNode) JSON.readTree(repositoryFile(
            "docs/contracts/creation-timeline/timeline-draft.example.json").toFile()).required("timeline");
        ((ObjectNode) timeline.required("canvas")).put("durationMs", 3_000);
        for (JsonNode track : timeline.required("tracks")) {
            ObjectNode element = (ObjectNode) track.required("elements").get(0);
            switch (track.required("trackType").asString()) {
                case "fancy_text" -> window(element, 600, 2_400);
                case "subtitle" -> window(element, 0, 3_000);
                case "visual_effect" -> {
                    window(element, 0, 500);
                    element.put("durationMs", 500);
                }
                case "image_overlay" -> window(element, 700, 2_500);
                case "pip_video" -> {
                    window(element, 1_000, 2_500);
                    element.put("sourceDurationMs", 1_000);
                }
                case "main_video" -> {
                    window(element, 0, 3_000);
                    element.put("sourceDurationMs", 3_000);
                }
                case "primary_audio" -> { }
                case "background_music" -> {
                    window(element, 0, 3_000);
                    element.put("sourceDurationMs", 1_200);
                    element.put("sourceEndMs", 1_200);
                }
                case "sound_effect" -> {
                    window(element, 1_400, 1_600);
                    element.put("sourceDurationMs", 200);
                    element.put("sourceEndMs", 200);
                }
                default -> throw new IllegalStateException("unexpected C0 track");
            }
        }
        TimelineDocumentDTO document = JSON.treeToValue(timeline, TimelineDocumentDTO.class);
        return new TimelineDocumentDTO(document.schemaVersion(), document.canvas(), document.tracks().stream()
            .filter(track -> !"primary_audio".equals(track.trackType().value())
                && (includeText || (!"subtitle".equals(track.trackType().value())
                && !"fancy_text".equals(track.trackType().value()))))
            .map(track -> new TimelineTrackDTO(track.trackId(), track.trackType(), track.area(), track.order(),
                track.locked(), track.muted(), track.elements()))
            .toList());
    }

    private static void window(ObjectNode element, long startMs, long endMs) {
        element.put("startMs", startMs);
        element.put("endMs", endMs);
    }

    private static List<FileHandle> inputs() {
        return List.of(
            handle("90071992547410003", resource("base-with-audio.mp4"), CreationAssetType.VIDEO,
                TimelineAssetUsageType.BASE_VIDEO, 3_000L, 320, 180, true, true),
            handle("90071992547410001", resource("overlay.png"), CreationAssetType.IMAGE,
                TimelineAssetUsageType.IMAGE, null, 64, 64, true, false),
            handle("90071992547410002", resource("pip-silent.mp4"), CreationAssetType.VIDEO,
                TimelineAssetUsageType.PIP_VIDEO, 1_000L, 160, 90, true, false),
            handle("90071992547410005", resource("bgm.wav"), CreationAssetType.AUDIO,
                TimelineAssetUsageType.BACKGROUND_MUSIC, 1_200L, null, null, false, true),
            handle("90071992547410006", resource("sfx.wav"), CreationAssetType.AUDIO,
                TimelineAssetUsageType.SOUND_EFFECT, 200L, null, null, false, true));
    }

    private static FileHandle handle(String assetId, Path file, CreationAssetType type,
                                     TimelineAssetUsageType usageType, Long durationMs, Integer width, Integer height,
                                     boolean video, boolean audio) {
        try {
            String mimeType = switch (type) {
                case VIDEO -> "video/mp4";
                case IMAGE -> "image/png";
                case AUDIO -> "audio/wav";
            };
            return new FileHandle(file, new CreationAssetResolveDTO(assetId, mimeType, sha256(file), type, usageType,
                Files.size(file), durationMs, width, height, video, audio));
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static List<CreationMediaHandle> asHandles(List<FileHandle> handles) {
        return handles.stream().map(handle -> (CreationMediaHandle) handle).toList();
    }

    private static Path copyOutput(TimelineRenderOutputHandle output, Path target) throws IOException {
        try (output; InputStream stream = output.stream()) {
            Files.copy(stream, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return target;
    }

    private static MediaProbe probeOutput(Path ffprobe, Path workRoot, Path output,
                                          TimelineRenderResultDTO rendered) {
        CreationAssetResolveDTO expected = new CreationAssetResolveDTO("output", "video/mp4", rendered.sha256(),
            CreationAssetType.VIDEO, TimelineAssetUsageType.BASE_VIDEO, rendered.fileSize(), rendered.durationMs(),
            rendered.width(), rendered.height(), true, true);
        FfprobeClient probe = new FfprobeClient(new TimelinePathGuard(workRoot),
            new JdkTimelineProcessExecutor(new TimelinePathGuard(workRoot), List.of(ffprobe)), ffprobe,
            Duration.ofSeconds(30));
        return probe.probe("verify-output", "probe-output", expected, output, () -> false);
    }

    private static String frameDigest(Path ffmpeg, Path workRoot, Path output, String attemptId) throws Exception {
        JdkTimelineProcessExecutor executor = new JdkTimelineProcessExecutor(new TimelinePathGuard(workRoot),
            List.of(ffmpeg));
        TimelineProcessResult result = executor.execute(new TimelineProcessRequest("frame-digest", attemptId,
            List.of(ffmpeg.toString(), "-hide_banner", "-loglevel", "error", "-ss", "1.000", "-i",
                output.toString(), "-frames:v", "1", "-f", "md5", "-"), workRoot,
            Map.of("LANG", "C", "TZ", "UTC"), Duration.ofSeconds(30), 32 * 1024, 32 * 1024), () -> false);
        assertThat(result.status()).isEqualTo(TimelineProcessResult.Status.SUCCEEDED);
        String digest = new String(result.stdout(), java.nio.charset.StandardCharsets.UTF_8).trim();
        assertThat(digest).startsWith("MD5=");
        return digest;
    }

    private static Path requiredExecutable(String variable) {
        String value = System.getenv(variable);
        assertThat(value).as(variable).isNotBlank();
        Path path = Path.of(value).toAbsolutePath().normalize();
        assertThat(path).isRegularFile();
        return path;
    }

    private static Path resource(String name) {
        try {
            return Path.of(Objects.requireNonNull(TimelineRealMediaIT.class.getResource("/timeline/media/" + name),
                name).toURI());
        } catch (Exception exception) {
            throw new IllegalStateException("checked-in media fixture is missing", exception);
        }
    }

    private static Path repositoryFile(String relativePath) {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(relativePath);
            if (Files.exists(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("repository file is missing");
    }

    private static String sha256(Path file) {
        try (InputStream input = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            for (int read; (read = input.read(buffer)) != -1; ) {
                digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static final class FileHandle implements CreationMediaHandle {
        private final Path file;
        private final CreationAssetResolveDTO metadata;
        private boolean closed;

        private FileHandle(Path file, CreationAssetResolveDTO metadata) {
            this.file = file;
            this.metadata = metadata;
        }

        @Override
        public CreationAssetResolveDTO metadata() {
            return metadata;
        }

        @Override
        public InputStream stream() {
            if (closed) {
                throw new IllegalStateException("media handle is closed");
            }
            try {
                return Files.newInputStream(file);
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        }

        @Override public long offset() { return 0; }
        @Override public long length() { return metadata.sizeBytes(); }
        @Override public long totalSize() { return metadata.sizeBytes(); }
        @Override public void close() { closed = true; }
    }
}
