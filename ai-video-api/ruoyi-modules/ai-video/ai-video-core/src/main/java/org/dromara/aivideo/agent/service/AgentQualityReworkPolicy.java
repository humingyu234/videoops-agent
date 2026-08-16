package org.dromara.aivideo.agent.service;

import org.dromara.aivideo.timeline.dto.TimelineOutputQualityDTO;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Selects the smallest bounded rework scope from one complete quality result. */
@Component
public class AgentQualityReworkPolicy {

    private static final int MAX_REPAIR_COUNT = 2;
    private static final Map<String, CriterionRule> RULES = Map.ofEntries(
        rule("media.playable", TimelineOutputQualityDTO.Layer.MEDIA, Scope.RENDER, true),
        rule("media.container_codec", TimelineOutputQualityDTO.Layer.MEDIA, Scope.RENDER, true),
        rule("media.video_dimensions", TimelineOutputQualityDTO.Layer.MEDIA, Scope.RENDER, true),
        rule("media.audio_present", TimelineOutputQualityDTO.Layer.MEDIA, Scope.RENDER, true),
        rule("media.duration", TimelineOutputQualityDTO.Layer.MEDIA, Scope.RENDER, true),
        rule("content.script_integrity", TimelineOutputQualityDTO.Layer.CONTENT_LAYOUT,
            Scope.SCRIPT_DOWNSTREAM, false),
        rule("content.must_include", TimelineOutputQualityDTO.Layer.CONTENT_LAYOUT,
            Scope.SCRIPT_DOWNSTREAM, false),
        rule("content.prohibited", TimelineOutputQualityDTO.Layer.CONTENT_LAYOUT,
            Scope.SCRIPT_DOWNSTREAM, false),
        rule("subtitle.text_integrity", TimelineOutputQualityDTO.Layer.CONTENT_LAYOUT,
            Scope.TIMELINE_RENDER, true),
        rule("subtitle.safe_area", TimelineOutputQualityDTO.Layer.CONTENT_LAYOUT,
            Scope.TIMELINE_RENDER, true),
        rule("subtitle.timing", TimelineOutputQualityDTO.Layer.CONTENT_LAYOUT,
            Scope.TIMELINE_RENDER, true),
        rule("perceptual.identity_similarity", TimelineOutputQualityDTO.Layer.PERCEPTUAL,
            Scope.VIDEO_DOWNSTREAM, false),
        rule("perceptual.lip_sync", TimelineOutputQualityDTO.Layer.PERCEPTUAL,
            Scope.VIDEO_DOWNSTREAM, false),
        rule("perceptual.voice_consistency", TimelineOutputQualityDTO.Layer.PERCEPTUAL,
            Scope.VOICE_DOWNSTREAM, false),
        rule("perceptual.visual_stability", TimelineOutputQualityDTO.Layer.PERCEPTUAL,
            Scope.VIDEO_DOWNSTREAM, false),
        rule("style.tone_match", TimelineOutputQualityDTO.Layer.PERCEPTUAL, Scope.NONE, false)
    );

    /**
     * Decides whether the current candidate can be repaired automatically.
     *
     * @param current current immutable quality result
     * @param previous immediately preceding candidate quality result, required after the first repair
     * @param repairCount number of repair candidates already created for this run
     * @return bounded disposition and the smallest affected dependency scope
     */
    public Decision decide(TimelineOutputQualityDTO current, TimelineOutputQualityDTO previous, int repairCount) {
        Map<String, TimelineOutputQualityDTO.Criterion> currentCriteria = validatedCriteria(current);
        if (repairCount < 0 || currentCriteria == null) {
            return conditional(Scope.NONE, "quality_contract_incomplete");
        }

        List<TimelineOutputQualityDTO.Criterion> lowConfidenceFailures = currentCriteria.values().stream()
            .filter(AgentQualityReworkPolicy::isLowConfidenceFailure)
            .toList();
        if (!lowConfidenceFailures.isEmpty()) {
            return conditional(broadestScope(lowConfidenceFailures), "low_confidence_failure");
        }

        List<TimelineOutputQualityDTO.Criterion> highConfidenceFailures = currentCriteria.values().stream()
            .filter(AgentQualityReworkPolicy::isHighConfidenceFailure)
            .toList();
        if (highConfidenceFailures.isEmpty()) {
            return new Decision(Disposition.FINAL_APPROVAL, Scope.NONE, "quality_ready_for_final_approval");
        }
        if (highConfidenceFailures.stream().anyMatch(criterion -> !RULES.get(criterion.code()).automatic())) {
            return conditional(broadestScope(highConfidenceFailures), "quality_failure_requires_approval");
        }

        Scope repairScope = highConfidenceFailures.stream()
            .anyMatch(criterion -> RULES.get(criterion.code()).scope() == Scope.TIMELINE_RENDER)
            ? Scope.TIMELINE_RENDER : Scope.RENDER;
        if (repairCount >= MAX_REPAIR_COUNT) {
            return conditional(repairScope, "repair_limit_reached");
        }
        if (repairCount > 0 && !hasMeasurableImprovement(current, currentCriteria, previous)) {
            return conditional(repairScope, "no_measurable_improvement");
        }
        return new Decision(Disposition.REPAIR, repairScope, "quality_repair_allowed");
    }

    private boolean hasMeasurableImprovement(TimelineOutputQualityDTO current,
                                             Map<String, TimelineOutputQualityDTO.Criterion> currentCriteria,
                                             TimelineOutputQualityDTO previous) {
        Map<String, TimelineOutputQualityDTO.Criterion> previousCriteria = validatedCriteria(previous);
        if (previousCriteria == null || !Objects.equals(current.ruleSetVersion(), previous.ruleSetVersion())) {
            return false;
        }
        for (Map.Entry<String, TimelineOutputQualityDTO.Criterion> entry : currentCriteria.entrySet()) {
            if (!Objects.equals(entry.getValue().ruleVersion(), previousCriteria.get(entry.getKey()).ruleVersion())) {
                return false;
            }
        }

        boolean improved = previousCriteria.values().stream()
            .filter(AgentQualityReworkPolicy::isHighConfidenceFailure)
            .anyMatch(criterion -> currentCriteria.get(criterion.code()).verdict()
                == TimelineOutputQualityDTO.Verdict.PASS);
        boolean introducedFailure = currentCriteria.values().stream()
            .filter(AgentQualityReworkPolicy::isHighConfidenceFailure)
            .anyMatch(criterion -> !isHighConfidenceFailure(previousCriteria.get(criterion.code())));
        return improved && !introducedFailure;
    }

    private Map<String, TimelineOutputQualityDTO.Criterion> validatedCriteria(TimelineOutputQualityDTO quality) {
        if (quality == null || quality.ruleSetVersion().isBlank() || quality.criteria().size() != RULES.size()) {
            return null;
        }
        Map<String, TimelineOutputQualityDTO.Criterion> criteriaByCode = new LinkedHashMap<>();
        for (TimelineOutputQualityDTO.Criterion criterion : quality.criteria()) {
            CriterionRule expected = RULES.get(criterion.code());
            if (expected == null || criterion.ruleVersion().isBlank() || criterion.layer() != expected.layer()
                || criteriaByCode.putIfAbsent(criterion.code(), criterion) != null) {
                return null;
            }
        }
        return criteriaByCode.size() == RULES.size() ? criteriaByCode : null;
    }

    private Scope broadestScope(List<TimelineOutputQualityDTO.Criterion> criteria) {
        return criteria.stream().map(criterion -> RULES.get(criterion.code()).scope())
            .max((left, right) -> Integer.compare(scopeRank(left), scopeRank(right))).orElse(Scope.NONE);
    }

    private int scopeRank(Scope scope) {
        return switch (scope) {
            case NONE -> 0;
            case RENDER -> 1;
            case TIMELINE_RENDER -> 2;
            case VIDEO_DOWNSTREAM -> 3;
            case VOICE_DOWNSTREAM -> 4;
            case SCRIPT_DOWNSTREAM -> 5;
        };
    }

    private static boolean isHighConfidenceFailure(TimelineOutputQualityDTO.Criterion criterion) {
        return criterion.verdict() == TimelineOutputQualityDTO.Verdict.FAIL
            && criterion.confidence() == TimelineOutputQualityDTO.Confidence.HIGH;
    }

    private static boolean isLowConfidenceFailure(TimelineOutputQualityDTO.Criterion criterion) {
        return criterion.verdict() == TimelineOutputQualityDTO.Verdict.FAIL
            && criterion.confidence() == TimelineOutputQualityDTO.Confidence.LOW;
    }

    private static Decision conditional(Scope scope, String reasonCode) {
        return new Decision(Disposition.CONDITIONAL_APPROVAL, scope, reasonCode);
    }

    private static Map.Entry<String, CriterionRule> rule(String code, TimelineOutputQualityDTO.Layer layer,
                                                         Scope scope, boolean automatic) {
        return Map.entry(code, new CriterionRule(layer, scope, automatic));
    }

    private record CriterionRule(TimelineOutputQualityDTO.Layer layer, Scope scope, boolean automatic) {
    }

    public record Decision(Disposition disposition, Scope scope, String reasonCode) {

        public Decision {
            Objects.requireNonNull(disposition, "disposition");
            Objects.requireNonNull(scope, "scope");
            Objects.requireNonNull(reasonCode, "reasonCode");
        }
    }

    public enum Disposition {
        REPAIR,
        CONDITIONAL_APPROVAL,
        FINAL_APPROVAL
    }

    public enum Scope {
        RENDER,
        TIMELINE_RENDER,
        VIDEO_DOWNSTREAM,
        VOICE_DOWNSTREAM,
        SCRIPT_DOWNSTREAM,
        NONE
    }
}
