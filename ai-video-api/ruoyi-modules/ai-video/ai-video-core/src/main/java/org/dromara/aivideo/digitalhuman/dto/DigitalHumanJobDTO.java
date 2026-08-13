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
    String errorMessage
) {
}
