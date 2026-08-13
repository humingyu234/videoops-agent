package org.dromara.aivideo.user.creation.domain.vo;

import org.dromara.aivideo.creation.service.ICreationProjectService;

import java.time.Instant;

public record CreationProjectVo(
    String projectId,
    String projectTitle,
    String sourceType,
    String sourceId,
    String baseVideoAssetId,
    String primaryAudioAssetId,
    String status,
    CanvasVo canvas,
    String currentDraftRevision,
    String schemaVersion,
    String latestOutputAssetId,
    Instant createdAt,
    Instant updatedAt
) {

    public static CreationProjectVo from(ICreationProjectService.CreationProjectDTO dto) {
        return new CreationProjectVo(dto.projectId(), dto.projectTitle(), dto.sourceType(), dto.sourceId(),
            dto.baseVideoAssetId(), dto.primaryAudioAssetId(), dto.projectStatus(),
            new CanvasVo(dto.canvasWidth(), dto.canvasHeight(), dto.frameRate(), dto.durationMs()),
            Long.toString(dto.currentDraftRevision()), dto.schemaVersion(), dto.latestOutputAssetId(),
            dto.createdAt(), dto.updatedAt());
    }

    public record CanvasVo(int width, int height, int frameRate, long durationMs) {
    }
}
