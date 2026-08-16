package org.dromara.aivideo.timeline.dto;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Ordered, evidence-led quality facts for one immutable timeline render output. */
public record TimelineOutputQualityDTO(
    String taskId,
    String assetId,
    String artifactSha256,
    String inputVersionId,
    String timelineContentHash,
    String ruleSetVersion,
    List<Criterion> criteria
) {

    public TimelineOutputQualityDTO {
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(assetId, "assetId");
        Objects.requireNonNull(artifactSha256, "artifactSha256");
        Objects.requireNonNull(inputVersionId, "inputVersionId");
        Objects.requireNonNull(timelineContentHash, "timelineContentHash");
        Objects.requireNonNull(ruleSetVersion, "ruleSetVersion");
        criteria = criteria == null ? List.of() : List.copyOf(criteria);
    }

    public record Criterion(
        String code,
        Layer layer,
        String ruleVersion,
        Verdict verdict,
        Confidence confidence,
        Map<String, Object> evidence
    ) {

        public Criterion {
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(layer, "layer");
            Objects.requireNonNull(ruleVersion, "ruleVersion");
            Objects.requireNonNull(verdict, "verdict");
            Objects.requireNonNull(confidence, "confidence");
            evidence = evidence == null ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(evidence));
        }
    }

    public enum Layer {
        MEDIA("media"),
        CONTENT_LAYOUT("content_layout"),
        PERCEPTUAL("perceptual");

        private final String value;

        Layer(String value) {
            this.value = value;
        }

        @JsonValue
        public String value() {
            return value;
        }
    }

    public enum Verdict {
        PASS,
        FAIL,
        REVIEW
    }

    public enum Confidence {
        HIGH,
        LOW
    }
}
