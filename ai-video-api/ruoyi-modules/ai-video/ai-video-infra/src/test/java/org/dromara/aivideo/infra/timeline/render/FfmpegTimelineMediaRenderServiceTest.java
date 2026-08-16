package org.dromara.aivideo.infra.timeline.render;

import org.dromara.aivideo.creation.dto.CreationAssetResolveDTO;
import org.dromara.aivideo.creation.enums.CreationAssetType;
import org.dromara.aivideo.creation.enums.CreationAssetUsageOrigin;
import org.dromara.aivideo.creation.service.CreationMediaHandle;
import org.dromara.aivideo.infra.timeline.TimelineInfrastructureProperties;
import org.dromara.aivideo.infra.timeline.path.TimelinePathGuard;
import org.dromara.aivideo.infra.timeline.probe.MediaProbe;
import org.dromara.aivideo.infra.timeline.process.TimelineProcessExecutor;
import org.dromara.aivideo.infra.timeline.process.TimelineProcessRequest;
import org.dromara.aivideo.infra.timeline.process.TimelineProcessResult;
import org.dromara.aivideo.task.enums.AiTaskStage;
import org.dromara.aivideo.timeline.dto.TimelineMediaProbeDTO;
import org.dromara.aivideo.timeline.dto.TimelineMediaQualityInspectionDTO;
import org.dromara.aivideo.timeline.dto.TimelineProgressDTO;
import org.dromara.aivideo.timeline.dto.TimelineRenderCommandDTO;
import org.dromara.aivideo.timeline.dto.TimelineTextMeasureResultDTO;
import org.dromara.aivideo.timeline.dto.TimelineDocumentDTO;
import org.dromara.aivideo.timeline.dto.TimelineSubtitleElementDTO;
import org.dromara.aivideo.timeline.dto.TimelineTrackDTO;
import org.dromara.aivideo.timeline.enums.TimelineAssetUsageType;
import org.dromara.aivideo.timeline.enums.TimelineElementType;
import org.dromara.aivideo.timeline.enums.TimelineExecutionFailureCode;
import org.dromara.aivideo.timeline.enums.TimelineOutputQuality;
import org.dromara.aivideo.timeline.enums.TimelineTrackArea;
import org.dromara.aivideo.timeline.enums.TimelineTrackType;
import org.dromara.aivideo.timeline.exception.TimelineExecutionException;
import org.dromara.aivideo.timeline.service.TimelineRenderOutputHandle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("dev")
class FfmpegTimelineMediaRenderServiceTest {

    private static final Path TEST_BINARY = Path.of(System.getProperty("java.home"), "bin", "java.exe")
        .toAbsolutePath().normalize();

    @TempDir
    Path temporaryDirectory;

    private Path workRoot;
    private RecordingProcessExecutor processExecutor;
    private RecordingProber mediaProber;
    private Fixture fixture;

    @BeforeEach
    void setUp() throws Exception {
        workRoot = Files.createDirectories(temporaryDirectory.resolve("approved-work-root"));
        processExecutor = new RecordingProcessExecutor();
        mediaProber = new RecordingProber();
        fixture = Fixture.create();
    }

    @Test
    void springUsesThePublicPropertiesConstructorForTheRenderer() throws Exception {
        Path rendererWorkRoot = Files.createDirectories(temporaryDirectory.resolve("spring-renderer-work"));
        Path fontRoot = Path.of(FfmpegTimelineMediaRenderServiceTest.class
            .getResource("/timeline/fonts/font-registry.json").toURI()).getParent();

        new ApplicationContextRunner()
            .withPropertyValues("aivideo.timeline.enabled=true")
            .withBean(TimelineInfrastructureProperties.class,
                () -> productionProperties(rendererWorkRoot, fontRoot))
            .withUserConfiguration(RendererConfiguration.class)
            .run(context -> {
                assertThat(context.getStartupFailure()).isNull();
                assertThat(context).hasSingleBean(FfmpegTimelineMediaRenderService.class);
            });
    }

    @Test
    void probeDiscoversStreamsWithoutTrustingUnknownInputStreamFlags() {
        byte[] content = "base-video".getBytes(StandardCharsets.UTF_8);
        CreationAssetResolveDTO metadata = new CreationAssetResolveDTO("1", "video/mp4", sha256(content),
            CreationAssetType.VIDEO, TimelineAssetUsageType.BASE_VIDEO, content.length, null, null, null,
            false, false);
        CountingHandle handle = new CountingHandle(metadata, content);

        TimelineMediaProbeDTO result = service(512L * 1024L, ignored -> { }).probe(handle);

        assertThat(result.videoStream()).isTrue();
        assertThat(result.audioStream()).isTrue();
        assertThat(result.videoCodec()).isEqualTo("h264");
        assertThat(result.audioCodec()).isEqualTo("aac");
        assertThat(processExecutor.requests).isEmpty();
    }

    @Test
    void inspectsFactsAndFullyDecodesOneControlledMaterialization() throws Exception {
        byte[] content = "base-video".getBytes(StandardCharsets.UTF_8);
        CreationAssetResolveDTO metadata = new CreationAssetResolveDTO("1", "video/mp4", sha256(content),
            CreationAssetType.VIDEO, null, content.length, 3_000L, null, null,
            false, false, CreationAssetUsageOrigin.TIMELINE_RENDER_OUTPUT);
        CountingHandle handle = new CountingHandle(metadata, content);

        TimelineMediaQualityInspectionDTO result = service(512L * 1024L, ignored -> { }).inspectQuality(handle);

        assertThat(result.fullyDecoded()).isTrue();
        assertThat(result.probe().videoCodec()).isEqualTo("h264");
        assertThat(result.probe().audioCodec()).isEqualTo("aac");
        assertThat(handle.streamCalls()).isEqualTo(1);
        assertThat(processExecutor.requests).singleElement().satisfies(request -> {
            assertThat(request.command()).containsSubsequence("-loglevel", "error", "-xerror", "-i");
            assertThat(request.command()).endsWith("-map", "0:v?", "-map", "0:a?", "-f", "null", "-");
        });
        assertThat(children(workRoot)).isEmpty();
    }

    @Test
    void returnsProbeDurationFactsAcrossThe250MillisecondMetadataBoundary() throws Exception {
        byte[] content = "base-video".getBytes(StandardCharsets.UTF_8);
        for (long metadataDuration : List.of(2_750L, 2_749L)) {
            CreationAssetResolveDTO metadata = new CreationAssetResolveDTO("1", "video/mp4", sha256(content),
                CreationAssetType.VIDEO, null, content.length, metadataDuration, 1080, 1920,
                false, false, CreationAssetUsageOrigin.TIMELINE_RENDER_OUTPUT);

            TimelineMediaQualityInspectionDTO result = service(512L * 1024L, ignored -> { })
                .inspectQuality(new CountingHandle(metadata, content));

            assertThat(result.fullyDecoded()).isTrue();
            assertThat(result.probe().durationMs()).isEqualTo(3_000L);
            assertThat(result.probe().width()).isEqualTo(320);
            assertThat(result.probe().height()).isEqualTo(180);
        }
        assertThat(processExecutor.requests).hasSize(2);
        assertThat(children(workRoot)).isEmpty();
    }

    @Test
    void mapsFullDecodeFailureToSafeProcessErrorAndCleansWork() throws Exception {
        byte[] content = "base-video".getBytes(StandardCharsets.UTF_8);
        CreationAssetResolveDTO metadata = new CreationAssetResolveDTO("1", "video/mp4", sha256(content),
            CreationAssetType.VIDEO, null, content.length, 3_000L, null, null,
            false, false, CreationAssetUsageOrigin.TIMELINE_RENDER_OUTPUT);
        CountingHandle handle = new CountingHandle(metadata, content);
        processExecutor.nextStatus = TimelineProcessResult.Status.NON_ZERO_EXIT;

        assertThatThrownBy(() -> service(512L * 1024L, ignored -> { }).inspectQuality(handle))
            .isInstanceOfSatisfying(TimelineExecutionException.class, exception -> {
                assertThat(exception.code()).isEqualTo(TimelineExecutionFailureCode.PROCESS_FAILED);
                assertThat(exception.getMessage()).isEqualTo("timeline render process failed");
            });

        assertThat(children(workRoot)).isEmpty();
    }

    @Test
    void materializesControlledHandlesInPlanOrderRendersAndTransfersOnlyItsOutputDirectory() throws Exception {
        FfmpegTimelineMediaRenderService service = service(512L * 1024L, directory ->
            Files.writeString(directory.resolve("verified-font.otf"), "font", StandardCharsets.UTF_8));
        List<TimelineProgressDTO> progress = new ArrayList<>();

        TimelineRenderOutputHandle output = service.render(command(), fixture.mediaHandles(), progress::add, () -> false);

        assertThat(processExecutor.requests).hasSize(2);
        Path workDirectory = processExecutor.requests.getFirst().workingDirectory();
        assertThat(workDirectory.getParent()).isEqualTo(workRoot.toRealPath());
        assertThat(processExecutor.requests).allSatisfy(request ->
            assertThat(request.workingDirectory()).isEqualTo(workDirectory));
        assertThat(processExecutor.requests.getFirst().command()).contains(workDirectory.resolve("input-0002.mp4").toString(),
            workDirectory.resolve("pip-0001.mp4").toString());
        assertThat(processExecutor.requests.get(1).command()).contains(workDirectory.resolve("pip-0001.mp4").toString(),
            workDirectory.resolve("output.mp4").toString());
        assertThat(mediaProber.probedFileNames).containsExactly(
            "input-0001.mp4", "input-0002.mp4", "input-0003.wav", "output.mp4");
        assertThat(fixture.handles()).extracting(CountingHandle::streamCalls).containsExactly(1, 1, 1);
        assertThat(fixture.handles()).extracting(CountingHandle::closed).containsExactly(false, false, false);
        assertThat(progress).extracting(TimelineProgressDTO::stage).containsSubsequence(
            AiTaskStage.PREPARING_ASSETS, AiTaskStage.READING_ASSETS, AiTaskStage.BUILDING_ASS,
            AiTaskStage.BUILDING_RENDER_PLAN, AiTaskStage.ENCODING, AiTaskStage.VERIFYING_OUTPUT);
        assertThat(output.metadata()).extracting(result -> result.fileName(), result -> result.contentType(),
            result -> result.fileSize(), result -> result.durationMs(), result -> result.width(), result -> result.height(),
            result -> result.frameRate()).containsExactly("output.mp4", "video/mp4", 12L, 3_000L, 1080, 1920, 30);
        assertThat(output.metadata().sha256()).isEqualTo(sha256("final-render".getBytes(StandardCharsets.UTF_8)));
        try (InputStream stream = output.stream()) {
            assertThat(stream.readAllBytes()).isEqualTo("final-render".getBytes(StandardCharsets.UTF_8));
        }
        assertThatThrownBy(output::stream).isInstanceOf(IllegalStateException.class);

        output.close();

        assertThat(Files.exists(workDirectory)).isFalse();
        assertThat(fixture.handles()).extracting(CountingHandle::closed).containsExactly(false, false, false);
    }

    @Test
    void rejectsCorruptInputAndCleansOnlyItsUntransferredWorkDirectory() throws Exception {
        FfmpegTimelineMediaRenderService service = service(512L * 1024L, directory ->
            Files.writeString(directory.resolve("verified-font.otf"), "font", StandardCharsets.UTF_8));
        List<CountingHandle> corrupt = List.of(
            fixture.handles().getFirst().withMetadataSha256("b".repeat(64)),
            fixture.handles().get(1), fixture.handles().get(2));

        assertThatThrownBy(() -> service.render(command(), asMediaHandles(corrupt), ignored -> { }, () -> false))
            .isInstanceOfSatisfying(TimelineExecutionException.class, exception -> {
                assertThat(exception.code()).isEqualTo(TimelineExecutionFailureCode.INPUT_INVALID);
                assertThat(exception.getMessage()).doesNotContain(workRoot.toString());
            });

        assertThat(processExecutor.requests).isEmpty();
        assertThat(children(workRoot)).isEmpty();
        assertThat(corrupt).extracting(CountingHandle::closed).containsOnly(false);
    }

    @Test
    void mapsProcessFailureOutputLimitAndBadPostProbeToStableErrorsAndCleansWork() throws Exception {
        FfmpegTimelineMediaRenderService service = service(8L, directory ->
            Files.writeString(directory.resolve("verified-font.otf"), "font", StandardCharsets.UTF_8));
        processExecutor.nextStatus = TimelineProcessResult.Status.NON_ZERO_EXIT;

        assertThatThrownBy(() -> service.render(command(), fixture.mediaHandles(), ignored -> { }, () -> false))
            .isInstanceOfSatisfying(TimelineExecutionException.class,
                exception -> assertThat(exception.code()).isEqualTo(TimelineExecutionFailureCode.PROCESS_FAILED));
        assertThat(children(workRoot)).isEmpty();

        processExecutor = new RecordingProcessExecutor();
        processExecutor.nextStatus = TimelineProcessResult.Status.TIMED_OUT;
        fixture = Fixture.create();
        assertThatThrownBy(() -> service(512L * 1024L, directory ->
            Files.writeString(directory.resolve("verified-font.otf"), "font"))
            .render(command(), fixture.mediaHandles(), ignored -> { }, () -> false))
            .isInstanceOfSatisfying(TimelineExecutionException.class,
                exception -> assertThat(exception.code()).isEqualTo(TimelineExecutionFailureCode.TIMEOUT));
        assertThat(children(workRoot)).isEmpty();

        processExecutor = new RecordingProcessExecutor();
        fixture = Fixture.create();
        assertThatThrownBy(() -> service(8L, directory -> Files.writeString(directory.resolve("verified-font.otf"), "font"))
            .render(command(), fixture.mediaHandles(), ignored -> { }, () -> false))
            .isInstanceOfSatisfying(TimelineExecutionException.class,
                exception -> assertThat(exception.code()).isEqualTo(TimelineExecutionFailureCode.OUTPUT_INVALID));
        assertThat(children(workRoot)).isEmpty();

        processExecutor = new RecordingProcessExecutor();
        mediaProber = new RecordingProber();
        mediaProber.invalidFinalOutput = true;
        fixture = Fixture.create();
        assertThatThrownBy(() -> service(512L * 1024L, directory -> Files.writeString(directory.resolve("verified-font.otf"), "font"))
            .render(command(), fixture.mediaHandles(), ignored -> { }, () -> false))
            .isInstanceOfSatisfying(TimelineExecutionException.class,
                exception -> assertThat(exception.code()).isEqualTo(TimelineExecutionFailureCode.OUTPUT_INVALID));
        assertThat(Files.list(workRoot).toList()).isEmpty();
    }

    @Test
    void cancelsBeforeWorkAndNeverClosesCallerInputHandles() throws Exception {
        FfmpegTimelineMediaRenderService service = service(512L * 1024L, directory ->
            Files.writeString(directory.resolve("verified-font.otf"), "font", StandardCharsets.UTF_8));

        assertThatThrownBy(() -> service.render(command(), fixture.mediaHandles(), ignored -> { }, () -> true))
            .isInstanceOf(CancellationException.class);

        assertThat(processExecutor.requests).isEmpty();
        assertThat(fixture.handles()).extracting(CountingHandle::closed).containsExactly(false, false, false);
        assertThat(children(workRoot)).isEmpty();
    }

    @Test
    void turnsAProcessCancellationIntoCancellationAndCleansTheUntransferredWorkDirectory() throws Exception {
        processExecutor.nextStatus = TimelineProcessResult.Status.CANCELLED;
        FfmpegTimelineMediaRenderService service = service(512L * 1024L, directory ->
            Files.writeString(directory.resolve("verified-font.otf"), "font", StandardCharsets.UTF_8));

        assertThatThrownBy(() -> service.render(command(), fixture.mediaHandles(), ignored -> { }, () -> false))
            .isInstanceOf(CancellationException.class);

        assertThat(processExecutor.requests).hasSize(1);
        assertThat(children(workRoot)).isEmpty();
        assertThat(fixture.handles()).extracting(CountingHandle::closed).containsExactly(false, false, false);
    }

    @Test
    void stopsTheExecutorWhenProgressCallbackFails() {
        FfmpegTimelineMediaRenderService service = service(512L * 1024L, directory ->
            Files.writeString(directory.resolve("verified-font.otf"), "font", StandardCharsets.UTF_8));

        assertThatThrownBy(() -> service.render(command(), fixture.mediaHandles(), ignored -> {
            throw new IllegalStateException("lease lost");
        }, () -> false))
            .isInstanceOfSatisfying(TimelineExecutionException.class,
                exception -> assertThat(exception.code()).isEqualTo(TimelineExecutionFailureCode.CALLBACK_FAILED));

        assertThat(processExecutor.cancelCalls).isEqualTo(1);
        assertThat(processExecutor.requests).isEmpty();
        assertThat(fixture.handles()).extracting(CountingHandle::closed).containsExactly(false, false, false);
    }

    @Test
    void mapsFontStagingFailureWithoutLaunchingFfmpeg() {
        FfmpegTimelineMediaRenderService service = service(512L * 1024L, directory -> {
            throw new IOException("font unavailable");
        });

        assertThatThrownBy(() -> service.render(command(), fixture.mediaHandles(), ignored -> { }, () -> false))
            .isInstanceOfSatisfying(TimelineExecutionException.class,
                exception -> assertThat(exception.code()).isEqualTo(TimelineExecutionFailureCode.FONT_UNAVAILABLE));

        assertThat(processExecutor.requests).isEmpty();
        assertThat(fixture.handles()).extracting(CountingHandle::closed).containsExactly(false, false, false);
    }

    @Test
    void rejectsMissingGlyphCoverageBeforeMaterializingAnyMedia() {
        FfmpegTimelineMediaRenderService service = service(512L * 1024L, directory ->
            Files.writeString(directory.resolve("verified-font.otf"), "font", StandardCharsets.UTF_8), ignored ->
            new TimelineTextMeasureResultDTO("glyph-0", "noto_sans_cjk_sc_regular", "2.004", "a".repeat(64),
                "2e0198557dc5a00c4cdde6eb970a3c2282c298f169c3f6bd7349c275156a9e33", 1, 1, false));

        assertThatThrownBy(() -> service.render(commandWithSubtitle(), fixture.mediaHandles(), ignored -> { }, () -> false))
            .isInstanceOfSatisfying(TimelineExecutionException.class,
                exception -> assertThat(exception.code()).isEqualTo(TimelineExecutionFailureCode.FONT_UNAVAILABLE));

        assertThat(processExecutor.requests).isEmpty();
        assertThat(fixture.handles()).extracting(CountingHandle::streamCalls).containsExactly(0, 0, 0);
    }

    private FfmpegTimelineMediaRenderService service(long maxOutputBytes,
                                                     FfmpegTimelineMediaRenderService.FontStager fontStager) {
        return service(maxOutputBytes, fontStager, ignored -> new TimelineTextMeasureResultDTO("measure", "font", "1.0",
            "a".repeat(64), "b".repeat(64), 1, 1, true));
    }

    private FfmpegTimelineMediaRenderService service(long maxOutputBytes,
                                                     FfmpegTimelineMediaRenderService.FontStager fontStager,
                                                     FfmpegTimelineMediaRenderService.TextMeasurer textMeasurer) {
        return new FfmpegTimelineMediaRenderService(new TimelinePathGuard(workRoot), processExecutor, TEST_BINARY,
            Duration.ofSeconds(2), maxOutputBytes, (ignored, facts) -> fixture.plan(), mediaProber, fontStager,
            textMeasurer);
    }

    private static TimelineInfrastructureProperties productionProperties(Path rendererWorkRoot, Path fontRoot) {
        TimelineInfrastructureProperties properties = new TimelineInfrastructureProperties();
        properties.setEnabled(true);
        properties.setFfmpegPath(TEST_BINARY.toString());
        properties.setFfprobePath(TEST_BINARY.toString());
        properties.setWorkRoot(rendererWorkRoot.toString());
        properties.setFontRoot(fontRoot.toString());
        properties.setProcessTimeout(Duration.ofSeconds(2));
        properties.setMaxOutputBytes(512L * 1024L);
        properties.setPerUserConcurrencyLimit(1);
        properties.setSystemConcurrencyLimit(1);
        properties.setWorkerId("timeline-renderer-test");
        properties.setPollDelay(Duration.ofSeconds(1));
        properties.setRecoveryBatchLimit(1);
        return properties;
    }

    @Configuration(proxyBeanMethods = false)
    @Import(FfmpegTimelineMediaRenderService.class)
    static class RendererConfiguration {
    }

    private static TimelineRenderCommandDTO command() {
        return new TimelineRenderCommandDTO("task-1", "execution-1", "attempt-1", "version-1",
            "timeline-fonts-1", "2e0198557dc5a00c4cdde6eb970a3c2282c298f169c3f6bd7349c275156a9e33",
            null, null, List.of());
    }

    private static TimelineRenderCommandDTO commandWithSubtitle() {
        TimelineSubtitleElementDTO subtitle = new TimelineSubtitleElementDTO("subtitle-1", TimelineElementType.SUBTITLE,
            0, 1_000L, 0, true, false, "subtitle", "缺字", "缺字", 0, 2,
            "noto_sans_cjk_sc_regular", "2.004", "a".repeat(64), 24, "#FFFFFFFF", false, null,
            false, null, 0, "lower", "center");
        TimelineDocumentDTO document = new TimelineDocumentDTO("timeline-1", null,
            List.of(new TimelineTrackDTO("subtitle-track", TimelineTrackType.SUBTITLE, TimelineTrackArea.TOP,
                0, false, false, List.of(subtitle))));
        return new TimelineRenderCommandDTO("task-1", "execution-1", "attempt-1", "version-1",
            "timeline-fonts-1", "2e0198557dc5a00c4cdde6eb970a3c2282c298f169c3f6bd7349c275156a9e33",
            document, null, List.of());
    }

    private static MediaProbe videoProbe(long fileSize, boolean audio) {
        return new MediaProbe("mov,mp4,m4a,3gp,3g2,mj2", 3_000L, fileSize, 320, 180, 30,
            audio ? 48_000 : null, audio ? 2 : null, true, audio, "h264", audio ? "aac" : null,
            audio ? "format=mp4;video=h264;audio=aac" : "format=mp4;video=h264;audio=none");
    }

    private static MediaProbe audioProbe(long fileSize) {
        return new MediaProbe("wav", 3_000L, fileSize, null, null, null, 48_000, 2,
            false, true, null, "pcm_s16le", "format=wav;video=none;audio=pcm_s16le");
    }

    private static MediaProbe finalProbe(long fileSize, boolean valid) {
        return new MediaProbe("mov,mp4,m4a,3gp,3g2,mj2", 3_000L, fileSize, 1080, 1920, 30,
            valid ? 48_000 : null, valid ? 2 : null, true, valid, "h264", valid ? "aac" : null,
            valid ? "format=mp4;video=h264;audio=aac" : "format=mp4;video=h264;audio=none");
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new AssertionError(exception);
        }
    }

    private static List<Path> children(Path directory) throws IOException {
        try (var paths = Files.list(directory)) {
            return paths.toList();
        }
    }

    private static List<CreationMediaHandle> asMediaHandles(List<CountingHandle> handles) {
        return handles.stream().map(handle -> (CreationMediaHandle) handle).toList();
    }

    private final class RecordingProber implements FfmpegTimelineMediaRenderService.MediaProber {
        private final List<String> probedFileNames = new ArrayList<>();
        private boolean invalidFinalOutput;

        @Override
        public MediaProbe probe(String executionId, String attemptId, CreationAssetResolveDTO expected, Path input,
                                BooleanSupplier cancellationRequested) {
            probedFileNames.add(input.getFileName().toString());
            try {
                long size = Files.size(input);
                return switch (input.getFileName().toString()) {
                    case "input-0001.mp4", "input-0002.mp4" -> videoProbe(size, true);
                    case "input-0003.wav" -> audioProbe(size);
                    case "output.mp4" -> finalProbe(size, !invalidFinalOutput);
                    default -> throw new AssertionError("unexpected probe target: " + input.getFileName());
                };
            } catch (IOException exception) {
                throw new AssertionError(exception);
            }
        }
    }

    private static final class RecordingProcessExecutor implements TimelineProcessExecutor {
        private final List<TimelineProcessRequest> requests = new ArrayList<>();
        private TimelineProcessResult.Status nextStatus = TimelineProcessResult.Status.SUCCEEDED;
        private int cancelCalls;

        @Override
        public TimelineProcessResult execute(TimelineProcessRequest request, BooleanSupplier cancellationRequested) {
            requests.add(request);
            if (nextStatus == TimelineProcessResult.Status.SUCCEEDED && !"-".equals(request.command().getLast())) {
                Path output = Path.of(request.command().getLast());
                try {
                    Files.write(output, output.getFileName().toString().startsWith("pip-")
                        ? "pip-tail".getBytes(StandardCharsets.UTF_8)
                        : "final-render".getBytes(StandardCharsets.UTF_8));
                } catch (IOException exception) {
                    throw new AssertionError(exception);
                }
            }
            return new TimelineProcessResult(nextStatus, nextStatus == TimelineProcessResult.Status.SUCCEEDED ? 0 : 1,
                new byte[0], 0, Duration.ofMillis(1));
        }

        @Override
        public void cancel(String executionId, String attemptId) {
            cancelCalls++;
        }
    }

    private record Fixture(TimelineRenderPlan plan, List<CountingHandle> handles) {
        private List<CreationMediaHandle> mediaHandles() {
            return asMediaHandles(handles);
        }

        private static Fixture create() {
            byte[] base = "base-video".getBytes(StandardCharsets.UTF_8);
            byte[] pip = "pip-video".getBytes(StandardCharsets.UTF_8);
            byte[] audio = "primary-audio".getBytes(StandardCharsets.UTF_8);
            CountingHandle baseHandle = new CountingHandle(asset("1", "video/mp4", sha256(base), CreationAssetType.VIDEO,
                TimelineAssetUsageType.BASE_VIDEO, base.length), base);
            CountingHandle pipHandle = new CountingHandle(asset("2", "video/mp4", sha256(pip), CreationAssetType.VIDEO,
                TimelineAssetUsageType.PIP_VIDEO, pip.length), pip);
            CountingHandle audioHandle = new CountingHandle(asset("3", "audio/wav", sha256(audio), CreationAssetType.AUDIO,
                TimelineAssetUsageType.PRIMARY_AUDIO, audio.length), audio);
            TimelineRenderPlan plan = new TimelineRenderPlan("execution-1", "attempt-1", 3_000L,
                List.of(
                    new TimelineRenderPlan.Input("input-0001.mp4", "1", sha256(base), base.length,
                        CreationAssetType.VIDEO, TimelineAssetUsageType.BASE_VIDEO, false),
                    new TimelineRenderPlan.Input("input-0002.mp4", "2", sha256(pip), pip.length,
                        CreationAssetType.VIDEO, TimelineAssetUsageType.PIP_VIDEO, true),
                    new TimelineRenderPlan.Input("input-0003.wav", "3", sha256(audio), audio.length,
                        CreationAssetType.AUDIO, TimelineAssetUsageType.PRIMARY_AUDIO, false)),
                List.of(
                    new TimelineRenderPlan.RenderInput("input-0001.mp4", CreationAssetType.VIDEO,
                        TimelineAssetUsageType.BASE_VIDEO, false),
                    new TimelineRenderPlan.RenderInput("pip-0001.mp4", CreationAssetType.VIDEO,
                        TimelineAssetUsageType.PIP_VIDEO, true),
                    new TimelineRenderPlan.RenderInput("input-0003.wav", CreationAssetType.AUDIO,
                        TimelineAssetUsageType.PRIMARY_AUDIO, false)),
                List.of(new TimelineRenderPlan.PipTail("input-0002.mp4", "pip-0001.mp4", 1_000L, 3_000L)),
                "[Script Info]\nTitle: controlled", "[0:v]null[vout]\nanullsrc[aout]", TimelineOutputQuality.STANDARD);
            return new Fixture(plan, List.of(baseHandle, pipHandle, audioHandle));
        }

        private static CreationAssetResolveDTO asset(String assetId, String mimeType, String sha256,
                                                      CreationAssetType type, TimelineAssetUsageType usage, long size) {
            return new CreationAssetResolveDTO(assetId, mimeType, sha256, type, usage, size, 3_000L,
                type == CreationAssetType.AUDIO ? null : 320, type == CreationAssetType.AUDIO ? null : 180,
                type != CreationAssetType.AUDIO, type != CreationAssetType.IMAGE);
        }
    }

    private static final class CountingHandle implements CreationMediaHandle {
        private final CreationAssetResolveDTO metadata;
        private final byte[] bytes;
        private int streamCalls;
        private boolean closed;

        private CountingHandle(CreationAssetResolveDTO metadata, byte[] bytes) {
            this.metadata = metadata;
            this.bytes = bytes.clone();
        }

        private CountingHandle withMetadataSha256(String sha256) {
            return new CountingHandle(new CreationAssetResolveDTO(metadata.assetId(), metadata.mimeType(), sha256,
                metadata.assetType(), metadata.usageType(), metadata.sizeBytes(), metadata.durationMs(), metadata.width(),
                metadata.height(), metadata.hasVideoStream(), metadata.hasAudioStream()), bytes);
        }

        @Override
        public CreationAssetResolveDTO metadata() {
            return metadata;
        }

        @Override
        public InputStream stream() {
            streamCalls++;
            return new ByteArrayInputStream(bytes);
        }

        @Override
        public long offset() {
            return 0;
        }

        @Override
        public long length() {
            return bytes.length;
        }

        @Override
        public long totalSize() {
            return bytes.length;
        }

        @Override
        public void close() {
            closed = true;
        }

        private int streamCalls() {
            return streamCalls;
        }

        private boolean closed() {
            return closed;
        }
    }
}
