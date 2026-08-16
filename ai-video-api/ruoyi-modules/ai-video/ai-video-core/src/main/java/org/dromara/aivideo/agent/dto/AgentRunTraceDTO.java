package org.dromara.aivideo.agent.dto;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Owner-scoped persisted facts related to one AgentRun.
 *
 * <p>This view is deliberately not an event log. It contains only the current durable facts that can be
 * reconstructed from existing run, task, project, evaluation, and approval records.</p>
 */
public record AgentRunTraceDTO(
    long agentRunId,
    String completeness,
    String runStatus,
    long contractRevision,
    long rowVersion,
    List<Fact> facts
) {

    public static final String DURABLE_FACTS = "durable_facts";

    public AgentRunTraceDTO {
        facts = List.copyOf(facts);
    }

    /** Safe, ordered projection of one persisted business fact. */
    public record Fact(
        int sequence,
        String factType,
        long factId,
        String stepCode,
        Long attempt,
        String status,
        String detailCode,
        Integer progressPercent,
        String relatedFactType,
        Long relatedFactId,
        Long resultAssetId,
        String errorCode,
        String safeSummary,
        Instant persistedAt
    ) {

        public Fact {
            if (sequence <= 0 || factId <= 0) {
                throw new IllegalArgumentException("Trace fact identity must be positive");
            }
            Objects.requireNonNull(factType, "factType");
            Objects.requireNonNull(stepCode, "stepCode");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(persistedAt, "persistedAt");
        }
    }
}
