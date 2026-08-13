package org.dromara.aivideo.infra.timeline.process;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("dev")
class JdkTimelineProcessExecutorTest {

    @TempDir
    Path temporaryDirectory;

    private Path approvedWorkRoot;

    @BeforeEach
    void createApprovedWorkRoot() throws Exception {
        approvedWorkRoot = Files.createDirectories(temporaryDirectory.resolve("approved-work-root"));
    }

    @Test
    void keepsShellMetacharactersAsOneArgumentAndClosesStandardInputImmediately() throws Exception {
        JdkTimelineProcessExecutor executor = executor();

        TimelineProcessResult argument = executor.execute(request("argument", ";|&$()"), () -> false);
        TimelineProcessResult stdin = executor.execute(request("stdin-eof"), () -> false);
        TimelineProcessResult environment = executor.execute(request("environment", "TZ"), () -> false);

        assertThat(argument.status()).isEqualTo(TimelineProcessResult.Status.SUCCEEDED);
        assertThat(argument.stdoutUtf8()).isEqualTo(";|&$()");
        assertThat(stdin.status()).isEqualTo(TimelineProcessResult.Status.SUCCEEDED);
        assertThat(stdin.stdoutUtf8()).isEqualTo("-1");
        assertThat(environment.stdoutUtf8()).isEqualTo("UTC");
    }

    @Test
    void enforcesSeparateHardOutputLimitsForStandardOutputAndError() throws Exception {
        JdkTimelineProcessExecutor executor = executor();

        TimelineProcessResult stdout = executor.execute(requestWithLimit(64, "stdout", "256"), () -> false);
        TimelineProcessResult stderr = executor.execute(requestWithLimit(64, "stderr", "256"), () -> false);

        assertThat(stdout.status()).isEqualTo(TimelineProcessResult.Status.OUTPUT_LIMIT_EXCEEDED);
        assertThat(stderr.status()).isEqualTo(TimelineProcessResult.Status.OUTPUT_LIMIT_EXCEEDED);
        assertThat(stdout.stdout().length).isLessThanOrEqualTo(64);
        assertThat(stderr.stderrBytes()).isLessThanOrEqualTo(64);
    }

    @Test
    void reportsNonZeroExitTimeoutAndCancellationWithoutThrowingRawProcessDetails() throws Exception {
        JdkTimelineProcessExecutor executor = executor();

        TimelineProcessResult nonZero = executor.execute(request("exit", "23"), () -> false);
        TimelineProcessResult timedOut = executor.execute(request("timeout-operation", Duration.ofMillis(80), "ready-sleep",
            approvedWorkRoot.resolve("timeout-ready").toString(), "10000"), () -> false);
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            String operationId = "cancelled-operation";
            Future<TimelineProcessResult> cancelled = worker.submit(() -> executor.execute(
                request(operationId, Duration.ofSeconds(10), "ready-sleep",
                    approvedWorkRoot.resolve("cancel-ready").toString(), "10000"), () -> false));
            awaitFile(approvedWorkRoot.resolve("cancel-ready"));
            executor.cancel(operationId, "attempt-1");

            assertThat(cancelled.get()).extracting(TimelineProcessResult::status)
                .isEqualTo(TimelineProcessResult.Status.CANCELLED);
        } finally {
            worker.shutdownNow();
        }
        assertThat(nonZero.status()).isEqualTo(TimelineProcessResult.Status.NON_ZERO_EXIT);
        assertThat(nonZero.exitCode()).isEqualTo(23);
        assertThat(timedOut.status()).isEqualTo(TimelineProcessResult.Status.TIMED_OUT);
    }

    @Test
    void cancellationStopsOnlyTheRegisteredProcessTree() throws Exception {
        JdkTimelineProcessExecutor executor = executor();
        String operationId = "tree-operation";
        Path childReady = approvedWorkRoot.resolve("child-ready");
        Path childPid = approvedWorkRoot.resolve("child.pid");
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            Future<TimelineProcessResult> result = worker.submit(() -> executor.execute(
                request(operationId, Duration.ofSeconds(10), "spawn-child", childReady.toString(), childPid.toString()),
                () -> false));
            awaitFile(childReady);
            long pid = Long.parseLong(Files.readString(childPid));
            executor.cancel("different-operation", "attempt-1");
            assertThat(result.isDone()).isFalse();
            assertThat(ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false)).isTrue();
            executor.cancel(operationId, "attempt-1");

            assertThat(result.get()).extracting(TimelineProcessResult::status)
                .isEqualTo(TimelineProcessResult.Status.CANCELLED);
            assertThat(ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false)).isFalse();
        } finally {
            worker.shutdownNow();
        }
    }

    @Test
    void preservesCallerInterruptionAfterStoppingTheRegisteredProcess() throws Exception {
        JdkTimelineProcessExecutor executor = executor();
        Path ready = approvedWorkRoot.resolve("interrupt-ready");
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicBoolean interruptedFlagPreserved = new AtomicBoolean();
        Thread worker = Thread.ofPlatform().start(() -> {
            try {
                executor.execute(request("interrupted-operation", Duration.ofSeconds(10), "ready-sleep",
                    ready.toString(), "10000"), () -> false);
            } catch (InterruptedException exception) {
                interruptedFlagPreserved.set(Thread.currentThread().isInterrupted());
                failure.set(exception);
            }
        });
        awaitFile(ready);

        worker.interrupt();
        worker.join(Duration.ofSeconds(5));

        assertThat(worker.isAlive()).isFalse();
        assertThat(failure.get()).isInstanceOf(InterruptedException.class);
        assertThat(interruptedFlagPreserved.get()).isTrue();
    }

    @Test
    void rejectsUnsafeEnvironmentKeysBeforeStartingAProcess() {
        assertThatThrownBy(() -> new TimelineProcessRequest("unsafe-environment", "attempt-1",
            javaCommand("argument", "value"), temporaryDirectory, Map.of("UNSAFE_SECRET", "value"),
            Duration.ofSeconds(1), 1024, 1024))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsCommandsAndWorkingDirectoriesOutsideItsApprovedExecutionPolicy() throws Exception {
        JdkTimelineProcessExecutor executor = executor();
        Path unapprovedExecutable = Files.writeString(approvedWorkRoot.resolve("unapproved-tool"), "fixture");
        Path outsideWorkRoot = Files.createDirectories(temporaryDirectory.resolve("outside-work-root"));

        TimelineProcessRequest unapprovedCommand = new TimelineProcessRequest("unapproved-command", "attempt-1",
            List.of(unapprovedExecutable.toString()), approvedWorkRoot, Map.of("TZ", "UTC"), Duration.ofSeconds(1),
            1024, 1024);
        TimelineProcessRequest unapprovedDirectory = new TimelineProcessRequest("unapproved-directory", "attempt-1",
            javaCommand("argument", "value"), outsideWorkRoot, Map.of("TZ", "UTC"), Duration.ofSeconds(1), 1024,
            1024);

        assertThat(executor.execute(unapprovedCommand, () -> false).status())
            .isEqualTo(TimelineProcessResult.Status.START_FAILED);
        assertThat(executor.execute(unapprovedDirectory, () -> false).status())
            .isEqualTo(TimelineProcessResult.Status.START_FAILED);
    }

    private TimelineProcessRequest request(String mode, String... arguments) {
        return request("process-" + mode, Duration.ofSeconds(2), mode, arguments);
    }

    private TimelineProcessRequest requestWithLimit(long limit, String mode, String... arguments) {
        return new TimelineProcessRequest("limited-" + mode, "attempt-1", javaCommand(mode, arguments),
            approvedWorkRoot, Map.of("TZ", "UTC"), Duration.ofSeconds(2), limit, limit);
    }

    private TimelineProcessRequest request(String operationId, Duration timeout, String mode, String... arguments) {
        return new TimelineProcessRequest(operationId, "attempt-1", javaCommand(mode, arguments), approvedWorkRoot,
            Map.of("TZ", "UTC"), timeout, 64 * 1024, 64 * 1024);
    }

    private JdkTimelineProcessExecutor executor() {
        return new JdkTimelineProcessExecutor(new org.dromara.aivideo.infra.timeline.path.TimelinePathGuard(
            approvedWorkRoot), List.of(Path.of(javaExecutable())));
    }

    private static List<String> javaCommand(String mode, String... arguments) {
        java.util.ArrayList<String> command = new java.util.ArrayList<>();
        command.add(javaExecutable());
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add(FakeProcessMain.class.getName());
        command.add(mode);
        command.addAll(List.of(arguments));
        return command;
    }

    private static String javaExecutable() {
        String executable = System.getProperty("os.name", "").toLowerCase().contains("win") ? "java.exe" : "java";
        return Path.of(System.getProperty("java.home"), "bin", executable).toString();
    }

    private static void awaitFile(Path path) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (!Files.exists(path) && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertThat(Files.exists(path)).isTrue();
    }

}
