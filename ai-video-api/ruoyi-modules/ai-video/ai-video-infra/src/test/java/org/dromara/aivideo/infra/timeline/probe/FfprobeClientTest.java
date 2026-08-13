package org.dromara.aivideo.infra.timeline.probe;

import org.dromara.aivideo.creation.dto.CreationAssetResolveDTO;
import org.dromara.aivideo.creation.enums.CreationAssetType;
import org.dromara.aivideo.infra.timeline.path.TimelinePathGuard;
import org.dromara.aivideo.infra.timeline.process.TimelineProcessExecutor;
import org.dromara.aivideo.infra.timeline.process.TimelineProcessRequest;
import org.dromara.aivideo.infra.timeline.process.TimelineProcessResult;
import org.dromara.aivideo.timeline.enums.TimelineAssetUsageType;
import org.dromara.aivideo.timeline.enums.TimelineExecutionFailureCode;
import org.dromara.aivideo.timeline.exception.TimelineExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("dev")
class FfprobeClientTest {

    @TempDir
    Path temporaryDirectory;

    private Path workRoot;
    private TimelinePathGuard pathGuard;
    private RecordingProcessExecutor processExecutor;
    private FfprobeClient client;

    @BeforeEach
    void setUp() throws Exception {
        workRoot = Files.createDirectories(temporaryDirectory.resolve("timeline-work"));
        pathGuard = new TimelinePathGuard(workRoot);
        processExecutor = new RecordingProcessExecutor();
        client = new FfprobeClient(pathGuard, processExecutor, javaExecutable(), Duration.ofSeconds(2));
    }

    @Test
    void probesValidVideoAudioAndImageWithAFixedLocalOnlyCommand() throws Exception {
        Path video = fixture(shellLikeFileName());
        Path audio = fixture("audio.media");
        Path image = fixture("image.media");
        processExecutor.enqueue(success("""
            {
              "unknown_root": {"must_not": "be_retained"},
              "format": {"format_name": "mov,mp4,m4a,3gp,3g2,mj2", "duration": "10.042", "tags": {"title": "ignored"}},
              "streams": [
                {"codec_type": "video", "codec_name": "h264", "width": 1920, "height": 1080,
                 "r_frame_rate": "30000/1001", "unknown_stream": true},
                {"codec_type": "audio", "codec_name": "aac", "sample_rate": "48000", "channels": 2}
              ]
            }
            """));
        processExecutor.enqueue(success("""
            {
              "format": {"format_name": "mp3", "duration": "20.000"},
              "streams": [{"codec_type": "audio", "codec_name": "mp3", "sample_rate": "44100", "channels": 2}]
            }
            """));
        processExecutor.enqueue(success("""
            {
              "format": {"format_name": "image2"},
              "streams": [{"codec_type": "video", "codec_name": "png", "width": 1280, "height": 720,
                           "r_frame_rate": "25/1"}]
            }
            """));

        MediaProbe videoProbe = probe(video, CreationAssetType.VIDEO);
        MediaProbe audioProbe = probe(audio, CreationAssetType.AUDIO);
        MediaProbe imageProbe = probe(image, CreationAssetType.IMAGE);

        assertThat(videoProbe).extracting(MediaProbe::formatName, MediaProbe::durationMs, MediaProbe::fileSize,
                MediaProbe::width, MediaProbe::height, MediaProbe::frameRate, MediaProbe::sampleRate,
                MediaProbe::channels, MediaProbe::videoStream, MediaProbe::audioStream, MediaProbe::videoCodec,
                MediaProbe::audioCodec)
            .containsExactly("mov,mp4,m4a,3gp,3g2,mj2", 10_042L, Files.size(video), 1920, 1080, 30, 48_000, 2,
                true, true, "h264", "aac");
        assertThat(audioProbe).extracting(MediaProbe::durationMs, MediaProbe::width, MediaProbe::height,
                MediaProbe::sampleRate, MediaProbe::channels, MediaProbe::videoStream, MediaProbe::audioStream)
            .containsExactly(20_000L, null, null, 44_100, 2, false, true);
        assertThat(imageProbe).extracting(MediaProbe::durationMs, MediaProbe::width, MediaProbe::height,
                MediaProbe::frameRate, MediaProbe::videoStream, MediaProbe::audioStream)
            .containsExactly(0L, 1280, 720, 25, true, false);
        assertThat(videoProbe.securitySummary()).isEqualTo("format=mov,mp4,m4a,3gp,3g2,mj2;video=h264;audio=aac");

        assertThat(processExecutor.requests).hasSize(3);
        TimelineProcessRequest firstRequest = processExecutor.requests.getFirst();
        assertThat(firstRequest.workingDirectory()).isEqualTo(workRoot.toRealPath());
        assertThat(firstRequest.command()).containsExactly(
            javaExecutable().toString(), "-v", "error", "-hide_banner", "-show_entries",
            "format=format_name,duration:stream=codec_type,codec_name,width,height,r_frame_rate,sample_rate,channels",
            "-of", "json", "-protocol_whitelist", "file,pipe", "-i", video.toRealPath().toString());
        assertThat(firstRequest.command()).doesNotContain("http", "https", "concat", "crypto", "data");
        assertThatThrownBy(() -> firstRequest.command().add("injected"))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsDamagedMediaAndRequiredStreamMismatchesWithoutLeakingPaths() throws Exception {
        Path fixture = fixture("private-input.mp4");
        processExecutor.enqueue(outcome(TimelineProcessResult.Status.NON_ZERO_EXIT));

        assertFailure(TimelineExecutionFailureCode.INPUT_INVALID,
            () -> probe(fixture, CreationAssetType.VIDEO), fixture);

        processExecutor.enqueue(success("""
            {"format":{"format_name":"mp3","duration":"1"},
             "streams":[{"codec_type":"audio","codec_name":"mp3","sample_rate":"48000","channels":2}]}
            """));
        assertFailure(TimelineExecutionFailureCode.INPUT_INVALID,
            () -> probe(fixture, CreationAssetType.VIDEO), fixture);

        processExecutor.enqueue(success("""
            {"format":{"format_name":"image2"},
             "streams":[{"codec_type":"video","codec_name":"png","width":100,"height":100,
                         "r_frame_rate":"25/1"}]}
            """));
        assertFailure(TimelineExecutionFailureCode.INPUT_INVALID,
            () -> probe(fixture, CreationAssetType.AUDIO), fixture);

        processExecutor.enqueue(success("not-json"));
        assertFailure(TimelineExecutionFailureCode.RESPONSE_INVALID,
            () -> probe(fixture, CreationAssetType.VIDEO), fixture);
    }

    @Test
    void rejectsOutOfRangeOrMalformedFactsButIgnoresUnknownFields() throws Exception {
        Path fixture = fixture("facts.media");
        processExecutor.enqueue(success("""
            {"format":{"format_name":"matroska","duration":"1","unrelated":{"nested":true}},
             "streams":[{"codec_type":"video","codec_name":"vp9","width":100,"height":100,
                         "r_frame_rate":"30/1","extra":"ignored"}]}
            """));
        assertThat(probe(fixture, CreationAssetType.VIDEO).formatName()).isEqualTo("matroska");

        assertInvalidVideoFact(fixture, "120.001", 100, 100, "30/1");
        assertInvalidVideoFact(fixture, "1", 3841, 100, "30/1");
        assertInvalidVideoFact(fixture, "1", 100, 2161, "30/1");
        assertInvalidVideoFact(fixture, "1", 100, 100, "61/1");
        assertInvalidVideoFact(fixture, "1", 100, 100, "0/0");

        processExecutor.enqueue(success("""
            {"format":{"format_name":true,"duration":"1"},
             "streams":[{"codec_type":"video","codec_name":true,"width":100,"height":100,
                         "r_frame_rate":"30/1"}]}
            """));
        assertFailure(TimelineExecutionFailureCode.INPUT_INVALID,
            () -> probe(fixture, CreationAssetType.VIDEO), fixture);

        processExecutor.enqueue(success("""
            {"format":{"format_name":"wav","duration":"1"},
             "streams":[{"codec_type":"audio","codec_name":"pcm_s16le","sample_rate":"192001","channels":2}]}
            """));
        assertFailure(TimelineExecutionFailureCode.INPUT_INVALID,
            () -> probe(fixture, CreationAssetType.AUDIO), fixture);
        processExecutor.enqueue(success("""
            {"format":{"format_name":"wav","duration":"1"},
             "streams":[{"codec_type":"audio","codec_name":"pcm_s16le","sample_rate":"48000","channels":9}]}
            """));
        assertFailure(TimelineExecutionFailureCode.INPUT_INVALID,
            () -> probe(fixture, CreationAssetType.AUDIO), fixture);
    }

    @Test
    void mapsBoundedProcessOutcomesToSafeFailures() throws Exception {
        Path fixture = fixture("bounded.media");
        processExecutor.enqueue(outcome(TimelineProcessResult.Status.OUTPUT_LIMIT_EXCEEDED));
        assertFailure(TimelineExecutionFailureCode.RESPONSE_TOO_LARGE,
            () -> probe(fixture, CreationAssetType.VIDEO), fixture);

        processExecutor.enqueue(outcome(TimelineProcessResult.Status.TIMED_OUT));
        assertFailure(TimelineExecutionFailureCode.TIMEOUT,
            () -> probe(fixture, CreationAssetType.VIDEO), fixture);

        processExecutor.enqueue(outcome(TimelineProcessResult.Status.START_FAILED));
        assertFailure(TimelineExecutionFailureCode.PROCESS_FAILED,
            () -> probe(fixture, CreationAssetType.VIDEO), fixture);

        processExecutor.enqueue(success("{" + " ".repeat(256 * 1024) + "}"));
        assertFailure(TimelineExecutionFailureCode.RESPONSE_TOO_LARGE,
            () -> probe(fixture, CreationAssetType.VIDEO), fixture);
    }

    @Test
    void rejectsNetworkProtocolPipeAndDeviceCandidatesBeforeStartingAProcess() throws Exception {
        Path validFixture = fixture("ordinary-local-file.media");
        List<Path> rejected = List.of(
            unsafeProtocolPath("http://untrusted.invalid/input.mp4"),
            unsafeProtocolPath("https://untrusted.invalid/input.mp4"),
            unsafeProtocolPath("concat:untrusted.txt"),
            unsafeProtocolPath("crypto:payload"),
            unsafeProtocolPath("data:application/octet-stream;base64,AAAA"),
            Path.of("\\\\.\\pipe\\timeline-probe"),
            devicePath());

        for (Path candidate : rejected) {
            assertFailure(TimelineExecutionFailureCode.INPUT_INVALID,
                () -> client.probe("execution-1", "attempt-1", metadata(CreationAssetType.VIDEO, validFixture),
                    candidate, () -> false), candidate);
        }

        assertThat(processExecutor.requests).isEmpty();
    }

    private void assertInvalidVideoFact(Path fixture, String duration, int width, int height, String frameRate)
        throws Exception {
        processExecutor.enqueue(success("""
            {"format":{"format_name":"mp4","duration":"%s"},
             "streams":[{"codec_type":"video","codec_name":"h264","width":%d,"height":%d,
                         "r_frame_rate":"%s"}]}
            """.formatted(duration, width, height, frameRate)));
        assertFailure(TimelineExecutionFailureCode.INPUT_INVALID,
            () -> probe(fixture, CreationAssetType.VIDEO), fixture);
    }

    private MediaProbe probe(Path input, CreationAssetType assetType) throws Exception {
        return client.probe("execution-1", "attempt-1", metadata(assetType, input), input, () -> false);
    }

    private CreationAssetResolveDTO metadata(CreationAssetType assetType, Path input) throws Exception {
        return new CreationAssetResolveDTO("asset-1", "application/octet-stream", "a".repeat(64), assetType,
            usage(assetType), Files.size(input), null, null, null, false, false);
    }

    private static TimelineAssetUsageType usage(CreationAssetType assetType) {
        return switch (assetType) {
            case VIDEO -> TimelineAssetUsageType.BASE_VIDEO;
            case AUDIO -> TimelineAssetUsageType.PRIMARY_AUDIO;
            case IMAGE -> TimelineAssetUsageType.IMAGE;
        };
    }

    private Path fixture(String fileName) throws Exception {
        return Files.writeString(workRoot.resolve(fileName), "fixture", StandardCharsets.UTF_8);
    }

    private void assertFailure(TimelineExecutionFailureCode expectedCode, ThrowingOperation operation, Path secret) {
        assertThatThrownBy(operation::run)
            .isInstanceOfSatisfying(TimelineExecutionException.class, exception -> {
                assertThat(exception.code()).isEqualTo(expectedCode);
                assertThat(exception.getMessage()).doesNotContain(secret.toString());
            });
    }

    private static TimelineProcessResult success(String output) {
        return new TimelineProcessResult(TimelineProcessResult.Status.SUCCEEDED, 0,
            output.getBytes(StandardCharsets.UTF_8), 0, Duration.ZERO);
    }

    private static TimelineProcessResult outcome(TimelineProcessResult.Status status) {
        return new TimelineProcessResult(status, -1, new byte[0], 0, Duration.ZERO);
    }

    private static Path javaExecutable() {
        String executable = System.getProperty("os.name", "").toLowerCase().contains("win") ? "java.exe" : "java";
        return Path.of(System.getProperty("java.home"), "bin", executable).toAbsolutePath().normalize();
    }

    private static Path devicePath() {
        return System.getProperty("os.name", "").toLowerCase().contains("win")
            ? Path.of("\\\\.\\PhysicalDrive0") : Path.of("/dev/null");
    }

    private static Path unsafeProtocolPath(String value) {
        try {
            return Path.of(value);
        } catch (java.nio.file.InvalidPathException exception) {
            // Windows rejects URI syntax before a Path can exist; the Path-only API has already excluded it.
            return Path.of(value.substring(0, value.indexOf(':')));
        }
    }

    private static String shellLikeFileName() {
        return System.getProperty("os.name", "").toLowerCase().contains("win")
            ? "video;-$().media" : "video;|&$().media";
    }

    @FunctionalInterface
    private interface ThrowingOperation {
        void run() throws Exception;
    }

    private static final class RecordingProcessExecutor implements TimelineProcessExecutor {
        private final Deque<TimelineProcessResult> outcomes = new ArrayDeque<>();
        private final List<TimelineProcessRequest> requests = new ArrayList<>();

        void enqueue(TimelineProcessResult outcome) {
            outcomes.addLast(Objects.requireNonNull(outcome));
        }

        @Override
        public TimelineProcessResult execute(TimelineProcessRequest request,
                                             BooleanSupplier cancellationRequested) {
            requests.add(request);
            if (outcomes.isEmpty()) {
                throw new AssertionError("ffprobe must not start for this input");
            }
            return outcomes.removeFirst();
        }

        @Override
        public void cancel(String executionId, String attemptId) {
            // The client delegates cancellation through the common process executor.
        }
    }
}
