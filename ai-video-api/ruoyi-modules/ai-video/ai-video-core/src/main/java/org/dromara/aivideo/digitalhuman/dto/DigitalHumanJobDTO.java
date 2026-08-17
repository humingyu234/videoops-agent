package org.dromara.aivideo.digitalhuman.dto;

import org.dromara.aivideo.digitalhuman.domain.DigitalHumanJobStage;
import org.dromara.aivideo.digitalhuman.domain.DigitalHumanJobStatus;
import org.dromara.aivideo.digitalhuman.domain.DigitalHumanJobType;

public record DigitalHumanJobDTO(
    Long jobId,
    Long parentJobId,
    DigitalHumanJobType jobType,
    DigitalHumanJobStatus status,
    DigitalHumanJobStage stage,
    Integer progress,
    boolean voiceConfirmed,
    boolean outputAvailable,
    String errorCode,
    String errorMessage,
    String inputHash
) {
    public DigitalHumanJobDTO(Long jobId, Long parentJobId, DigitalHumanJobType jobType,
                              DigitalHumanJobStatus status, DigitalHumanJobStage stage,
                              Integer progress, boolean voiceConfirmed, boolean outputAvailable,
                              String errorCode, String errorMessage) {
        this(jobId, parentJobId, jobType, status, stage, progress, voiceConfirmed, outputAvailable,
            errorCode, errorMessage, null);
    }

    public DigitalHumanJobDTO(Long jobId, Long parentJobId, DigitalHumanJobType jobType,
                              DigitalHumanJobStatus status, DigitalHumanJobStage stage,
                              Integer progress, boolean voiceConfirmed, boolean outputAvailable,
                              String errorMessage) {
        this(jobId, parentJobId, jobType, status, stage, progress, voiceConfirmed, outputAvailable,
            null, errorMessage, null);
    }
}
