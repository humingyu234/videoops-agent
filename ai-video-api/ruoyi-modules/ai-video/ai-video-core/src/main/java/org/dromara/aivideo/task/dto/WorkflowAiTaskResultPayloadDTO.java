package org.dromara.aivideo.task.dto;

import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

/** Stable internal workflow result facts; raw provider responses and URLs are never retained here. */
public record WorkflowAiTaskResultPayloadDTO(List<String> resultAssetIds, Map<String, JsonNode> outputFacts)
    implements AiTaskResultPayloadDTO {
    public WorkflowAiTaskResultPayloadDTO {
        if (resultAssetIds == null
            || resultAssetIds.stream().anyMatch(id -> !WorkflowAiTaskPayloadDTO.positiveId(id))) {
            throw new IllegalArgumentException("invalid workflow result assets");
        }
        resultAssetIds = List.copyOf(resultAssetIds);
        outputFacts = WorkflowAiTaskPayloadDTO.validateFacts(outputFacts);
    }
}
