package org.dromara.aivideo.creation.service;

import org.dromara.aivideo.creation.dto.CreationOutputDTO;

import java.time.Instant;

/** Creation-project lifecycle entry point for the app-facing timeline flow. */
public interface ICreationProjectService {

    CreationProjectDTO create(long actorId, CreateProjectCommand command);

    CreationProjectDTO getOwned(long actorId, String projectId);

    CreationProjectDTO updateTitleOwned(long actorId, String projectId, UpdateProjectTitleCommand command);

    CreationOutputDTO getLatestOutputOwned(long actorId, String projectId);

    /** Only source selection and replay metadata are accepted from the request boundary. */
    record CreateProjectCommand(
        String sourceType,
        String sourceId,
        String projectTitle,
        String idempotencyKey
    ) {
    }

    /** Only the user-facing title can be changed after project creation. */
    record UpdateProjectTitleCommand(String projectTitle) {
    }

    /** Owner-safe project state used by the app controller. */
    record CreationProjectDTO(
        String projectId,
        String projectTitle,
        String sourceType,
        String sourceId,
        String baseVideoAssetId,
        String primaryAudioAssetId,
        String projectStatus,
        int canvasWidth,
        int canvasHeight,
        int frameRate,
        long durationMs,
        long currentDraftRevision,
        String schemaVersion,
        String latestOutputAssetId,
        Instant createdAt,
        Instant updatedAt
    ) {
    }
}
