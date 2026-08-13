package org.dromara.aivideo.infra.timeline;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("dev")
class TimelineInfrastructurePropertiesTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void disabledConfigurationDoesNotRequireMediaInfrastructure() {
        TimelineInfrastructureProperties properties = new TimelineInfrastructureProperties();
        properties.setEnabled(false);

        assertThatCode(properties::validate).doesNotThrowAnyException();
    }

    @Test
    void enabledConfigurationAcceptsApprovedLocalInfrastructure() throws Exception {
        TimelineInfrastructureProperties properties = validEnabledProperties();

        assertThatCode(properties::validate).doesNotThrowAnyException();
    }

    @Test
    void enabledConfigurationRejectsInvalidConcurrencyLimitsBeforeScheduling() throws Exception {
        TimelineInfrastructureProperties properties = validEnabledProperties();
        properties.setPerUserConcurrencyLimit(3);
        properties.setSystemConcurrencyLimit(2);

        assertThatThrownBy(properties::validate)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("perUserConcurrencyLimit");
    }

    @Test
    void enabledAiConfigurationRejectsMissingApiKey() throws Exception {
        TimelineInfrastructureProperties properties = validEnabledProperties();
        properties.getAi().setEnabled(true);
        properties.getAi().setBaseUrl("https://api.example.test/v1");
        properties.getAi().setModel("timeline-assistant");
        properties.getAi().setApiKey(" ");

        assertThatThrownBy(properties::validate)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("apiKey");
    }

    @Test
    void enabledConfigurationRejectsWorkerIdsLongerThanTheTaskContractLimit() throws Exception {
        TimelineInfrastructureProperties properties = validEnabledProperties();
        properties.setWorkerId("w".repeat(129));

        assertThatThrownBy(properties::validate)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("workerId");
    }

    @Test
    void enabledConfigurationRejectsMissingOrRelativeMediaBinaries() throws Exception {
        TimelineInfrastructureProperties missingFfmpeg = validEnabledProperties();
        missingFfmpeg.setFfmpegPath(temporaryDirectory.resolve("missing-ffmpeg.exe").toString());
        assertInvalid(missingFfmpeg, "ffmpegPath");

        TimelineInfrastructureProperties missingFfprobe = validEnabledProperties();
        missingFfprobe.setFfprobePath(temporaryDirectory.resolve("missing-ffprobe.exe").toString());
        assertInvalid(missingFfprobe, "ffprobePath");

        TimelineInfrastructureProperties relativeFfmpeg = validEnabledProperties();
        relativeFfmpeg.setFfmpegPath("relative/ffmpeg");
        assertInvalid(relativeFfmpeg, "ffmpegPath");
    }

    @Test
    void enabledConfigurationRejectsDirectoriesInPlaceOfExecutableBinaries() throws Exception {
        TimelineInfrastructureProperties properties = validEnabledProperties();
        Path directory = Files.createDirectories(temporaryDirectory.resolve("not-an-executable"));
        properties.setFfprobePath(directory.toAbsolutePath().toString());

        assertInvalid(properties, "ffprobePath");
    }

    @Test
    void enabledConfigurationRejectsInvalidWorkAndFontRoots() throws Exception {
        TimelineInfrastructureProperties invalidWorkRoot = validEnabledProperties();
        Path workFile = Files.createFile(temporaryDirectory.resolve("work-root-file"));
        invalidWorkRoot.setWorkRoot(workFile.toAbsolutePath().toString());
        assertInvalid(invalidWorkRoot, "workRoot");

        TimelineInfrastructureProperties missingFont = validEnabledProperties();
        Files.delete(Path.of(missingFont.getFontRoot()).resolve("NotoSerifCJKsc-Regular.otf"));
        assertInvalid(missingFont, "fontRoot");
    }

    @Test
    void enabledConfigurationRejectsUnsafeLimitsBeforeScheduling() throws Exception {
        TimelineInfrastructureProperties zeroOutputLimit = validEnabledProperties();
        zeroOutputLimit.setMaxOutputBytes(0);
        assertInvalid(zeroOutputLimit, "maxOutputBytes");

        TimelineInfrastructureProperties unboundedOutputLimit = validEnabledProperties();
        unboundedOutputLimit.setMaxOutputBytes(Long.MAX_VALUE);
        assertInvalid(unboundedOutputLimit, "maxOutputBytes");

        TimelineInfrastructureProperties zeroTimeout = validEnabledProperties();
        zeroTimeout.setProcessTimeout(Duration.ZERO);
        assertInvalid(zeroTimeout, "processTimeout");

        TimelineInfrastructureProperties tooManyPerUser = validEnabledProperties();
        tooManyPerUser.setPerUserConcurrencyLimit(101);
        assertInvalid(tooManyPerUser, "perUserConcurrencyLimit");

        TimelineInfrastructureProperties tooManySystem = validEnabledProperties();
        tooManySystem.setSystemConcurrencyLimit(101);
        assertInvalid(tooManySystem, "systemConcurrencyLimit");

        TimelineInfrastructureProperties invalidRecoveryBatch = validEnabledProperties();
        invalidRecoveryBatch.setRecoveryBatchLimit(101);
        assertInvalid(invalidRecoveryBatch, "recoveryBatchLimit");
    }

    @Test
    void enabledAiConfigurationRejectsInsecureEndpointsAndUnsafeResponseLimits() throws Exception {
        TimelineInfrastructureProperties insecureEndpoint = validEnabledProperties();
        insecureEndpoint.getAi().setEnabled(true);
        insecureEndpoint.getAi().setBaseUrl("http://api.example.test/v1");
        insecureEndpoint.getAi().setApiKey("test-key");
        insecureEndpoint.getAi().setModel("timeline-assistant");
        assertInvalid(insecureEndpoint, "ai.baseUrl");

        TimelineInfrastructureProperties zeroResponseLimit = validEnabledProperties();
        zeroResponseLimit.getAi().setEnabled(true);
        zeroResponseLimit.getAi().setBaseUrl("https://api.example.test/v1");
        zeroResponseLimit.getAi().setApiKey("test-key");
        zeroResponseLimit.getAi().setModel("timeline-assistant");
        zeroResponseLimit.getAi().setMaxResponseBytes(0);
        assertInvalid(zeroResponseLimit, "ai.maxResponseBytes");

        TimelineInfrastructureProperties unboundedResponseLimit = validEnabledProperties();
        unboundedResponseLimit.getAi().setEnabled(true);
        unboundedResponseLimit.getAi().setBaseUrl("https://api.example.test/v1");
        unboundedResponseLimit.getAi().setApiKey("test-key");
        unboundedResponseLimit.getAi().setModel("timeline-assistant");
        unboundedResponseLimit.getAi().setMaxResponseBytes(Long.MAX_VALUE);
        assertInvalid(unboundedResponseLimit, "ai.maxResponseBytes");
    }

    private TimelineInfrastructureProperties validEnabledProperties() throws Exception {
        Path root = Files.createTempDirectory(temporaryDirectory, "timeline-properties-");
        Path binaries = Files.createDirectories(root.resolve("bin"));
        Path workRoot = Files.createDirectories(root.resolve("work"));
        Path fontRoot = Files.createDirectories(root.resolve("fonts"));
        Path ffmpeg = Files.createFile(binaries.resolve("ffmpeg.exe"));
        Path ffprobe = Files.createFile(binaries.resolve("ffprobe.exe"));
        Files.createFile(fontRoot.resolve("NotoSansCJKsc-Regular.otf"));
        Files.createFile(fontRoot.resolve("NotoSerifCJKsc-Regular.otf"));

        TimelineInfrastructureProperties properties = new TimelineInfrastructureProperties();
        properties.setEnabled(true);
        properties.setFfmpegPath(ffmpeg.toAbsolutePath().toString());
        properties.setFfprobePath(ffprobe.toAbsolutePath().toString());
        properties.setWorkRoot(workRoot.toAbsolutePath().toString());
        properties.setFontRoot(fontRoot.toAbsolutePath().toString());
        properties.setProcessTimeout(Duration.ofSeconds(30));
        properties.setMaxOutputBytes(64L * 1024L * 1024L);
        properties.setPerUserConcurrencyLimit(1);
        properties.setSystemConcurrencyLimit(2);
        properties.setWorkerId("timeline-test-worker");
        properties.setPollDelay(Duration.ofSeconds(1));
        properties.setRecoveryBatchLimit(10);
        properties.getAi().setEnabled(false);
        return properties;
    }

    private static void assertInvalid(TimelineInfrastructureProperties properties, String property) {
        assertThatThrownBy(properties::validate)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining(property);
    }
}
