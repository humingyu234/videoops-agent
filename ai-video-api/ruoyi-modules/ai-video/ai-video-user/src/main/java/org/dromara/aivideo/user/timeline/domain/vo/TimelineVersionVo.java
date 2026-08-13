package org.dromara.aivideo.user.timeline.domain.vo;

import org.dromara.aivideo.timeline.service.ITimelineVersionService;

import java.time.Instant;

public record TimelineVersionVo(
    String versionId,
    String projectId,
    String versionNo,
    String sourceDraftRevision,
    String schemaVersion,
    String contentHash,
    String versionReason,
    String sourceVersionId,
    Instant createdAt,
    boolean replayed
) {

    public static TimelineVersionVo from(ITimelineVersionService.TimelineVersionView view) {
        return new TimelineVersionVo(view.versionId(), view.projectId(), view.versionNo(), view.sourceDraftRevision(),
            view.schemaVersion(), view.contentHash(), view.versionReason(), view.sourceVersionId(),
            view.createdAt(), view.replayed());
    }
}
