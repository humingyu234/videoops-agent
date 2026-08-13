package org.dromara.aivideo.workflow.order.dto;

import tools.jackson.databind.JsonNode;

import java.util.Map;

public record CreateWorkflowOrderDTO(String templateId, String schemaHash, String idempotencyKey,
                                     Map<String, JsonNode> inputs) {
}
