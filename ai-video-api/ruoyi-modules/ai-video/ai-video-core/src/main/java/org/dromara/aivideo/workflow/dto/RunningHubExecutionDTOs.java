package org.dromara.aivideo.workflow.dto;

import tools.jackson.databind.JsonNode;

import java.util.List;

/** Controlled contracts between workflow orchestration and the RunningHub client. */
public final class RunningHubExecutionDTOs {

    private RunningHubExecutionDTOs() {
    }

    public record NodeInput(String nodeId, String fieldName, JsonNode value) {
    }

    public record SubmitRequest(String accountId, String executionMode, String remoteId, String instanceType,
                                String accessPasswordCiphertext, List<NodeInput> nodeInfoList) {
    }

    public record Submission(String externalTaskId, String externalStatus) {
    }

    public enum QueryState {
        PENDING,
        SUCCESS,
        FAILED
    }

    public record Output(String url, String outputType, int resultIndex) {
        public Output(String url, String outputType) {
            this(url, outputType, 0);
        }
    }

    public record QueryResult(QueryState state, String externalStatus, String safeError,
                              List<Output> outputs) {
    }

    public record OutputStoragePolicy(long maxBytes, List<String> allowedHosts) {
    }

    public record StoredOutput(String objectKey, String originalName, String contentType,
                               String fileFormat, long sizeBytes, String sha256) {
    }
}
