package org.dromara.aivideo.infra.timeline.render;

import org.dromara.aivideo.creation.enums.CreationAssetType;
import org.dromara.aivideo.infra.timeline.process.TimelineProcessRequest;
import org.dromara.aivideo.timeline.enums.TimelineAssetUsageType;
import org.dromara.aivideo.timeline.enums.TimelineOutputQuality;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("dev")
class FfmpegCommandBuilderTest {

    @TempDir
    Path temporaryDirectory;

    private final FfmpegCommandBuilder builder = new FfmpegCommandBuilder();

    @Test
    void usesOnlyFixedShellFreeArgumentsAndGeneratedAliases() {
        TimelineRenderPlan plan = plan(TimelineOutputQuality.STANDARD,
            "subtitle{\\an9}; -filter_complex [owned]");
        Path binary = temporaryDirectory.resolve("bin").resolve("ffmpeg.exe").toAbsolutePath();

        TimelineProcessRequest request = builder.build(plan, binary, temporaryDirectory, Duration.ofSeconds(30));

        assertThat(request.command()).containsSubsequence(
            binary.toString(), "-nostdin", "-hide_banner", "-nostats", "-progress", "pipe:1",
            "-protocol_whitelist", "file,pipe", "-/filter_complex",
            temporaryDirectory.resolve("filter.txt").toString(), "-map", "[vout]", "-map", "[aout]",
            "-c:v", "libx264", "-preset", "medium", "-crf", "23", "-pix_fmt", "yuv420p",
            "-r", "30", "-c:a", "aac", "-f", "mp4", temporaryDirectory.resolve("output.mp4").toString()
        );
        assertThat(request.command()).containsSubsequence("-loop", "1", "-framerate", "30", "-i",
            temporaryDirectory.resolve("input-0002.png").toString());
        assertThat(request.command()).containsSubsequence("-noautorotate", "-i",
            temporaryDirectory.resolve("input-0001.mp4").toString());
        assertThat(request.command()).containsSubsequence("-stream_loop", "-1", "-noautorotate", "-i",
            temporaryDirectory.resolve("pip-0001.mp4").toString());
        assertThat(request.command()).doesNotContain(temporaryDirectory.resolve("input-0003.mp4").toString());
        assertThat(request.command()).contains("-n").doesNotContain("-y");
        assertThat(request.command()).doesNotContain("subtitle{\\an9}; -filter_complex [owned]", "original.mp4");
        assertThat(request.environment()).containsExactlyInAnyOrderEntriesOf(
            java.util.Map.of("LANG", "C", "LC_ALL", "C", "TZ", "UTC"));
        assertThat(request.stdoutLimitBytes()).isEqualTo(1024L * 1024L);
        assertThat(request.stderrLimitBytes()).isEqualTo(1024L * 1024L);

        TimelineProcessRequest tailRequest = builder.buildPipTail(plan, plan.pipTails().getFirst(), binary,
            temporaryDirectory, Duration.ofSeconds(30));
        assertThat(tailRequest.command()).containsSubsequence(
            binary.toString(), "-nostdin", "-hide_banner", "-nostats", "-progress", "pipe:1",
            "-protocol_whitelist", "file,pipe", "-i", temporaryDirectory.resolve("input-0003.mp4").toString(),
            "-ss", "1", "-t", "2", "-map", "0:v:0", "-an", "-c:v", "libx264", "-pix_fmt", "yuv420p",
            "-r", "30", "-f", "mp4", temporaryDirectory.resolve("pip-0001.mp4").toString()
        );
        assertThat(tailRequest.command()).containsSubsequence("-noautorotate", "-i",
            temporaryDirectory.resolve("input-0003.mp4").toString());
        assertThat(tailRequest.command()).doesNotContain("subtitle{\\an9}; -filter_complex [owned]");
    }

    @Test
    void mapsHighQualityToTheFrozenEncoderSettingsWithoutChangingArgumentCount() {
        Path binary = temporaryDirectory.resolve("bin").resolve("ffmpeg.exe").toAbsolutePath();
        TimelineProcessRequest standard = builder.build(plan(TimelineOutputQuality.STANDARD, "plain"), binary,
            temporaryDirectory, Duration.ofSeconds(30));
        TimelineProcessRequest high = builder.build(plan(TimelineOutputQuality.HIGH,
            "text; -i unexpected.mp4"), binary, temporaryDirectory, Duration.ofSeconds(30));

        assertThat(high.command()).containsSubsequence("-preset", "slow", "-crf", "18");
        assertThat(high.command()).hasSameSizeAs(standard.command());
        assertThat(high.command()).doesNotContain("text; -i unexpected.mp4");
    }

    @Test
    void permitsZeroPipSourceStartWhileKeepingItsPositiveTailDuration() {
        TimelineRenderPlan plan = planWithPipStart(0L);
        Path binary = temporaryDirectory.resolve("bin").resolve("ffmpeg.exe").toAbsolutePath();

        TimelineProcessRequest request = builder.buildPipTail(plan, plan.pipTails().getFirst(), binary,
            temporaryDirectory, Duration.ofSeconds(30));

        assertThat(request.command()).containsSubsequence("-ss", "0", "-t", "1");
    }

    private static TimelineRenderPlan plan(TimelineOutputQuality quality, String untrustedAssText) {
        return plan(quality, untrustedAssText, 1_000L, 3_000L);
    }

    private static TimelineRenderPlan planWithPipStart(long sourceStartMs) {
        return plan(TimelineOutputQuality.STANDARD, "plain", sourceStartMs, sourceStartMs + 1_000L);
    }

    private static TimelineRenderPlan plan(TimelineOutputQuality quality, String untrustedAssText,
                                           long sourceStartMs, long sourceEndMs) {
        List<TimelineRenderPlan.Input> inputs = List.of(
            input("input-0001.mp4", CreationAssetType.VIDEO, TimelineAssetUsageType.BASE_VIDEO, false),
            input("input-0002.png", CreationAssetType.IMAGE, TimelineAssetUsageType.IMAGE, false),
            input("input-0003.mp4", CreationAssetType.VIDEO, TimelineAssetUsageType.PIP_VIDEO, true),
            input("input-0004.wav", CreationAssetType.AUDIO, TimelineAssetUsageType.PRIMARY_AUDIO, false)
        );
        return new TimelineRenderPlan("execution-1", "attempt-1", 3_000L, inputs, List.of(
            new TimelineRenderPlan.RenderInput("input-0001.mp4", CreationAssetType.VIDEO,
                TimelineAssetUsageType.BASE_VIDEO, false),
            new TimelineRenderPlan.RenderInput("input-0002.png", CreationAssetType.IMAGE,
                TimelineAssetUsageType.IMAGE, false),
            new TimelineRenderPlan.RenderInput("pip-0001.mp4", CreationAssetType.VIDEO,
                TimelineAssetUsageType.PIP_VIDEO, true),
            new TimelineRenderPlan.RenderInput("input-0004.wav", CreationAssetType.AUDIO,
                TimelineAssetUsageType.PRIMARY_AUDIO, false)
        ), List.of(new TimelineRenderPlan.PipTail("input-0003.mp4", "pip-0001.mp4", sourceStartMs, sourceEndMs)),
            "[Script Info]\n; " + untrustedAssText, "[0:v]null[vout];[0:a]anull[aout]", quality);
    }

    private static TimelineRenderPlan.Input input(String alias, CreationAssetType assetType,
                                                   TimelineAssetUsageType usageType, boolean loop) {
        return new TimelineRenderPlan.Input(alias, "1", "a".repeat(64), 1_000L, assetType, usageType, loop);
    }
}
