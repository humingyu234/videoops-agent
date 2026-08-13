package org.dromara.aivideo.infra.timeline.probe;

import org.dromara.aivideo.creation.dto.CreationAssetResolveDTO;
import org.dromara.aivideo.creation.enums.CreationAssetType;
import org.dromara.aivideo.infra.timeline.path.TimelinePathGuard;
import org.dromara.aivideo.infra.timeline.process.TimelineProcessExecutor;
import org.dromara.aivideo.infra.timeline.process.TimelineProcessRequest;
import org.dromara.aivideo.infra.timeline.process.TimelineProcessResult;
import org.dromara.aivideo.timeline.constant.TimelineContractLimits;
import org.dromara.aivideo.timeline.enums.TimelineExecutionFailureCode;
import org.dromara.aivideo.timeline.exception.TimelineExecutionException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * Executes only the deployment-approved ffprobe binary against a guarded local input file.
 */
public final class FfprobeClient {

    private static final String SHOW_ENTRIES = "format=format_name,duration:"
        + "stream=codec_type,codec_name,width,height,r_frame_rate,sample_rate,channels";
    private static final long MAX_STDOUT_BYTES = 256L * 1024L;
    private static final long MAX_STDERR_BYTES = 32L * 1024L;
    private static final Map<String, String> PROCESS_ENVIRONMENT = Map.of("LANG", "C", "TZ", "UTC");

    private final TimelinePathGuard pathGuard;
    private final TimelineProcessExecutor processExecutor;
    private final Path ffprobeBinary;
    private final Duration processTimeout;
    private final JsonMapper jsonMapper;

    public FfprobeClient(TimelinePathGuard pathGuard,
                         TimelineProcessExecutor processExecutor,
                         Path ffprobeBinary,
                         Duration processTimeout) {
        this(pathGuard, processExecutor, ffprobeBinary, processTimeout, JsonMapper.builder().build());
    }

    FfprobeClient(TimelinePathGuard pathGuard,
                  TimelineProcessExecutor processExecutor,
                  Path ffprobeBinary,
                  Duration processTimeout,
                  JsonMapper jsonMapper) {
        this.pathGuard = Objects.requireNonNull(pathGuard, "pathGuard");
        this.processExecutor = Objects.requireNonNull(processExecutor, "processExecutor");
        this.ffprobeBinary = requireAbsolutePath(ffprobeBinary);
        if (processTimeout == null || processTimeout.isZero() || processTimeout.isNegative()) {
            throw new IllegalArgumentException("invalid ffprobe timeout");
        }
        this.processTimeout = processTimeout;
        this.jsonMapper = Objects.requireNonNull(jsonMapper, "jsonMapper");
    }

    /**
     * Probes one server-materialized asset. The path must already be beneath the guarded work root.
     */
    public MediaProbe probe(String executionId,
                            String attemptId,
                            CreationAssetResolveDTO expectedMedia,
                            Path input,
                            BooleanSupplier cancellationRequested) {
        Objects.requireNonNull(cancellationRequested, "cancellationRequested");
        CreationAssetType assetType = requireAssetType(expectedMedia);
        Path localInput = requireLocalInput(input);
        long fileSize = requireExpectedFileSize(expectedMedia, localInput, assetType);
        TimelineProcessResult result = execute(executionId, attemptId, localInput, cancellationRequested);
        return parseResult(result, assetType, fileSize);
    }

    private TimelineProcessResult execute(String executionId,
                                          String attemptId,
                                          Path localInput,
                                          BooleanSupplier cancellationRequested) {
        try {
            TimelineProcessRequest request = new TimelineProcessRequest(executionId, attemptId,
                command(localInput), pathGuard.approvedRoot(), PROCESS_ENVIRONMENT, processTimeout,
                MAX_STDOUT_BYTES, MAX_STDERR_BYTES);
            TimelineProcessResult result = processExecutor.execute(request, cancellationRequested);
            if (result == null) {
                throw processFailed();
            }
            return result;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw processFailed();
        } catch (TimelineExecutionException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw processFailed();
        }
    }

    private MediaProbe parseResult(TimelineProcessResult result, CreationAssetType assetType, long fileSize) {
        return switch (result.status()) {
            case SUCCEEDED -> parseOutput(result.stdout(), assetType, fileSize);
            case NON_ZERO_EXIT -> throw inputInvalid();
            case TIMED_OUT -> throw timeout();
            case OUTPUT_LIMIT_EXCEEDED -> throw responseTooLarge();
            case START_FAILED, PROCESS_FAILED, CANCELLED -> throw processFailed();
        };
    }

    private MediaProbe parseOutput(byte[] stdout, CreationAssetType assetType, long fileSize) {
        if (stdout.length == 0) {
            throw responseInvalid();
        }
        if (stdout.length > MAX_STDOUT_BYTES) {
            throw responseTooLarge();
        }
        try {
            JsonNode root = jsonMapper.readTree(stdout);
            if (root == null || !root.isObject()) {
                throw invalidProbeFacts();
            }
            JsonNode format = requireObject(root.get("format"));
            JsonNode streams = requireArray(root.get("streams"));
            String formatName = requiredFormat(format.get("format_name"));
            long durationMs = durationMs(format.get("duration"), assetType);
            StreamFacts streamFacts = parseStreams(streams, assetType);
            ensureExpectedStreams(assetType, streamFacts);
            return new MediaProbe(formatName, durationMs, fileSize, streamFacts.width(), streamFacts.height(),
                streamFacts.frameRate(), streamFacts.sampleRate(), streamFacts.channels(), streamFacts.videoStream(),
                streamFacts.audioStream(), streamFacts.videoCodec(), streamFacts.audioCodec(),
                securitySummary(formatName, streamFacts));
        } catch (ProbeFactsException exception) {
            throw inputInvalid();
        } catch (TimelineExecutionException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw responseInvalid();
        }
    }

    private StreamFacts parseStreams(JsonNode streams, CreationAssetType assetType) {
        StreamFacts result = StreamFacts.empty();
        for (JsonNode stream : streams) {
            JsonNode object = requireObject(stream);
            String codecType = stringScalar(object.get("codec_type"));
            if ("video".equals(codecType) && !result.videoStream()) {
                result = result.withVideo(requiredCodec(object.get("codec_name")),
                    boundedPositiveInteger(object.get("width"), maxWidth(assetType)),
                    boundedPositiveInteger(object.get("height"), maxHeight(assetType)),
                    frameRate(object.get("r_frame_rate")));
            } else if ("audio".equals(codecType) && !result.audioStream()) {
                result = result.withAudio(requiredCodec(object.get("codec_name")),
                    boundedPositiveInteger(object.get("sample_rate"), maxAudioSampleRate()),
                    boundedPositiveInteger(object.get("channels"), maxAudioChannels()));
            }
        }
        return result;
    }

    private static void ensureExpectedStreams(CreationAssetType assetType, StreamFacts facts) {
        switch (assetType) {
            case VIDEO, IMAGE -> {
                if (!facts.videoStream()) {
                    throw invalidProbeFacts();
                }
            }
            case AUDIO -> {
                if (!facts.audioStream()) {
                    throw invalidProbeFacts();
                }
            }
        }
    }

    private static long durationMs(JsonNode duration, CreationAssetType assetType) {
        if (assetType == CreationAssetType.IMAGE && optionalImageDuration(duration)) {
            return 0;
        }
        String value = stringScalar(duration);
        if (value == null || !value.matches("[0-9]+(?:\\.[0-9]+)?")) {
            throw invalidProbeFacts();
        }
        try {
            BigDecimal milliseconds = new BigDecimal(value).movePointRight(3).setScale(0, RoundingMode.HALF_UP);
            long result = milliseconds.longValueExact();
            if (milliseconds.compareTo(BigDecimal.ONE) < 0
                || milliseconds.compareTo(BigDecimal.valueOf(maxDurationMs(assetType))) > 0) {
                throw invalidProbeFacts();
            }
            return result;
        } catch (ArithmeticException exception) {
            throw invalidProbeFacts();
        }
    }

    private static boolean optionalImageDuration(JsonNode duration) {
        if (duration == null || duration.isNull()) {
            return true;
        }
        String value = stringScalar(duration);
        return value != null && "N/A".equals(value);
    }

    private static int frameRate(JsonNode value) {
        String ratio = stringScalar(value);
        if (ratio == null || !ratio.matches("[0-9]+/[0-9]+")) {
            throw invalidProbeFacts();
        }
        String[] parts = ratio.split("/", -1);
        try {
            BigDecimal numerator = new BigDecimal(parts[0]);
            BigDecimal denominator = new BigDecimal(parts[1]);
            if (numerator.signum() <= 0 || denominator.signum() <= 0) {
                throw invalidProbeFacts();
            }
            BigDecimal framesPerSecond = numerator.divide(denominator, 12, RoundingMode.HALF_UP);
            if (framesPerSecond.compareTo(BigDecimal.ONE) < 0
                || framesPerSecond.compareTo(BigDecimal.valueOf(maxVideoFrameRate())) > 0) {
                throw invalidProbeFacts();
            }
            return framesPerSecond.setScale(0, RoundingMode.HALF_UP).intValueExact();
        } catch (ArithmeticException exception) {
            throw invalidProbeFacts();
        }
    }

    private static int boundedPositiveInteger(JsonNode value, int maximum) {
        String text = integerScalar(value);
        if (text == null || !text.matches("[0-9]+")) {
            throw invalidProbeFacts();
        }
        try {
            int parsed = Integer.parseInt(text);
            if (parsed < 1 || parsed > maximum) {
                throw invalidProbeFacts();
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw invalidProbeFacts();
        }
    }

    private static JsonNode requireObject(JsonNode value) {
        if (value == null || !value.isObject()) {
            throw invalidProbeFacts();
        }
        return value;
    }

    private static JsonNode requireArray(JsonNode value) {
        if (value == null || !value.isArray()) {
            throw invalidProbeFacts();
        }
        return value;
    }

    private static String requiredFormat(JsonNode value) {
        String result = stringScalar(value);
        if (!safeToken(result, 128, true)) {
            throw invalidProbeFacts();
        }
        return result;
    }

    private static String requiredCodec(JsonNode value) {
        String result = stringScalar(value);
        if (!safeToken(result, 64, false)) {
            throw invalidProbeFacts();
        }
        return result;
    }

    private static String stringScalar(JsonNode value) {
        if (value == null || value.isNull() || !value.isString()) {
            return null;
        }
        return value.asString();
    }

    private static String integerScalar(JsonNode value) {
        if (value == null || value.isNull() || (!value.isString() && !value.isNumber())) {
            return null;
        }
        return value.asString();
    }

    private static boolean safeToken(String value, int maximumLength, boolean allowComma) {
        if (value == null || value.isBlank() || value.length() > maximumLength) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (!(character >= 'a' && character <= 'z')
                && !(character >= 'A' && character <= 'Z')
                && !(character >= '0' && character <= '9')
                && "._+-".indexOf(character) < 0
                && !(allowComma && character == ',')) {
                return false;
            }
        }
        return true;
    }

    private Path requireLocalInput(Path input) {
        try {
            return pathGuard.requireExistingFile(input);
        } catch (IllegalArgumentException exception) {
            throw inputInvalid();
        }
    }

    private static long requireExpectedFileSize(CreationAssetResolveDTO expectedMedia,
                                                Path localInput,
                                                CreationAssetType assetType) {
        if (expectedMedia.sizeBytes() <= 0) {
            throw inputInvalid();
        }
        try {
            long actualSize = Files.size(localInput);
            if (actualSize <= 0 || actualSize != expectedMedia.sizeBytes() || actualSize > maxBytes(assetType)) {
                throw inputInvalid();
            }
            return actualSize;
        } catch (IOException exception) {
            throw inputUnavailable();
        }
    }

    private static CreationAssetType requireAssetType(CreationAssetResolveDTO expectedMedia) {
        if (expectedMedia == null || expectedMedia.assetType() == null) {
            throw inputInvalid();
        }
        return expectedMedia.assetType();
    }

    private static Path requireAbsolutePath(Path value) {
        if (value == null || !value.isAbsolute()) {
            throw new IllegalArgumentException("invalid ffprobe binary");
        }
        return value.toAbsolutePath().normalize();
    }

    private List<String> command(Path localInput) {
        return List.of(ffprobeBinary.toString(), "-v", "error", "-hide_banner", "-show_entries",
            SHOW_ENTRIES, "-of", "json", "-protocol_whitelist", "file,pipe", "-i", localInput.toString());
    }

    private static String securitySummary(String formatName, StreamFacts facts) {
        return "format=" + formatName + ";video=" + (facts.videoStream() ? facts.videoCodec() : "none")
            + ";audio=" + (facts.audioStream() ? facts.audioCodec() : "none");
    }

    private static long maxBytes(CreationAssetType assetType) {
        return switch (assetType) {
            case VIDEO -> limit("maxVideoBytes");
            case IMAGE -> limit("maxImageBytes");
            case AUDIO -> limit("maxAudioBytes");
        };
    }

    private static long maxDurationMs(CreationAssetType assetType) {
        return switch (assetType) {
            case VIDEO -> limit("maxVideoDurationMs");
            case IMAGE -> 0;
            case AUDIO -> limit("maxAudioDurationMs");
        };
    }

    private static int maxWidth(CreationAssetType assetType) {
        return switch (assetType) {
            case VIDEO -> Math.toIntExact(limit("maxVideoWidth"));
            case IMAGE -> Math.toIntExact(limit("maxImageWidth"));
            case AUDIO -> throw invalidProbeFacts();
        };
    }

    private static int maxHeight(CreationAssetType assetType) {
        return switch (assetType) {
            case VIDEO -> Math.toIntExact(limit("maxVideoHeight"));
            case IMAGE -> Math.toIntExact(limit("maxImageHeight"));
            case AUDIO -> throw invalidProbeFacts();
        };
    }

    private static int maxVideoFrameRate() {
        return Math.toIntExact(limit("maxVideoFrameRate"));
    }

    private static int maxAudioSampleRate() {
        return Math.toIntExact(limit("maxAudioSampleRateHz"));
    }

    private static int maxAudioChannels() {
        return Math.toIntExact(limit("maxAudioChannels"));
    }

    private static long limit(String name) {
        return TimelineContractLimits.NUMERIC_LIMITS.get(name).longValueExact();
    }

    private static TimelineExecutionException inputInvalid() {
        return failure("媒体探测结果不符合素材约束", TimelineExecutionFailureCode.INPUT_INVALID, false);
    }

    private static TimelineExecutionException inputUnavailable() {
        return failure("媒体输入当前不可用", TimelineExecutionFailureCode.INPUT_UNAVAILABLE, true);
    }

    private static TimelineExecutionException timeout() {
        return failure("媒体探测超时", TimelineExecutionFailureCode.TIMEOUT, true);
    }

    private static TimelineExecutionException processFailed() {
        return failure("媒体探测进程执行失败", TimelineExecutionFailureCode.PROCESS_FAILED, true);
    }

    private static TimelineExecutionException responseTooLarge() {
        return failure("媒体探测响应超过安全上限", TimelineExecutionFailureCode.RESPONSE_TOO_LARGE, false);
    }

    private static TimelineExecutionException responseInvalid() {
        return failure("媒体探测响应格式无效", TimelineExecutionFailureCode.RESPONSE_INVALID, false);
    }

    private static TimelineExecutionException failure(String message,
                                                      TimelineExecutionFailureCode code,
                                                      boolean retryable) {
        return new TimelineExecutionException(message, code, retryable, null);
    }

    private static ProbeFactsException invalidProbeFacts() {
        return new ProbeFactsException();
    }

    private record StreamFacts(boolean videoStream,
                               boolean audioStream,
                               Integer width,
                               Integer height,
                               Integer frameRate,
                               Integer sampleRate,
                               Integer channels,
                               String videoCodec,
                               String audioCodec) {

        private static StreamFacts empty() {
            return new StreamFacts(false, false, null, null, null, null, null, null, null);
        }

        private StreamFacts withVideo(String codec, int width, int height, int frameRate) {
            return new StreamFacts(true, audioStream, width, height, frameRate, sampleRate, channels, codec,
                audioCodec);
        }

        private StreamFacts withAudio(String codec, int sampleRate, int channels) {
            return new StreamFacts(videoStream, true, width, height, frameRate, sampleRate, channels, videoCodec,
                codec);
        }
    }

    private static final class ProbeFactsException extends RuntimeException {
    }
}
