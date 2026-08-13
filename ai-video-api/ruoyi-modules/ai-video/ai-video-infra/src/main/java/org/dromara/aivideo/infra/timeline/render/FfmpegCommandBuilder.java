package org.dromara.aivideo.infra.timeline.render;

import org.dromara.aivideo.creation.enums.CreationAssetType;
import org.dromara.aivideo.infra.timeline.process.TimelineProcessRequest;
import org.dromara.aivideo.timeline.enums.TimelineOutputQuality;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Builds a shell-free FFmpeg process request from a fully controlled render plan. */
public final class FfmpegCommandBuilder {

    private static final long PROCESS_OUTPUT_LIMIT_BYTES = 1024L * 1024L;
    private static final Map<String, String> PROCESS_ENVIRONMENT = Map.of(
        "LANG", "C",
        "LC_ALL", "C",
        "TZ", "UTC"
    );

    public TimelineProcessRequest build(TimelineRenderPlan plan,
                                        Path ffmpegBinary,
                                        Path workingDirectory,
                                        Duration timeout) {
        Objects.requireNonNull(plan, "plan");
        Path binary = absolute(ffmpegBinary);
        Path directory = absolute(workingDirectory);
        List<String> command = new ArrayList<>();
        command.add(binary.toString());
        appendPreamble(command);
        for (TimelineRenderPlan.RenderInput input : plan.renderInputs()) {
            appendRenderInput(command, directory, input);
        }
        command.addAll(List.of(
            "-/filter_complex", child(directory, TimelineRenderPlan.FILTER_FILE_NAME).toString(),
            "-map", "[vout]", "-map", "[aout]",
            "-c:v", "libx264", "-preset", preset(plan.quality()), "-crf", crf(plan.quality()),
            "-pix_fmt", "yuv420p", "-r", "30",
            "-c:a", "aac", "-movflags", "+faststart", "-n", "-f", "mp4",
            child(directory, TimelineRenderPlan.OUTPUT_FILE_NAME).toString()
        ));
        return new TimelineProcessRequest(plan.executionId(), plan.attemptId(), command, directory,
            PROCESS_ENVIRONMENT, timeout, PROCESS_OUTPUT_LIMIT_BYTES, PROCESS_OUTPUT_LIMIT_BYTES);
    }

    public TimelineProcessRequest buildPipTail(TimelineRenderPlan plan,
                                               TimelineRenderPlan.PipTail tail,
                                               Path ffmpegBinary,
                                               Path workingDirectory,
                                               Duration timeout) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(tail, "tail");
        if (!plan.pipTails().contains(tail)) {
            throw new IllegalArgumentException("PIP tail is not part of the render plan");
        }
        Path binary = absolute(ffmpegBinary);
        Path directory = absolute(workingDirectory);
        List<String> command = new ArrayList<>();
        command.add(binary.toString());
        appendPreamble(command);
        command.addAll(List.of(
            "-noautorotate", "-i", child(directory, tail.sourceAlias()).toString(),
            "-ss", nonNegativeSeconds(tail.sourceStartMs()),
            "-t", seconds(tail.sourceEndMs() - tail.sourceStartMs()),
            "-map", "0:v:0", "-an",
            "-c:v", "libx264", "-preset", preset(plan.quality()), "-crf", crf(plan.quality()),
            "-pix_fmt", "yuv420p", "-r", "30", "-movflags", "+faststart", "-n", "-f", "mp4",
            child(directory, tail.renderAlias()).toString()
        ));
        return new TimelineProcessRequest(plan.executionId(), plan.attemptId(), command, directory,
            PROCESS_ENVIRONMENT, timeout, PROCESS_OUTPUT_LIMIT_BYTES, PROCESS_OUTPUT_LIMIT_BYTES);
    }

    private static void appendPreamble(List<String> command) {
        command.addAll(List.of(
            "-nostdin", "-hide_banner", "-nostats", "-progress", "pipe:1",
            "-protocol_whitelist", "file,pipe"
        ));
    }

    private static void appendRenderInput(List<String> command,
                                          Path directory,
                                          TimelineRenderPlan.RenderInput input) {
        if (input.streamLoop()) {
            command.addAll(List.of("-stream_loop", "-1"));
        }
        if (input.assetType() == CreationAssetType.VIDEO) {
            command.add("-noautorotate");
        }
        if (input.assetType() == CreationAssetType.IMAGE) {
            command.addAll(List.of("-loop", "1", "-framerate", "30"));
        }
        command.add("-i");
        command.add(child(directory, input.alias()).toString());
    }

    private static String seconds(long milliseconds) {
        if (milliseconds <= 0) {
            throw new IllegalArgumentException("timeline duration must be positive");
        }
        return decimalSeconds(milliseconds);
    }

    private static String nonNegativeSeconds(long milliseconds) {
        if (milliseconds < 0) {
            throw new IllegalArgumentException("timeline offset must not be negative");
        }
        return decimalSeconds(milliseconds);
    }

    private static String decimalSeconds(long milliseconds) {
        return BigDecimal.valueOf(milliseconds, 3).stripTrailingZeros().toPlainString();
    }

    private static String preset(TimelineOutputQuality quality) {
        return switch (quality) {
            case STANDARD -> "medium";
            case HIGH -> "slow";
        };
    }

    private static String crf(TimelineOutputQuality quality) {
        return switch (quality) {
            case STANDARD -> "23";
            case HIGH -> "18";
        };
    }

    private static Path absolute(Path path) {
        if (path == null || !path.isAbsolute()) {
            throw new IllegalArgumentException("timeline process path must be absolute");
        }
        return path.toAbsolutePath().normalize();
    }

    private static Path child(Path parent, String fileName) {
        Path child = parent.resolve(fileName).normalize();
        if (!child.startsWith(parent)) {
            throw new IllegalArgumentException("timeline process path escapes working directory");
        }
        return child;
    }
}
