package org.dromara.aivideo.user.digitalhuman.domain.vo;

import org.dromara.aivideo.digitalhuman.dto.DigitalHumanJobDTO;

public record DigitalHumanJobVo(
    String jobId,
    String parentJobId,
    String jobType,
    String status,
    String stage,
    Integer progress,
    boolean voiceConfirmed,
    boolean outputAvailable,
    String errorMessage
) {
    public static DigitalHumanJobVo from(DigitalHumanJobDTO value) {
        return new DigitalHumanJobVo(String.valueOf(value.jobId()),
            value.parentJobId() == null ? null : String.valueOf(value.parentJobId()), value.jobType().getValue(),
            value.status().getValue(), value.stage().getValue(), value.progress(), value.voiceConfirmed(),
            value.outputAvailable(), value.errorMessage());
    }
}
