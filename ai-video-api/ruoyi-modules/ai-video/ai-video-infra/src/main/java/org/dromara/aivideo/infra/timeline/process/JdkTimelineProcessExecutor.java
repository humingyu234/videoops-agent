package org.dromara.aivideo.infra.timeline.process;

import org.dromara.aivideo.infra.timeline.path.TimelinePathGuard;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.concurrent.locks.LockSupport;

/**
 * JDK-only, shell-free implementation with bounded concurrent stream readers.
 */
public final class JdkTimelineProcessExecutor implements TimelineProcessExecutor {

    private static final long POLL_INTERVAL_MILLIS = 25;
    private static final long TERMINATION_GRACE_MILLIS = 200;
    private static final long FORCED_TERMINATION_WAIT_MILLIS = 1000;
    private static final Duration OUTPUT_READER_WAIT = Duration.ofSeconds(2);
    private static final List<String> REQUIRED_SYSTEM_ENVIRONMENT_KEYS = List.of("SystemRoot", "WINDIR");

    private final TimelinePathGuard pathGuard;
    private final Set<Path> approvedExecutables;
    private final ConcurrentMap<ProcessKey, ActiveProcess> activeProcesses = new ConcurrentHashMap<>();

    /**
     * Creates an executor that can only start the specified verified binaries within one guarded work root.
     */
    public JdkTimelineProcessExecutor(TimelinePathGuard pathGuard, Collection<Path> approvedExecutables) {
        this.pathGuard = Objects.requireNonNull(pathGuard, "pathGuard");
        if (approvedExecutables == null || approvedExecutables.isEmpty()) {
            throw new IllegalArgumentException("timeline executable policy is invalid");
        }
        Set<Path> verified = new LinkedHashSet<>();
        for (Path executable : approvedExecutables) {
            verified.add(requireVerifiedExecutable(executable));
        }
        this.approvedExecutables = Set.copyOf(verified);
    }

    @Override
    public TimelineProcessResult execute(TimelineProcessRequest request,
                                         BooleanSupplier cancellationRequested) throws InterruptedException {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(cancellationRequested, "cancellationRequested");
        long startedNanos = System.nanoTime();
        ProcessKey key = new ProcessKey(request.executionId(), request.attemptId());
        ActiveProcess active = new ActiveProcess();
        if (activeProcesses.putIfAbsent(key, active) != null) {
            return result(TimelineProcessResult.Status.START_FAILED, -1, new byte[0], 0, startedNanos);
        }
        Process process = null;
        boolean started = false;
        try {
            if (isCancellationRequested(active, cancellationRequested)) {
                return result(TimelineProcessResult.Status.CANCELLED, -1, new byte[0], 0, startedNanos);
            }
            Path workingDirectory = requireWorkingDirectory(request.workingDirectory());
            ProcessBuilder builder = new ProcessBuilder(requireApprovedCommand(request.command()));
            builder.directory(workingDirectory.toFile());
            configureEnvironment(builder.environment(), request.environment());
            process = builder.start();
            started = true;
            active.process = process;
            closeStandardInput(process);
            if (isCancellationRequested(active, cancellationRequested)) {
                terminateProcessTree(process);
                return result(TimelineProcessResult.Status.CANCELLED, safeExitCode(process), new byte[0], 0,
                    startedNanos);
            }
            return awaitCompletion(process, active, cancellationRequested, request, startedNanos);
        } catch (InterruptedException exception) {
            forceTerminateProcessTree(process);
            Thread.currentThread().interrupt();
            throw exception;
        } catch (IOException | SecurityException exception) {
            forceTerminateProcessTree(process);
            return result(started ? TimelineProcessResult.Status.PROCESS_FAILED : TimelineProcessResult.Status.START_FAILED,
                safeExitCode(process), new byte[0], 0, startedNanos);
        } catch (RuntimeException exception) {
            forceTerminateProcessTree(process);
            return result(started ? TimelineProcessResult.Status.PROCESS_FAILED : TimelineProcessResult.Status.START_FAILED,
                safeExitCode(process), new byte[0], 0, startedNanos);
        } finally {
            activeProcesses.remove(key, active);
        }
    }

    @Override
    public void cancel(String executionId, String attemptId) {
        if (executionId == null || attemptId == null) {
            return;
        }
        ActiveProcess active = activeProcesses.get(new ProcessKey(executionId, attemptId));
        if (active == null) {
            return;
        }
        active.cancellationRequested.set(true);
        Process process = active.process;
        if (process == null) {
            return;
        }
        try {
            terminateProcessTree(process);
        } catch (InterruptedException exception) {
            forceTerminateProcessTree(process);
            Thread.currentThread().interrupt();
        }
    }

    private TimelineProcessResult awaitCompletion(Process process, ActiveProcess active,
                                                   BooleanSupplier cancellationRequested,
                                                   TimelineProcessRequest request,
                                                   long startedNanos) throws InterruptedException {
        AtomicBoolean outputLimitExceeded = new AtomicBoolean();
        AtomicBoolean readerFailed = new AtomicBoolean();
        try (InputStream stdout = process.getInputStream(); InputStream stderr = process.getErrorStream()) {
            ExecutorService readers = Executors.newVirtualThreadPerTaskExecutor();
            try {
                Future<byte[]> stdoutReader = readers.submit(() -> readStdout(stdout, request.stdoutLimitBytes(),
                    outputLimitExceeded, readerFailed));
                Future<Long> stderrReader = readers.submit(() -> readStderr(stderr, request.stderrLimitBytes(),
                    outputLimitExceeded, readerFailed));
                TimelineProcessResult.Status status = waitForProcess(process, active, cancellationRequested,
                    request.timeout(), startedNanos, outputLimitExceeded, readerFailed);
                if (process.isAlive()) {
                    terminateProcessTree(process);
                }
                byte[] capturedStdout = awaitStdout(stdoutReader, readerFailed);
                long stderrBytes = awaitStderrBytes(stderrReader, readerFailed);
                if (outputLimitExceeded.get()) {
                    status = TimelineProcessResult.Status.OUTPUT_LIMIT_EXCEEDED;
                } else if (readerFailed.get()) {
                    status = TimelineProcessResult.Status.PROCESS_FAILED;
                }
                return result(status, safeExitCode(process), capturedStdout, stderrBytes, startedNanos);
            } catch (InterruptedException exception) {
                forceTerminateProcessTree(process);
                throw exception;
            } finally {
                readers.shutdownNow();
            }
        } catch (IOException exception) {
            forceTerminateProcessTree(process);
            return result(TimelineProcessResult.Status.PROCESS_FAILED, safeExitCode(process), new byte[0], 0,
                startedNanos);
        }
    }

    private static TimelineProcessResult.Status waitForProcess(Process process, ActiveProcess active,
                                                                BooleanSupplier cancellationRequested,
                                                                Duration timeout, long startedNanos,
                                                                AtomicBoolean outputLimitExceeded,
                                                                AtomicBoolean readerFailed) throws InterruptedException {
        long timeoutNanos = timeoutNanos(timeout);
        while (true) {
            if (isCancellationRequested(active, cancellationRequested)) {
                terminateProcessTree(process);
                return TimelineProcessResult.Status.CANCELLED;
            }
            if (outputLimitExceeded.get()) {
                terminateProcessTree(process);
                return TimelineProcessResult.Status.OUTPUT_LIMIT_EXCEEDED;
            }
            if (readerFailed.get()) {
                terminateProcessTree(process);
                return TimelineProcessResult.Status.PROCESS_FAILED;
            }
            if (process.waitFor(POLL_INTERVAL_MILLIS, TimeUnit.MILLISECONDS)) {
                if (isCancellationRequested(active, cancellationRequested)) {
                    return TimelineProcessResult.Status.CANCELLED;
                }
                if (outputLimitExceeded.get()) {
                    return TimelineProcessResult.Status.OUTPUT_LIMIT_EXCEEDED;
                }
                if (readerFailed.get()) {
                    return TimelineProcessResult.Status.PROCESS_FAILED;
                }
                return process.exitValue() == 0 ? TimelineProcessResult.Status.SUCCEEDED
                    : TimelineProcessResult.Status.NON_ZERO_EXIT;
            }
            if (System.nanoTime() - startedNanos >= timeoutNanos) {
                terminateProcessTree(process);
                return TimelineProcessResult.Status.TIMED_OUT;
            }
        }
    }

    private static byte[] awaitStdout(Future<byte[]> reader, AtomicBoolean readerFailed) throws InterruptedException {
        try {
            byte[] stdout = reader.get(OUTPUT_READER_WAIT.toMillis(), TimeUnit.MILLISECONDS);
            return stdout == null ? new byte[0] : stdout;
        } catch (TimeoutException | ExecutionException exception) {
            reader.cancel(true);
            readerFailed.set(true);
            return new byte[0];
        }
    }

    private static long awaitStderrBytes(Future<Long> reader, AtomicBoolean readerFailed) throws InterruptedException {
        try {
            Long bytes = reader.get(OUTPUT_READER_WAIT.toMillis(), TimeUnit.MILLISECONDS);
            return bytes == null ? 0 : bytes;
        } catch (TimeoutException | ExecutionException exception) {
            reader.cancel(true);
            readerFailed.set(true);
            return 0;
        }
    }

    private static byte[] readStdout(InputStream input, long limit, AtomicBoolean outputLimitExceeded,
                                     AtomicBoolean readerFailed) {
        ByteArrayOutputStream captured = new ByteArrayOutputStream((int) Math.min(limit, 8192));
        long capturedBytes = 0;
        byte[] buffer = new byte[8192];
        try {
            int read;
            while ((read = input.read(buffer)) != -1) {
                long remaining = limit - capturedBytes;
                if (read > remaining) {
                    if (remaining > 0) {
                        captured.write(buffer, 0, (int) remaining);
                    }
                    outputLimitExceeded.set(true);
                    break;
                }
                captured.write(buffer, 0, read);
                capturedBytes += read;
            }
            return captured.toByteArray();
        } catch (IOException exception) {
            readerFailed.set(true);
            return new byte[0];
        }
    }

    private static long readStderr(InputStream input, long limit, AtomicBoolean outputLimitExceeded,
                                   AtomicBoolean readerFailed) {
        long capturedBytes = 0;
        byte[] buffer = new byte[8192];
        try {
            int read;
            while ((read = input.read(buffer)) != -1) {
                long remaining = limit - capturedBytes;
                if (read > remaining) {
                    outputLimitExceeded.set(true);
                    break;
                }
                capturedBytes += read;
            }
            return capturedBytes;
        } catch (IOException exception) {
            readerFailed.set(true);
            return 0;
        }
    }

    private Path requireWorkingDirectory(Path candidate) throws IOException {
        try {
            return pathGuard.requireExistingDirectory(candidate);
        } catch (IllegalArgumentException exception) {
            throw new IOException("invalid timeline working directory", exception);
        }
    }

    private List<String> requireApprovedCommand(List<String> requestedCommand) throws IOException {
        Path executable;
        try {
            executable = requireVerifiedExecutable(Path.of(requestedCommand.getFirst()));
        } catch (IllegalArgumentException exception) {
            throw new IOException("invalid timeline executable", exception);
        }
        if (!approvedExecutables.contains(executable)) {
            throw new IOException("unapproved timeline executable");
        }
        List<String> command = new ArrayList<>(requestedCommand);
        command.set(0, executable.toString());
        return List.copyOf(command);
    }

    private static Path requireVerifiedExecutable(Path candidate) {
        if (candidate == null || !candidate.isAbsolute()) {
            throw invalidExecutable();
        }
        try {
            BasicFileAttributes attributes = Files.readAttributes(candidate, BasicFileAttributes.class,
                LinkOption.NOFOLLOW_LINKS);
            if (!attributes.isRegularFile() || attributes.isSymbolicLink() || attributes.isOther()
                || Files.isSymbolicLink(candidate) || !Files.isExecutable(candidate)) {
                throw invalidExecutable();
            }
            Path noFollow = candidate.toRealPath(LinkOption.NOFOLLOW_LINKS);
            Path following = candidate.toRealPath();
            if (!noFollow.equals(following)) {
                throw invalidExecutable();
            }
            return noFollow;
        } catch (IOException | SecurityException exception) {
            throw invalidExecutable();
        }
    }

    private static IllegalArgumentException invalidExecutable() {
        return new IllegalArgumentException("timeline executable policy is invalid");
    }

    private static void configureEnvironment(Map<String, String> target, Map<String, String> requested) {
        target.clear();
        for (String key : REQUIRED_SYSTEM_ENVIRONMENT_KEYS) {
            String value = System.getenv(key);
            if (value != null && !value.isBlank()) {
                target.put(key, value);
            }
        }
        target.putAll(requested);
    }

    private static void closeStandardInput(Process process) {
        try {
            process.getOutputStream().close();
        } catch (IOException ignored) {
            // Process termination and bounded stream handling determine the safe outcome.
        }
    }

    private static boolean isCancellationRequested(ActiveProcess active, BooleanSupplier callback) {
        return active.cancellationRequested.get() || callback.getAsBoolean();
    }

    private static long timeoutNanos(Duration timeout) {
        try {
            return timeout.toNanos();
        } catch (ArithmeticException exception) {
            return Long.MAX_VALUE;
        }
    }

    private static TimelineProcessResult result(TimelineProcessResult.Status status, int exitCode, byte[] stdout,
                                                long stderrBytes, long startedNanos) {
        return new TimelineProcessResult(status, exitCode, stdout, stderrBytes,
            Duration.ofNanos(Math.max(0, System.nanoTime() - startedNanos)));
    }

    private static int safeExitCode(Process process) {
        if (process == null || process.isAlive()) {
            return -1;
        }
        try {
            return process.exitValue();
        } catch (IllegalThreadStateException exception) {
            return -1;
        }
    }

    private static void terminateProcessTree(Process process) throws InterruptedException {
        if (process == null) {
            return;
        }
        ProcessHandle root = process.toHandle();
        Set<ProcessHandle> knownProcesses = new LinkedHashSet<>();
        knownProcesses.add(root);
        terminateDescendants(root, knownProcesses, false);
        // A second snapshot narrows the fork window before the parent is asked to exit.
        terminateDescendants(root, knownProcesses, false);
        terminate(root, false);
        awaitTermination(knownProcesses, TERMINATION_GRACE_MILLIS);
        if (hasAliveProcess(knownProcesses)) {
            forceTerminateTree(root, knownProcesses);
            awaitTermination(knownProcesses, FORCED_TERMINATION_WAIT_MILLIS);
        }
    }

    private static void forceTerminateProcessTree(Process process) {
        if (process == null) {
            return;
        }
        ProcessHandle root = process.toHandle();
        Set<ProcessHandle> knownProcesses = new LinkedHashSet<>();
        knownProcesses.add(root);
        forceTerminateTree(root, knownProcesses);
        awaitTerminationUninterruptibly(knownProcesses, FORCED_TERMINATION_WAIT_MILLIS);
    }

    private static void forceTerminateTree(ProcessHandle root, Set<ProcessHandle> knownProcesses) {
        forceKnownDescendants(root, knownProcesses);
        terminateDescendants(root, knownProcesses, true);
        // Capture again after descendants have been signalled, before forcing the root.
        terminateDescendants(root, knownProcesses, true);
        terminate(root, true);
        // A final pass catches descendants that were observable while the root was still alive.
        terminateDescendants(root, knownProcesses, true);
        // A child can outlive its parent between snapshots; every already-recorded child is still forced.
        forceKnownDescendants(root, knownProcesses);
    }

    private static void terminateDescendants(ProcessHandle root, Set<ProcessHandle> knownProcesses,
                                             boolean forcibly) {
        root.descendants().forEach(handle -> {
            knownProcesses.add(handle);
            terminate(handle, forcibly);
        });
    }

    private static void terminate(ProcessHandle handle, boolean forcibly) {
        if (!handle.isAlive()) {
            return;
        }
        if (forcibly) {
            handle.destroyForcibly();
        } else {
            handle.destroy();
        }
    }

    private static void forceKnownDescendants(ProcessHandle root, Set<ProcessHandle> knownProcesses) {
        knownProcesses.stream()
            .filter(handle -> !handle.equals(root))
            .forEach(handle -> terminate(handle, true));
    }

    private static void awaitTermination(Set<ProcessHandle> knownProcesses, long timeoutMillis)
        throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (hasAliveProcess(knownProcesses) && System.nanoTime() < deadline) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) {
                return;
            }
            TimeUnit.NANOSECONDS.sleep(Math.min(TimeUnit.MILLISECONDS.toNanos(POLL_INTERVAL_MILLIS), remaining));
        }
    }

    private static void awaitTerminationUninterruptibly(Set<ProcessHandle> knownProcesses, long timeoutMillis) {
        boolean restoreInterrupt = Thread.interrupted();
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (hasAliveProcess(knownProcesses) && System.nanoTime() < deadline) {
            LockSupport.parkNanos(Math.min(TimeUnit.MILLISECONDS.toNanos(POLL_INTERVAL_MILLIS),
                deadline - System.nanoTime()));
            if (Thread.interrupted()) {
                restoreInterrupt = true;
            }
        }
        if (restoreInterrupt) {
            Thread.currentThread().interrupt();
        }
    }

    private static boolean hasAliveProcess(Set<ProcessHandle> processes) {
        return processes.stream().anyMatch(ProcessHandle::isAlive);
    }

    private record ProcessKey(String executionId, String attemptId) {
    }

    private static final class ActiveProcess {
        private final AtomicBoolean cancellationRequested = new AtomicBoolean();
        private volatile Process process;
    }
}
