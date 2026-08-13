package org.dromara.aivideo.timeline.exception;

import org.dromara.aivideo.timeline.enums.TimelineExecutionFailureCode;

import java.util.Objects;

public final class TimelineExecutionException extends RuntimeException {
    private final TimelineExecutionFailureCode code;
    private final boolean retryable;

    public TimelineExecutionException(String safeMessage,
                                      TimelineExecutionFailureCode code,
                                      boolean retryable,
                                      Throwable cause) {
        super(safeMessage, cause);
        if (safeMessage == null || safeMessage.isBlank()
            || safeMessage.codePointCount(0, safeMessage.length()) > 512) {
            throw new IllegalArgumentException("safeMessage must contain 1..512 code points");
        }
        this.code = Objects.requireNonNull(code, "code");
        this.retryable = retryable;
    }

    public TimelineExecutionFailureCode code() {
        return code;
    }

    public boolean retryable() {
        return retryable;
    }
}
