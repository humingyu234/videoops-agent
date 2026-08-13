package org.dromara.aivideo.user.timeline.domain.vo;

import org.dromara.aivideo.timeline.dto.TimelineDocumentDTO;
import org.dromara.aivideo.timeline.dto.TimelineNormalizationChangeDTO;
import org.dromara.aivideo.timeline.service.ITimelineDraftService;

import java.time.Instant;
import java.util.List;

public record TimelineWriteResultVo(
    String projectId,
    String timelineDraftId,
    String revision,
    String schemaVersion,
    String contentHash,
    TimelineDocumentDTO timeline,
    Instant savedAt,
    boolean replayed,
    boolean superseded,
    String operationResultRevision,
    String operationContentHash,
    String currentRevision,
    List<TimelineNormalizationChangeDTO> normalizationChanges
) {

    public static TimelineWriteResultVo from(ITimelineDraftService.TimelineWriteResult result) {
        return new TimelineWriteResultVo(result.projectId(), result.timelineDraftId(), result.revision(),
            result.schemaVersion(), result.contentHash(), result.timeline(), result.savedAt(), result.replayed(),
            result.superseded(), result.operationResultRevision(), result.operationContentHash(),
            result.currentRevision(), result.normalizationChanges());
    }
}
