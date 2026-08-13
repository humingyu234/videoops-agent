package org.dromara.aivideo.timeline.service;

import org.dromara.aivideo.timeline.dto.TimelineAssetReferenceDTO;
import org.dromara.aivideo.timeline.dto.TimelineDocumentDTO;
import org.dromara.aivideo.timeline.dto.TimelineNormalizationChangeDTO;
import tools.jackson.databind.JsonNode;

import java.util.List;

/** Strict timeline-1 parsing, semantic validation, and server-side normalization. */
public interface ITimelineDocumentService {

    ValidatedTimeline validate(long actorId, ProjectContext project, JsonNode rawTimeline);

    record ProjectContext(
        String projectId,
        String baseVideoAssetId,
        String primaryAudioAssetId,
        String scriptTextSnapshot,
        long durationMs,
        int width,
        int height,
        int frameRate
    ) {
    }

    record ValidatedTimeline(
        TimelineDocumentDTO timeline,
        String canonicalJson,
        String contentHash,
        List<TimelineAssetReferenceDTO> assets,
        List<TimelineNormalizationChangeDTO> normalizationChanges
    ) {
        public ValidatedTimeline {
            assets = assets == null ? List.of() : List.copyOf(assets);
            normalizationChanges = normalizationChanges == null ? List.of() : List.copyOf(normalizationChanges);
        }
    }
}
