package org.dromara.aivideo.infra.timeline.process;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Bounded process outcome. Standard error is intentionally represented only by its byte count.
 */
public record TimelineProcessResult(
    Status status,
    int exitCode,
    byte[] stdout,
    long stderrBytes,
    Duration elapsed
) {

    public TimelineProcessResult {
        if (status == null || stdout == null || stderrBytes < 0 || elapsed == null || elapsed.isNegative()) {
            throw new IllegalArgumentException("invalid timeline process result");
        }
        stdout = stdout.clone();
    }

    @Override
    public byte[] stdout() {
        return stdout.clone();
    }

    /**
     * Decodes only the bounded standard output that the caller explicitly requested.
     */
    public String stdoutUtf8() {
        return new String(stdout, StandardCharsets.UTF_8);
    }

    public boolean succeeded() {
        return status == Status.SUCCEEDED;
    }

    @Override
    public String toString() {
        return "TimelineProcessResult[status=" + status + ", exitCode=" + exitCode
            + ", stdoutBytes=" + stdout.length + ", stderrBytes=" + stderrBytes + ", elapsed=" + elapsed + ']';
    }

    public enum Status {
        SUCCEEDED,
        NON_ZERO_EXIT,
        TIMED_OUT,
        CANCELLED,
        OUTPUT_LIMIT_EXCEEDED,
        START_FAILED,
        PROCESS_FAILED
    }
}
