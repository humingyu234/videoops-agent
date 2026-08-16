package org.dromara.aivideo.infra.timeline.render;

import org.dromara.aivideo.creation.dto.CreationAssetResolveDTO;
import org.dromara.aivideo.creation.enums.CreationAssetType;
import org.dromara.aivideo.creation.enums.CreationAssetUsageOrigin;
import org.dromara.aivideo.creation.service.CreationMediaHandle;
import org.dromara.aivideo.infra.timeline.TimelineInfrastructureProperties;
import org.dromara.aivideo.infra.timeline.ass.TimelineFontMeasurer;
import org.dromara.aivideo.infra.timeline.path.TimelinePathGuard;
import org.dromara.aivideo.infra.timeline.probe.FfprobeClient;
import org.dromara.aivideo.infra.timeline.probe.MediaProbe;
import org.dromara.aivideo.infra.timeline.process.JdkTimelineProcessExecutor;
import org.dromara.aivideo.infra.timeline.process.TimelineProcessExecutor;
import org.dromara.aivideo.infra.timeline.process.TimelineProcessRequest;
import org.dromara.aivideo.infra.timeline.process.TimelineProcessResult;
import org.dromara.aivideo.task.enums.AiTaskStage;
import org.dromara.aivideo.timeline.constant.TimelineContractLimits;
import org.dromara.aivideo.timeline.dto.TimelineMediaProbeDTO;
import org.dromara.aivideo.timeline.dto.TimelineMediaQualityInspectionDTO;
import org.dromara.aivideo.timeline.dto.TimelineElementDTO;
import org.dromara.aivideo.timeline.dto.TimelineFancyTextElementDTO;
import org.dromara.aivideo.timeline.dto.TimelineProgressDTO;
import org.dromara.aivideo.timeline.dto.TimelineRenderCommandDTO;
import org.dromara.aivideo.timeline.dto.TimelineRenderResultDTO;
import org.dromara.aivideo.timeline.dto.TimelineSubtitleElementDTO;
import org.dromara.aivideo.timeline.dto.TimelineTextMeasureCommandDTO;
import org.dromara.aivideo.timeline.dto.TimelineTextMeasureResultDTO;
import org.dromara.aivideo.timeline.dto.TimelineTrackDTO;
import org.dromara.aivideo.timeline.enums.TimelineAssetUsageType;
import org.dromara.aivideo.timeline.enums.TimelineExecutionFailureCode;
import org.dromara.aivideo.timeline.exception.TimelineExecutionException;
import org.dromara.aivideo.timeline.service.ITimelineMediaRenderService;
import org.dromara.aivideo.timeline.service.TimelineRenderOutputHandle;
import org.dromara.aivideo.timeline.service.TimelineTaskProgressListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

/**
 * Shell-free implementation that consumes only caller-owned, controlled media handles and renders
 * their materialized aliases inside one guarded work directory.
 */
@Component
@ConditionalOnProperty(prefix = "aivideo.timeline", name = "enabled", havingValue = "true")
public final class FfmpegTimelineMediaRenderService implements ITimelineMediaRenderService {

    private static final int CANVAS_WIDTH = limit("canvasWidth");
    private static final int CANVAS_HEIGHT = limit("canvasHeight");
    private static final int OUTPUT_FRAME_RATE = limit("outputFrameRate");
    private static final long OUTPUT_DURATION_TOLERANCE_MS = 100L;
    private static final int MAX_GENERATED_SCRIPT_BYTES = 256 * 1024;
    private static final long PROCESS_OUTPUT_LIMIT_BYTES = 1024L * 1024L;
    private static final Map<String, String> PROCESS_ENVIRONMENT = Map.of(
        "LANG", "C",
        "LC_ALL", "C",
        "TZ", "UTC"
    );
    private static final String FONT_REGISTRY_FILE = "font-registry.json";
    private static final String FONT_REGISTRY_SHA256 = "2e0198557dc5a00c4cdde6eb970a3c2282c298f169c3f6bd7349c275156a9e33";
    private static final Map<String, String> REQUIRED_FONT_HASHES = Map.of(
        "NotoSansCJKsc-Regular.otf", "2c76254f6fc379fddfce0a7e84fb5385bb135d3e399294f6eeb6680d0365b74b",
        "NotoSerifCJKsc-Regular.otf", "2a2eae2628df83556c54018c41e20fa532c1b862c5256ae8b3f23feb918d12ca"
    );

    private final TimelinePathGuard pathGuard;
    private final TimelineProcessExecutor processExecutor;
    private final Path ffmpegBinary;
    private final Duration processTimeout;
    private final long maxOutputBytes;
    private final RenderPlanFactory renderPlanFactory;
    private final MediaProber mediaProber;
    private final FontStager fontStager;
    private final TextMeasurer textMeasurer;
    private final FfmpegCommandBuilder commandBuilder;

    /**
     * Production constructor. All paths are validated by properties before a real renderer exists.
     */
    @Autowired
    public FfmpegTimelineMediaRenderService(TimelineInfrastructureProperties properties) {
        this(dependencies(properties));
    }

    private FfmpegTimelineMediaRenderService(Dependencies dependencies) {
        this(dependencies.pathGuard(), dependencies.processExecutor(), dependencies.ffmpegBinary(),
            dependencies.processTimeout(), dependencies.maxOutputBytes(), dependencies.renderPlanFactory(),
            dependencies.mediaProber(), dependencies.fontStager(), dependencies.textMeasurer());
    }

    /** Package-private deterministic seam for media safety tests. */
    FfmpegTimelineMediaRenderService(TimelinePathGuard pathGuard,
                                     TimelineProcessExecutor processExecutor,
                                     Path ffmpegBinary,
                                     Duration processTimeout,
                                     long maxOutputBytes,
                                     RenderPlanFactory renderPlanFactory,
                                     MediaProber mediaProber,
                                     FontStager fontStager,
                                     TextMeasurer textMeasurer) {
        this.pathGuard = Objects.requireNonNull(pathGuard, "pathGuard");
        this.processExecutor = Objects.requireNonNull(processExecutor, "processExecutor");
        if (ffmpegBinary == null || !ffmpegBinary.isAbsolute()) {
            throw new IllegalArgumentException("timeline FFmpeg binary is invalid");
        }
        this.ffmpegBinary = ffmpegBinary.toAbsolutePath().normalize();
        if (processTimeout == null || processTimeout.isZero() || processTimeout.isNegative()) {
            throw new IllegalArgumentException("timeline process timeout is invalid");
        }
        this.processTimeout = processTimeout;
        if (maxOutputBytes <= 0) {
            throw new IllegalArgumentException("timeline output limit is invalid");
        }
        this.maxOutputBytes = maxOutputBytes;
        this.renderPlanFactory = Objects.requireNonNull(renderPlanFactory, "renderPlanFactory");
        this.mediaProber = Objects.requireNonNull(mediaProber, "mediaProber");
        this.fontStager = Objects.requireNonNull(fontStager, "fontStager");
        this.textMeasurer = Objects.requireNonNull(textMeasurer, "textMeasurer");
        this.commandBuilder = new FfmpegCommandBuilder();
    }

    @Override
    public TimelineMediaProbeDTO probe(CreationMediaHandle input) {
        return inspect(input, false).probe();
    }

    @Override
    public TimelineMediaQualityInspectionDTO inspectQuality(CreationMediaHandle input) {
        return inspect(input, true);
    }

    private TimelineMediaQualityInspectionDTO inspect(CreationMediaHandle input, boolean decodeFully) {
        CreationAssetResolveDTO metadata = decodeFully ? requireQualityMetadata(input) : requireMetadata(input);
        Path workDirectory = null;
        try {
            workDirectory = createWorkDirectory();
            String alias = "input-0001." + extension(metadata.assetType());
            Path localInput = materialize(input, metadata, alias, workDirectory, () -> false);
            String probeId = "timeline-probe-" + UUID.randomUUID();
            MediaProbe probe = mediaProber.probe(probeId, probeId, metadata, localInput, () -> false);
            if (decodeFully) {
                validateQualityProbe(metadata, probe);
            } else {
                validateStandaloneProbe(metadata, probe);
            }
            TimelineMediaProbeDTO facts = new TimelineMediaProbeDTO(metadata.assetId(), metadata.assetType().value(),
                probe.formatName(),
                probe.durationMs(), probe.fileSize(), probe.width(), probe.height(), probe.frameRate(),
                probe.sampleRate(), probe.channels(), probe.videoStream(), probe.audioStream(), probe.videoCodec(),
                probe.audioCodec());
            if (decodeFully) {
                String decodeId = "timeline-quality-" + UUID.randomUUID();
                executeQualityDecode(fullDecodeRequest(decodeId, localInput, workDirectory));
            }
            return new TimelineMediaQualityInspectionDTO(facts, decodeFully);
        } catch (TimelineExecutionException exception) {
            throw exception;
        } catch (CancellationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw inputInvalid();
        } finally {
            cleanupQuietly(workDirectory);
        }
    }

    private TimelineProcessRequest fullDecodeRequest(String decodeId, Path input, Path workDirectory) {
        List<String> command = List.of(
            ffmpegBinary.toString(), "-nostdin", "-hide_banner", "-loglevel", "error", "-xerror",
            "-i", input.toString(), "-map", "0:v?", "-map", "0:a?", "-f", "null", "-"
        );
        return new TimelineProcessRequest(decodeId, decodeId, command, workDirectory, PROCESS_ENVIRONMENT,
            processTimeout, PROCESS_OUTPUT_LIMIT_BYTES, PROCESS_OUTPUT_LIMIT_BYTES);
    }

    @Override
    public TimelineTextMeasureResultDTO measureText(TimelineTextMeasureCommandDTO command) {
        return textMeasurer.measure(command);
    }

    @Override
    public TimelineRenderOutputHandle render(TimelineRenderCommandDTO command,
                                             List<CreationMediaHandle> inputs,
                                             TimelineTaskProgressListener progress,
                                             BooleanSupplier cancellationRequested) {
        if (progress == null || cancellationRequested == null) {
            throw inputInvalid();
        }
        Path workDirectory = null;
        boolean outputTransferred = false;
        TimelineRenderPlan plan = null;
        try {
            List<CreationAssetResolveDTO> inputFacts = inputFacts(inputs);
            plan = buildPlan(command, inputFacts);
            verifyGlyphCoverage(command);
            checkCancelled(cancellationRequested);
            report(progress, plan, AiTaskStage.PREPARING_ASSETS, 5, "Preparing controlled media");

            workDirectory = createWorkDirectory();
            Map<InputKey, HandleInput> handles = bindHandles(inputs);
            report(progress, plan, AiTaskStage.READING_ASSETS, 15, "Reading controlled media");
            for (TimelineRenderPlan.Input source : plan.inputs()) {
                HandleInput handle = handles.remove(new InputKey(source.assetId(), source.usageType()));
                if (handle == null) {
                    throw inputInvalid();
                }
                validatePlanBinding(source, handle.metadata());
                Path localInput = materialize(handle.handle(), handle.metadata(), source.alias(), workDirectory,
                    cancellationRequested);
                MediaProbe probe = probeInput(plan, handle.metadata(), localInput, cancellationRequested);
                validateInputProbe(handle.metadata(), probe);
                checkCancelled(cancellationRequested);
            }
            if (!handles.isEmpty()) {
                throw inputInvalid();
            }

            report(progress, plan, AiTaskStage.BUILDING_ASS, 45, "Preparing subtitle resources");
            writeGeneratedFile(workDirectory, TimelineRenderPlan.ASS_FILE_NAME, plan.assScript());
            checkCancelled(cancellationRequested);
            stageFonts(workDirectory);
            checkCancelled(cancellationRequested);
            report(progress, plan, AiTaskStage.BUILDING_RENDER_PLAN, 50, "Preparing render plan");
            writeGeneratedFile(workDirectory, TimelineRenderPlan.FILTER_FILE_NAME, plan.filterScript());

            report(progress, plan, AiTaskStage.ENCODING, 55, "Encoding timeline media");
            for (TimelineRenderPlan.PipTail tail : plan.pipTails()) {
                checkCancelled(cancellationRequested);
                Path tailOutput = prepareFfmpegOutput(workDirectory, tail.renderAlias());
                executeFfmpeg(commandBuilder.buildPipTail(plan, tail, ffmpegBinary, workDirectory, processTimeout),
                    cancellationRequested);
                verifyGeneratedOutput(tailOutput);
            }
            Path outputPath = prepareFfmpegOutput(workDirectory, TimelineRenderPlan.OUTPUT_FILE_NAME);
            executeFfmpeg(commandBuilder.build(plan, ffmpegBinary, workDirectory, processTimeout), cancellationRequested);
            outputPath = verifyGeneratedOutput(outputPath);

            report(progress, plan, AiTaskStage.VERIFYING_OUTPUT, 90, "Verifying rendered media");
            MediaProbe outputProbe = probeOutput(plan, outputPath, cancellationRequested);
            validateOutput(plan, outputPath, outputProbe);
            checkCancelled(cancellationRequested);
            String sha256 = sha256(outputPath, outputInvalid());
            TimelineRenderResultDTO result = new TimelineRenderResultDTO(TimelineRenderPlan.OUTPUT_FILE_NAME,
                "video/mp4", sha256, outputProbe.fileSize(), outputProbe.durationMs(), outputProbe.width(),
                outputProbe.height(), outputProbe.frameRate());
            report(progress, plan, AiTaskStage.VERIFYING_OUTPUT, 100, "Rendered media verified");

            LocalOutputHandle output = new LocalOutputHandle(result, outputPath, workDirectory);
            outputTransferred = true;
            return output;
        } catch (CancellationException exception) {
            if (plan != null) {
                processExecutor.cancel(plan.executionId(), plan.attemptId());
            }
            throw exception;
        } catch (TimelineExecutionException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw processFailed();
        } finally {
            if (!outputTransferred) {
                cleanupQuietly(workDirectory);
            }
        }
    }

    @Override
    public void cancel(String executionId, String attemptId) {
        processExecutor.cancel(executionId, attemptId);
    }

    private TimelineRenderPlan buildPlan(TimelineRenderCommandDTO command,
                                         List<CreationAssetResolveDTO> inputFacts) {
        try {
            TimelineRenderPlan plan = renderPlanFactory.build(command, inputFacts);
            return Objects.requireNonNull(plan, "render plan");
        } catch (TimelineExecutionException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw inputInvalid();
        }
    }

    private List<CreationAssetResolveDTO> inputFacts(List<CreationMediaHandle> inputs) {
        if (inputs == null || inputs.isEmpty()) {
            throw inputInvalid();
        }
        List<CreationAssetResolveDTO> facts = new ArrayList<>(inputs.size());
        for (CreationMediaHandle input : inputs) {
            facts.add(requireMetadata(input));
        }
        return List.copyOf(facts);
    }

    private Map<InputKey, HandleInput> bindHandles(List<CreationMediaHandle> inputs) {
        Map<InputKey, HandleInput> result = new HashMap<>();
        for (CreationMediaHandle input : inputs) {
            CreationAssetResolveDTO metadata = requireMetadata(input);
            InputKey key = new InputKey(metadata.assetId(), metadata.usageType());
            if (result.putIfAbsent(key, new HandleInput(input, metadata)) != null) {
                throw inputInvalid();
            }
        }
        return result;
    }

    private static CreationAssetResolveDTO requireMetadata(CreationMediaHandle input) {
        CreationAssetResolveDTO metadata = requireCommonMetadata(input);
        if (metadata.usageType() == null) {
            throw inputInvalid();
        }
        return metadata;
    }

    private static CreationAssetResolveDTO requireQualityMetadata(CreationMediaHandle input) {
        CreationAssetResolveDTO metadata = requireCommonMetadata(input);
        if (metadata.assetType() != CreationAssetType.VIDEO
            || metadata.usageOrigin() != CreationAssetUsageOrigin.TIMELINE_RENDER_OUTPUT) {
            throw inputInvalid();
        }
        return metadata;
    }

    private static CreationAssetResolveDTO requireCommonMetadata(CreationMediaHandle input) {
        if (input == null) {
            throw inputInvalid();
        }
        try {
            CreationAssetResolveDTO metadata = input.metadata();
            if (metadata == null || metadata.assetId() == null || metadata.assetId().isBlank()
                || metadata.sha256() == null || !metadata.sha256().matches("[0-9a-f]{64}")
                || metadata.assetType() == null || metadata.sizeBytes() <= 0) {
                throw inputInvalid();
            }
            return metadata;
        } catch (TimelineExecutionException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw inputInvalid();
        }
    }

    private static void validatePlanBinding(TimelineRenderPlan.Input source,
                                            CreationAssetResolveDTO metadata) {
        if (!source.assetId().equals(metadata.assetId()) || !source.sha256().equals(metadata.sha256())
            || source.sizeBytes() != metadata.sizeBytes() || source.assetType() != metadata.assetType()
            || source.usageType() != metadata.usageType()) {
            throw inputInvalid();
        }
    }

    private Path materialize(CreationMediaHandle input,
                             CreationAssetResolveDTO metadata,
                             String alias,
                             Path workDirectory,
                             BooleanSupplier cancellationRequested) {
        validateHandleRange(input, metadata);
        Path output = prepareGeneratedOutput(workDirectory, alias, inputInvalid());
        InputStream source;
        try {
            source = input.stream();
        } catch (RuntimeException exception) {
            throw inputUnavailable();
        }
        if (source == null) {
            throw inputUnavailable();
        }
        MessageDigest digest = sha256Digest(processFailed());
        long copied = 0;
        try (OutputStream target = newOutputStream(output)) {
            byte[] buffer = new byte[32 * 1024];
            while (true) {
                checkCancelled(cancellationRequested);
                int count;
                try {
                    count = source.read(buffer);
                } catch (IOException exception) {
                    throw inputUnavailable();
                }
                if (count < 0) {
                    break;
                }
                if (count == 0) {
                    continue;
                }
                if (count > metadata.sizeBytes() - copied) {
                    throw inputInvalid();
                }
                try {
                    target.write(buffer, 0, count);
                } catch (IOException exception) {
                    throw processFailed();
                }
                digest.update(buffer, 0, count);
                copied += count;
            }
        } catch (TimelineExecutionException | CancellationException exception) {
            throw exception;
        } catch (IOException exception) {
            throw processFailed();
        }
        if (copied != metadata.sizeBytes() || !metadata.sha256().equals(HexFormat.of().formatHex(digest.digest()))) {
            throw inputInvalid();
        }
        try {
            return pathGuard.verifyCreatedOutputFile(output);
        } catch (IllegalArgumentException exception) {
            throw processFailed();
        }
    }

    private static void validateHandleRange(CreationMediaHandle input, CreationAssetResolveDTO metadata) {
        try {
            if (input.offset() != 0 || input.length() != metadata.sizeBytes()
                || input.totalSize() != metadata.sizeBytes()) {
                throw inputInvalid();
            }
        } catch (TimelineExecutionException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw inputInvalid();
        }
    }

    private void validateInputProbe(CreationAssetResolveDTO expected, MediaProbe actual) {
        if (actual == null || actual.fileSize() != expected.sizeBytes()) {
            throw inputInvalid();
        }
        if (expected.durationMs() != null && actual.durationMs() != expected.durationMs()) {
            throw inputInvalid();
        }
        if (expected.width() != null && !expected.width().equals(actual.width())) {
            throw inputInvalid();
        }
        if (expected.height() != null && !expected.height().equals(actual.height())) {
            throw inputInvalid();
        }
        if (expected.hasVideoStream() != actual.videoStream() || expected.hasAudioStream() != actual.audioStream()) {
            throw inputInvalid();
        }
        boolean expectedPrimaryStream = switch (expected.assetType()) {
            case VIDEO, IMAGE -> actual.videoStream();
            case AUDIO -> actual.audioStream();
        };
        if (!expectedPrimaryStream) {
            throw inputInvalid();
        }
    }

    private void validateStandaloneProbe(CreationAssetResolveDTO expected, MediaProbe actual) {
        if (actual == null || actual.fileSize() != expected.sizeBytes()) {
            throw inputInvalid();
        }
        if (expected.durationMs() != null && actual.durationMs() != expected.durationMs()) {
            throw inputInvalid();
        }
        if (expected.width() != null && !expected.width().equals(actual.width())) {
            throw inputInvalid();
        }
        if (expected.height() != null && !expected.height().equals(actual.height())) {
            throw inputInvalid();
        }
        boolean requiredStreamPresent = switch (expected.assetType()) {
            case VIDEO, IMAGE -> actual.videoStream();
            case AUDIO -> actual.audioStream();
        };
        if (!requiredStreamPresent) {
            throw inputInvalid();
        }
    }

    private void validateQualityProbe(CreationAssetResolveDTO expected, MediaProbe actual) {
        if (actual == null || actual.fileSize() != expected.sizeBytes()) {
            throw inputInvalid();
        }
    }

    /**
     * Verifies every text that reaches the generated ASS document against its registered font.
     * The plan builder has already validated the document shape; a null document is only accepted
     * by the package-private test plan seam and cannot occur in the production factory.
     */
    private void verifyGlyphCoverage(TimelineRenderCommandDTO command) {
        if (command == null || command.timeline() == null || command.timeline().tracks() == null) {
            return;
        }
        int requestIndex = 0;
        for (TimelineTrackDTO track : command.timeline().tracks()) {
            if (track == null || track.elements() == null) {
                throw fontUnavailable();
            }
            for (TimelineElementDTO element : track.elements()) {
                if (element instanceof TimelineSubtitleElementDTO subtitle && subtitle.enabled()) {
                    verifyGlyphs("glyph-" + requestIndex++, subtitle.fontCode(), subtitle.displayText(),
                        subtitle.fontSizePx(), subtitle.outlineEnabled() ? subtitle.outlineWidthPx() : 0,
                        command.fontRegistrySha256());
                } else if (element instanceof TimelineFancyTextElementDTO fancyText && fancyText.enabled()) {
                    // Glyph support is independent of style size; 12px/0px is the smallest C0-valid probe.
                    verifyGlyphs("glyph-" + requestIndex++, fancyText.fontCode(), fancyText.text(), 12, 0,
                        command.fontRegistrySha256());
                }
            }
        }
    }

    private void verifyGlyphs(String requestId,
                              String fontCode,
                              String text,
                              int fontSizePx,
                              int outlineWidthPx,
                              String registrySha256) {
        try {
            TimelineTextMeasureResultDTO result = textMeasurer.measure(new TimelineTextMeasureCommandDTO(requestId,
                fontCode, text, fontSizePx, CANVAS_WIDTH, outlineWidthPx,
                TimelineContractLimits.NUMERIC_LIMITS.get("safeMarginRatio")));
            if (result == null || !fontCode.equals(result.fontCode()) || !registrySha256.equals(result.fontRegistrySha256())
                || !result.allCodePointsSupported()) {
                throw fontUnavailable();
            }
        } catch (TimelineExecutionException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw fontUnavailable();
        }
    }

    private MediaProbe probeInput(TimelineRenderPlan plan,
                                  CreationAssetResolveDTO metadata,
                                  Path localInput,
                                  BooleanSupplier cancellationRequested) {
        try {
            MediaProbe probe = mediaProber.probe(plan.executionId(), plan.attemptId(), metadata, localInput,
                cancellationRequested);
            checkCancelled(cancellationRequested);
            return probe;
        } catch (TimelineExecutionException exception) {
            checkCancelled(cancellationRequested);
            throw exception;
        }
    }

    private void writeGeneratedFile(Path workDirectory, String name, String content) {
        byte[] bytes = content == null ? null : content.getBytes(StandardCharsets.UTF_8);
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_GENERATED_SCRIPT_BYTES) {
            throw processFailed();
        }
        Path output = prepareGeneratedOutput(workDirectory, name, processFailed());
        try (OutputStream stream = newOutputStream(output)) {
            stream.write(bytes);
        } catch (IOException exception) {
            throw processFailed();
        }
        try {
            pathGuard.verifyCreatedOutputFile(output);
        } catch (IllegalArgumentException exception) {
            throw processFailed();
        }
    }

    private void stageFonts(Path workDirectory) {
        Path fontsDirectory = workDirectory.resolve(TimelineRenderPlan.FONTS_DIRECTORY_NAME).normalize();
        try {
            if (!fontsDirectory.startsWith(workDirectory)) {
                throw fontUnavailable();
            }
            Files.createDirectory(fontsDirectory);
            Path verifiedDirectory = pathGuard.requireExistingDirectory(fontsDirectory);
            fontStager.stage(verifiedDirectory);
            pathGuard.requireExistingDirectory(verifiedDirectory);
        } catch (TimelineExecutionException exception) {
            throw exception;
        } catch (IOException | IllegalArgumentException | SecurityException exception) {
            throw fontUnavailable();
        }
    }

    private Path prepareFfmpegOutput(Path workDirectory, String fileName) {
        return prepareGeneratedOutput(workDirectory, fileName, outputInvalid());
    }

    private Path prepareGeneratedOutput(Path workDirectory,
                                        String fileName,
                                        TimelineExecutionException failure) {
        try {
            Path candidate = workDirectory.resolve(fileName).normalize();
            if (!candidate.startsWith(workDirectory)) {
                throw failure;
            }
            return pathGuard.prepareOutputFile(candidate);
        } catch (TimelineExecutionException exception) {
            throw exception;
        } catch (IllegalArgumentException exception) {
            throw failure;
        }
    }

    private void executeFfmpeg(TimelineProcessRequest request, BooleanSupplier cancellationRequested) {
        executeFfmpeg(request, cancellationRequested, processFailed());
    }

    private void executeQualityDecode(TimelineProcessRequest request) {
        executeFfmpeg(request, () -> false, inputInvalid());
    }

    private void executeFfmpeg(TimelineProcessRequest request,
                               BooleanSupplier cancellationRequested,
                               TimelineExecutionException nonZeroFailure) {
        checkCancelled(cancellationRequested);
        TimelineProcessResult result;
        try {
            result = processExecutor.execute(request, cancellationRequested);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw processFailed();
        } catch (RuntimeException exception) {
            checkCancelled(cancellationRequested);
            throw processFailed();
        }
        checkCancelled(cancellationRequested);
        if (result == null) {
            throw processFailed();
        }
        switch (result.status()) {
            case SUCCEEDED -> {
                return;
            }
            case CANCELLED -> throw cancelled();
            case TIMED_OUT -> throw timeout();
            case NON_ZERO_EXIT -> throw nonZeroFailure;
            case OUTPUT_LIMIT_EXCEEDED, START_FAILED, PROCESS_FAILED -> throw processFailed();
        }
    }

    private Path verifyGeneratedOutput(Path output) {
        try {
            Path verified = pathGuard.verifyCreatedOutputFile(output);
            long size = Files.size(verified);
            if (size <= 0 || size > maxOutputBytes) {
                throw outputInvalid();
            }
            return verified;
        } catch (TimelineExecutionException exception) {
            throw exception;
        } catch (IOException | IllegalArgumentException exception) {
            throw outputInvalid();
        }
    }

    private MediaProbe probeOutput(TimelineRenderPlan plan, Path output, BooleanSupplier cancellationRequested) {
        try {
            CreationAssetResolveDTO expected = new CreationAssetResolveDTO("1", "video/mp4", "0".repeat(64),
                CreationAssetType.VIDEO, TimelineAssetUsageType.BASE_VIDEO, Files.size(output), plan.durationMs(),
                CANVAS_WIDTH, CANVAS_HEIGHT, true, true);
            MediaProbe probe = mediaProber.probe(plan.executionId(), plan.attemptId(), expected, output,
                cancellationRequested);
            checkCancelled(cancellationRequested);
            return probe;
        } catch (CancellationException exception) {
            throw exception;
        } catch (TimelineExecutionException exception) {
            checkCancelled(cancellationRequested);
            throw outputInvalid();
        } catch (IOException | RuntimeException exception) {
            throw outputInvalid();
        }
    }

    private void validateOutput(TimelineRenderPlan plan, Path output, MediaProbe probe) {
        if (probe == null || probe.fileSize() <= 0 || probe.fileSize() > maxOutputBytes
            || probe.fileSize() != fileSize(output, outputInvalid()) || !mp4Format(probe.formatName())
            || !probe.videoStream() || !probe.audioStream() || !"h264".equals(probe.videoCodec())
            || !"aac".equals(probe.audioCodec()) || !Integer.valueOf(CANVAS_WIDTH).equals(probe.width())
            || !Integer.valueOf(CANVAS_HEIGHT).equals(probe.height())
            || !Integer.valueOf(OUTPUT_FRAME_RATE).equals(probe.frameRate())
            || Math.abs(probe.durationMs() - plan.durationMs()) > OUTPUT_DURATION_TOLERANCE_MS) {
            throw outputInvalid();
        }
    }

    private static boolean mp4Format(String formatName) {
        if (formatName == null) {
            return false;
        }
        for (String format : formatName.split(",", -1)) {
            if ("mp4".equals(format)) {
                return true;
            }
        }
        return false;
    }

    private Path createWorkDirectory() {
        try {
            Path directory = Files.createTempDirectory(pathGuard.approvedRoot(), "render-");
            Path verified = pathGuard.requireExistingDirectory(directory);
            if (!pathGuard.approvedRoot().equals(verified.getParent())) {
                throw processFailed();
            }
            return verified;
        } catch (TimelineExecutionException exception) {
            throw exception;
        } catch (IOException | IllegalArgumentException exception) {
            throw processFailed();
        }
    }

    private void report(TimelineTaskProgressListener listener,
                        TimelineRenderPlan plan,
                        AiTaskStage stage,
                        int percent,
                        String safeMessage) {
        try {
            listener.onProgress(new TimelineProgressDTO(stage, percent, safeMessage));
        } catch (RuntimeException exception) {
            processExecutor.cancel(plan.executionId(), plan.attemptId());
            throw callbackFailed();
        }
    }

    private static void checkCancelled(BooleanSupplier cancellationRequested) {
        try {
            if (cancellationRequested.getAsBoolean()) {
                throw cancelled();
            }
        } catch (CancellationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw cancelled();
        }
    }

    private void cleanupQuietly(Path workDirectory) {
        if (workDirectory == null) {
            return;
        }
        try {
            cleanupWorkDirectory(workDirectory);
        } catch (IOException ignored) {
            // Cleanup never broadens its target; the primary safe failure is preserved.
        }
    }

    private void cleanupWorkDirectory(Path workDirectory) throws IOException {
        final Path verified;
        try {
            verified = pathGuard.requireExistingDirectory(workDirectory);
        } catch (IllegalArgumentException exception) {
            throw cleanupFailure();
        }
        if (!pathGuard.approvedRoot().equals(verified.getParent())) {
            throw cleanupFailure();
        }
        try {
            Files.walkFileTree(verified, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes)
                    throws IOException {
                    if (attributes.isSymbolicLink() || attributes.isOther()) {
                        throw cleanupFailure();
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                    if (!attributes.isRegularFile() || attributes.isSymbolicLink() || attributes.isOther()) {
                        throw cleanupFailure();
                    }
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path directory, IOException failure) throws IOException {
                    if (failure != null) {
                        throw cleanupFailure();
                    }
                    Files.delete(directory);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException | SecurityException exception) {
            throw cleanupFailure();
        }
    }

    private static OutputStream newOutputStream(Path output) throws IOException {
        Set<OpenOption> options = Set.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE,
            LinkOption.NOFOLLOW_LINKS);
        SeekableByteChannel channel = Files.newByteChannel(output, options);
        return Channels.newOutputStream(channel);
    }

    private static String extension(CreationAssetType type) {
        if (type == null) {
            throw inputInvalid();
        }
        return TimelineRenderPlan.extension(type);
    }

    private static long fileSize(Path file, TimelineExecutionException failure) {
        try {
            return Files.size(file);
        } catch (IOException exception) {
            throw failure;
        }
    }

    private static String sha256(Path file, TimelineExecutionException failure) {
        MessageDigest digest = sha256Digest(failure);
        try (InputStream stream = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
            byte[] buffer = new byte[32 * 1024];
            for (int count; (count = stream.read(buffer)) >= 0; ) {
                if (count > 0) {
                    digest.update(buffer, 0, count);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException exception) {
            throw failure;
        }
    }

    private static MessageDigest sha256Digest(TimelineExecutionException failure) {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw failure;
        }
    }

    private static Dependencies dependencies(TimelineInfrastructureProperties properties) {
        Objects.requireNonNull(properties, "properties");
        properties.validate();
        TimelinePathGuard pathGuard = new TimelinePathGuard(properties.validatedWorkRoot());
        Path ffmpeg = properties.ffmpegBinary();
        Path ffprobe = properties.ffprobeBinary();
        TimelineProcessExecutor executor = new JdkTimelineProcessExecutor(pathGuard, List.of(ffmpeg, ffprobe));
        FfprobeClient probeClient = new FfprobeClient(pathGuard, executor, ffprobe, properties.getProcessTimeout());
        Path fontRoot = properties.validatedFontRoot();
        VerifiedFontStager fontStager = new VerifiedFontStager(fontRoot);
        fontStager.verifySource();
        return new Dependencies(pathGuard, executor, ffmpeg, properties.getProcessTimeout(), properties.getMaxOutputBytes(),
            new TimelineRenderPlanBuilder()::build, probeClient::probe, fontStager,
            new TimelineFontMeasurer(fontRoot)::measure);
    }

    private static int limit(String name) {
        return TimelineContractLimits.NUMERIC_LIMITS.get(name).intValueExact();
    }

    private static TimelineExecutionException inputInvalid() {
        return failure("timeline render input is invalid", TimelineExecutionFailureCode.INPUT_INVALID, false);
    }

    private static TimelineExecutionException inputUnavailable() {
        return failure("timeline render input is unavailable", TimelineExecutionFailureCode.INPUT_UNAVAILABLE, true);
    }

    private static TimelineExecutionException fontUnavailable() {
        return failure("timeline render font is unavailable", TimelineExecutionFailureCode.FONT_UNAVAILABLE, false);
    }

    private static TimelineExecutionException timeout() {
        return failure("timeline render timed out", TimelineExecutionFailureCode.TIMEOUT, true);
    }

    private static TimelineExecutionException processFailed() {
        return failure("timeline render process failed", TimelineExecutionFailureCode.PROCESS_FAILED, true);
    }

    private static TimelineExecutionException outputInvalid() {
        return failure("timeline render output is invalid", TimelineExecutionFailureCode.OUTPUT_INVALID, false);
    }

    private static TimelineExecutionException callbackFailed() {
        return failure("timeline render progress callback failed", TimelineExecutionFailureCode.CALLBACK_FAILED, true);
    }

    private static TimelineExecutionException failure(String safeMessage,
                                                      TimelineExecutionFailureCode code,
                                                      boolean retryable) {
        return new TimelineExecutionException(safeMessage, code, retryable, null);
    }

    private static CancellationException cancelled() {
        return new CancellationException("timeline render cancelled");
    }

    private static IOException cleanupFailure() {
        return new IOException("timeline render output cleanup failed");
    }

    @FunctionalInterface
    interface RenderPlanFactory {
        TimelineRenderPlan build(TimelineRenderCommandDTO command, List<CreationAssetResolveDTO> inputFacts);
    }

    @FunctionalInterface
    interface MediaProber {
        MediaProbe probe(String executionId,
                         String attemptId,
                         CreationAssetResolveDTO expected,
                         Path input,
                         BooleanSupplier cancellationRequested);
    }

    @FunctionalInterface
    interface FontStager {
        void stage(Path targetDirectory) throws IOException;
    }

    @FunctionalInterface
    interface TextMeasurer {
        TimelineTextMeasureResultDTO measure(TimelineTextMeasureCommandDTO command);
    }

    private record Dependencies(TimelinePathGuard pathGuard,
                                TimelineProcessExecutor processExecutor,
                                Path ffmpegBinary,
                                Duration processTimeout,
                                long maxOutputBytes,
                                RenderPlanFactory renderPlanFactory,
                                MediaProber mediaProber,
                                FontStager fontStager,
                                TextMeasurer textMeasurer) {
    }

    private record InputKey(String assetId, TimelineAssetUsageType usageType) {
    }

    private record HandleInput(CreationMediaHandle handle, CreationAssetResolveDTO metadata) {
    }

    private final class LocalOutputHandle implements TimelineRenderOutputHandle {
        private final TimelineRenderResultDTO metadata;
        private final Path output;
        private final Path workDirectory;
        private final AtomicBoolean closed = new AtomicBoolean();
        private InputStream stream;
        private boolean opened;

        private LocalOutputHandle(TimelineRenderResultDTO metadata, Path output, Path workDirectory) {
            this.metadata = Objects.requireNonNull(metadata, "metadata");
            this.output = Objects.requireNonNull(output, "output");
            this.workDirectory = Objects.requireNonNull(workDirectory, "workDirectory");
        }

        @Override
        public TimelineRenderResultDTO metadata() {
            return metadata;
        }

        @Override
        public synchronized InputStream stream() {
            if (closed.get() || opened) {
                throw new IllegalStateException("timeline render output is unavailable");
            }
            opened = true;
            try {
                stream = Files.newInputStream(pathGuard.verifyCreatedOutputFile(output), LinkOption.NOFOLLOW_LINKS);
                return stream;
            } catch (IOException | IllegalArgumentException exception) {
                throw new IllegalStateException("timeline render output is unavailable");
            }
        }

        @Override
        public synchronized void close() throws IOException {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            IOException failure = null;
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException exception) {
                    failure = new IOException("timeline render output stream close failed");
                }
            }
            try {
                cleanupWorkDirectory(workDirectory);
            } catch (IOException exception) {
                IOException cleanup = cleanupFailure();
                if (failure == null) {
                    failure = cleanup;
                } else {
                    failure.addSuppressed(cleanup);
                }
            }
            if (failure != null) {
                throw failure;
            }
        }
    }

    private static final class VerifiedFontStager implements FontStager {
        private final Path fontRoot;

        private VerifiedFontStager(Path fontRoot) {
            if (fontRoot == null || !fontRoot.isAbsolute()) {
                throw fontUnavailable();
            }
            this.fontRoot = fontRoot.toAbsolutePath().normalize();
        }

        @Override
        public void stage(Path targetDirectory) throws IOException {
            Path root = verifySource();
            for (Map.Entry<String, String> entry : REQUIRED_FONT_HASHES.entrySet()) {
                Path source = requireFontFile(root, entry.getKey());
                if (!entry.getValue().equals(sha256(source, fontUnavailable()))) {
                    throw fontUnavailable();
                }
                Path target = targetDirectory.resolve(entry.getKey()).normalize();
                if (!target.startsWith(targetDirectory)) {
                    throw fontUnavailable();
                }
                try (InputStream input = Files.newInputStream(source, LinkOption.NOFOLLOW_LINKS);
                     OutputStream output = newOutputStream(target)) {
                    input.transferTo(output);
                }
                Path copied = requireFontFile(targetDirectory, entry.getKey());
                if (!entry.getValue().equals(sha256(copied, fontUnavailable()))) {
                    throw fontUnavailable();
                }
            }
        }

        private Path verifySource() {
            Path root = requireFontDirectory(fontRoot);
            if (!FONT_REGISTRY_SHA256.equals(sha256(requireFontFile(root, FONT_REGISTRY_FILE), fontUnavailable()))) {
                throw fontUnavailable();
            }
            for (Map.Entry<String, String> entry : REQUIRED_FONT_HASHES.entrySet()) {
                Path source = requireFontFile(root, entry.getKey());
                if (!entry.getValue().equals(sha256(source, fontUnavailable()))) {
                    throw fontUnavailable();
                }
            }
            return root;
        }

        private static Path requireFontDirectory(Path candidate) {
            try {
                BasicFileAttributes attributes = Files.readAttributes(candidate, BasicFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS);
                if (!attributes.isDirectory() || attributes.isSymbolicLink() || attributes.isOther()
                    || Files.isSymbolicLink(candidate)) {
                    throw fontUnavailable();
                }
                Path noFollow = candidate.toRealPath(LinkOption.NOFOLLOW_LINKS);
                Path following = candidate.toRealPath();
                if (!noFollow.equals(following) || !Files.isReadable(noFollow)) {
                    throw fontUnavailable();
                }
                return noFollow;
            } catch (IOException | SecurityException exception) {
                throw fontUnavailable();
            }
        }

        private static Path requireFontFile(Path root, String fileName) {
            try {
                Path candidate = root.resolve(fileName).normalize();
                if (!candidate.startsWith(root)) {
                    throw fontUnavailable();
                }
                BasicFileAttributes attributes = Files.readAttributes(candidate, BasicFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS);
                if (!attributes.isRegularFile() || attributes.isSymbolicLink() || attributes.isOther()
                    || Files.isSymbolicLink(candidate)) {
                    throw fontUnavailable();
                }
                Path noFollow = candidate.toRealPath(LinkOption.NOFOLLOW_LINKS);
                Path following = candidate.toRealPath();
                if (!noFollow.equals(following) || !noFollow.startsWith(root) || !Files.isReadable(noFollow)) {
                    throw fontUnavailable();
                }
                return noFollow;
            } catch (IOException | SecurityException exception) {
                throw fontUnavailable();
            }
        }
    }
}
