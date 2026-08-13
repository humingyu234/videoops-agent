package org.dromara.aivideo.timeline.service;

import org.dromara.common.core.domain.PageResult;
import org.dromara.common.mybatis.core.page.PageQuery;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;

/** Owner-scoped immutable timeline versions, conflict copies, and restore operations. */
public interface ITimelineVersionService {

    TimelineVersionView createManualVersion(long actorId, String projectId, CreateManualVersionCommand command);

    TimelineVersionView createConflictCopy(long actorId, String projectId, CreateConflictCopyCommand command);

    ITimelineDraftService.TimelineWriteResult restoreVersion(long actorId, String projectId, String versionId,
                                                             RestoreTimelineVersionCommand command);

    List<TimelineVersionView> listOwnedVersions(long actorId, String projectId);

    PageResult<TimelineVersionView> pageOwnedVersions(long actorId, String projectId, PageQuery pageQuery);

    record CreateManualVersionCommand(String idempotencyKey, String expectedRevision) {
    }

    record CreateConflictCopyCommand(String idempotencyKey, String baseRevision, String schemaVersion,
                                     JsonNode timeline) {
    }

    record RestoreTimelineVersionCommand(String idempotencyKey, String expectedRevision) {
    }

    record TimelineVersionView(
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
    }
}
