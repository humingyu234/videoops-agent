package org.dromara.aivideo.agent.dto;

import tools.jackson.databind.JsonNode;

/**
 * Agent 可调用的黄金链工具契约。
 */
public final class AgentToolDTOs {

    private AgentToolDTOs() {
    }

    /** 通用入口只承载工具名与待严格解析的参数对象。 */
    public record Call(String toolName, JsonNode arguments) {
    }

    public record SubmitVoiceArgs(String idempotencyKey, String scriptText, String referenceVoiceId) {
    }

    public record JobArgs(String jobId) {
    }

    public record SubmitVideoArgs(String idempotencyKey, String voiceJobId, String portraitId) {
    }

    public record PrepareProjectArgs(String idempotencyKey, String videoJobId, String projectTitle) {
    }

    public record RenderTimelineArgs(String idempotencyKey, String projectId, String expectedRevision) {
    }

    public record TaskArgs(String taskId) {
    }

    /** Marker for the closed set of structured tool results. */
    public interface Result {
    }

    public record GenerationJobResult(
        String jobId,
        String parentJobId,
        String jobType,
        String status,
        String stage,
        Integer progress,
        boolean voiceConfirmed,
        boolean outputAvailable
    ) implements Result {
    }

    public record ProjectResult(
        String projectId,
        String projectStatus,
        String currentDraftRevision,
        int canvasWidth,
        int canvasHeight,
        int frameRate,
        long durationMs
    ) implements Result {
    }

    public record RenderTaskResult(
        String taskId,
        String status,
        String stage,
        String projectId,
        String draftRevision
    ) implements Result {
    }

    public record RenderStatusResult(
        String taskId,
        String status,
        String stage,
        int progress,
        String projectId,
        String draftRevision,
        String resultAssetId,
        boolean cancellable,
        boolean retryable
    ) implements Result {
    }

    public record OutputInspectionResult(
        String taskId,
        String assetId,
        String status,
        String assetType,
        String usageOrigin,
        String mimeType,
        String sha256,
        long sizeBytes,
        Long durationMs,
        Integer width,
        Integer height,
        boolean hasVideoStream,
        boolean hasAudioStream,
        String downloadPath
    ) implements Result {
    }
}
