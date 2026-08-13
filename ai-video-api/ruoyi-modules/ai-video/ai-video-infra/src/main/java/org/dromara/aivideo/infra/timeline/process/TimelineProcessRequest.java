package org.dromara.aivideo.infra.timeline.process;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Immutable, shell-free process input. Callers may only supply approved environment overrides.
 */
public record TimelineProcessRequest(
    String executionId,
    String attemptId,
    List<String> command,
    Path workingDirectory,
    Map<String, String> environment,
    Duration timeout,
    long stdoutLimitBytes,
    long stderrLimitBytes
) {

    private static final int MAX_IDENTIFIER_LENGTH = 128;
    private static final long MAX_STREAM_LIMIT_BYTES = 1024L * 1024L;
    private static final Set<String> ALLOWED_ENVIRONMENT_KEYS = Set.of(
        "FONTCONFIG_FILE", "FONTCONFIG_PATH", "LANG", "LC_ALL", "TZ");

    public TimelineProcessRequest {
        executionId = requireIdentifier(executionId);
        attemptId = requireIdentifier(attemptId);
        command = immutableCommand(command);
        workingDirectory = requireAbsoluteDirectory(workingDirectory);
        environment = immutableEnvironment(environment);
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw invalid();
        }
        if (stdoutLimitBytes <= 0 || stdoutLimitBytes > MAX_STREAM_LIMIT_BYTES
            || stderrLimitBytes <= 0 || stderrLimitBytes > MAX_STREAM_LIMIT_BYTES) {
            throw invalid();
        }
    }

    @Override
    public String toString() {
        return "TimelineProcessRequest[executionId=" + executionId + ", attemptId=" + attemptId
            + ", argumentCount=" + command.size() + ", timeout=" + timeout
            + ", stdoutLimitBytes=" + stdoutLimitBytes + ", stderrLimitBytes=" + stderrLimitBytes + ']';
    }

    private static String requireIdentifier(String value) {
        if (value == null || value.isBlank() || !value.equals(value.trim()) || value.length() > MAX_IDENTIFIER_LENGTH
            || containsNul(value)) {
            throw invalid();
        }
        return value;
    }

    private static List<String> immutableCommand(List<String> values) {
        if (values == null || values.isEmpty()) {
            throw invalid();
        }
        List<String> copy = List.copyOf(values);
        for (String value : copy) {
            if (value == null || value.isBlank() || containsNul(value)) {
                throw invalid();
            }
        }
        try {
            if (!Path.of(copy.getFirst()).isAbsolute()) {
                throw invalid();
            }
        } catch (InvalidPathException exception) {
            throw invalid();
        }
        return copy;
    }

    private static Path requireAbsoluteDirectory(Path value) {
        if (value == null || !value.isAbsolute() || containsTraversal(value)) {
            throw invalid();
        }
        return value.toAbsolutePath().normalize();
    }

    private static Map<String, String> immutableEnvironment(Map<String, String> values) {
        if (values == null) {
            throw invalid();
        }
        Map<String, String> copy = Map.copyOf(values);
        for (Map.Entry<String, String> entry : copy.entrySet()) {
            if (!ALLOWED_ENVIRONMENT_KEYS.contains(entry.getKey()) || entry.getValue() == null
                || containsNul(entry.getValue())) {
                throw invalid();
            }
        }
        return copy;
    }

    private static boolean containsTraversal(Path path) {
        for (Path segment : path) {
            if ("..".equals(segment.toString())) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsNul(String value) {
        return value.indexOf('\0') >= 0;
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("invalid timeline process request");
    }
}
