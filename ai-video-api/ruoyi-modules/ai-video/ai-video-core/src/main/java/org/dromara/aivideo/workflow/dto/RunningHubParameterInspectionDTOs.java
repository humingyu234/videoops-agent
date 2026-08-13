package org.dromara.aivideo.workflow.dto;

import java.util.List;

/** RunningHub 参数候选检查的跨模块受控数据契约。 */
public final class RunningHubParameterInspectionDTOs {

    private RunningHubParameterInspectionDTOs() {
    }

    public record Request(String accountId, String executionMode, String workflowId, String webAppId) {
    }

    public record Result(String webAppName, List<Candidate> candidates) {
        public Result {
            candidates = candidates == null ? List.of() : List.copyOf(candidates);
        }
    }

    public record Candidate(String nodeId, String nodeName, String fieldName, String fieldType,
                            String description, String defaultValue, List<Option> options) {
        public Candidate {
            options = options == null ? List.of() : List.copyOf(options);
        }
    }

    public record Option(String value, String label) {
    }
}
