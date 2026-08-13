package org.dromara.aivideo.user.timeline.domain.vo;

import org.dromara.aivideo.timeline.dto.TimelineDocumentDTO;
import org.dromara.aivideo.timeline.service.ITimelineDraftService;

import java.time.Instant;

public record TimelineDraftVo(
    String projectId,
    String timelineDraftId,
    String revision,
    String schemaVersion,
    String contentHash,
    TimelineDocumentDTO timeline,
    Instant savedAt
) {

    public static TimelineDraftVo from(ITimelineDraftService.TimelineDraftView view) {
        return new TimelineDraftVo(view.projectId(), view.timelineDraftId(), view.revision(), view.schemaVersion(),
            view.contentHash(), view.timeline(), view.savedAt());
    }
}
