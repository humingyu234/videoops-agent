package org.dromara.aivideo.timeline.service;

import org.dromara.aivideo.timeline.dto.TimelineDocumentDTO;
import org.dromara.aivideo.timeline.dto.TimelineNormalizationChangeDTO;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;

/** Owner-scoped draft reads and writes with persistent idempotency receipts. */
public interface ITimelineDraftService {

    TimelineDraftView getOwned(long actorId, String projectId);

    TimelineWriteResult save(long actorId, String projectId, SaveTimelineDraftCommand command);

    record SaveTimelineDraftCommand(
        String idempotencyKey,
        String expectedRevision,
        String schemaVersion,
        JsonNode timeline
    ) {
    }

    record TimelineDraftView(
        String projectId,
        String timelineDraftId,
        String revision,
        String schemaVersion,
        String contentHash,
        TimelineDocumentDTO timeline,
        Instant savedAt
    ) {
    }

    record TimelineWriteResult(
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
        public TimelineWriteResult {
            normalizationChanges = normalizationChanges == null ? List.of() : List.copyOf(normalizationChanges);
        }
    }
}
