package org.dromara.aivideo.agent.service;

import org.dromara.aivideo.timeline.dto.TimelineOutputQualityDTO;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("dev")
class AgentQualityReworkPolicyTest {

    private static final AgentQualityReworkPolicy POLICY = new AgentQualityReworkPolicy();
    private static final String RULE_SET_VERSION = "t5-quality-1";
    private static final List<CriterionSeed> CONTRACT = List.of(
        seed("media.playable", TimelineOutputQualityDTO.Layer.MEDIA, "media-ffmpeg-v1"),
        seed("media.container_codec", TimelineOutputQualityDTO.Layer.MEDIA, "media-ffmpeg-v1"),
        seed("media.video_dimensions", TimelineOutputQualityDTO.Layer.MEDIA, "media-ffmpeg-v1"),
        seed("media.audio_present", TimelineOutputQualityDTO.Layer.MEDIA, "media-ffmpeg-v1"),
        seed("media.duration", TimelineOutputQualityDTO.Layer.MEDIA, "media-ffmpeg-v1"),
        seed("content.script_integrity", TimelineOutputQualityDTO.Layer.CONTENT_LAYOUT,
            "content-frozen-script-v1"),
        seed("content.must_include", TimelineOutputQualityDTO.Layer.CONTENT_LAYOUT,
            "content-frozen-script-v1"),
        seed("content.prohibited", TimelineOutputQualityDTO.Layer.CONTENT_LAYOUT,
            "content-frozen-script-v1"),
        seed("subtitle.text_integrity", TimelineOutputQualityDTO.Layer.CONTENT_LAYOUT,
            "subtitle-frozen-timeline-v1"),
        seed("subtitle.safe_area", TimelineOutputQualityDTO.Layer.CONTENT_LAYOUT,
            "subtitle-frozen-timeline-v1"),
        seed("subtitle.timing", TimelineOutputQualityDTO.Layer.CONTENT_LAYOUT,
            "subtitle-frozen-timeline-v1"),
        seed("perceptual.identity_similarity", TimelineOutputQualityDTO.Layer.PERCEPTUAL, "human-review-v1"),
        seed("perceptual.lip_sync", TimelineOutputQualityDTO.Layer.PERCEPTUAL, "human-review-v1"),
        seed("perceptual.voice_consistency", TimelineOutputQualityDTO.Layer.PERCEPTUAL, "human-review-v1"),
        seed("perceptual.visual_stability", TimelineOutputQualityDTO.Layer.PERCEPTUAL, "human-review-v1"),
        seed("style.tone_match", TimelineOutputQualityDTO.Layer.PERCEPTUAL, "human-review-v1")
    );

    @ParameterizedTest(name = "{0}")
    @MethodSource("firstCandidateCases")
    void selectsOnlyTheAllowedFirstCandidateScope(String name, List<String> failures,
                                                   TimelineOutputQualityDTO.Confidence confidence,
                                                   AgentQualityReworkPolicy.Disposition disposition,
                                                   AgentQualityReworkPolicy.Scope scope) {
        AgentQualityReworkPolicy.Decision decision = POLICY.decide(quality(failures, confidence), null, 0);

        assertThat(decision.disposition()).isEqualTo(disposition);
        assertThat(decision.scope()).isEqualTo(scope);
    }

    private static Stream<Arguments> firstCandidateCases() {
        return Stream.of(
            Arguments.of("media high fail", List.of("media.playable"),
                TimelineOutputQualityDTO.Confidence.HIGH, AgentQualityReworkPolicy.Disposition.REPAIR,
                AgentQualityReworkPolicy.Scope.RENDER),
            Arguments.of("subtitle high fail", List.of("subtitle.timing"),
                TimelineOutputQualityDTO.Confidence.HIGH, AgentQualityReworkPolicy.Disposition.REPAIR,
                AgentQualityReworkPolicy.Scope.TIMELINE_RENDER),
            Arguments.of("subtitle and media high fail", List.of("media.duration", "subtitle.safe_area"),
                TimelineOutputQualityDTO.Confidence.HIGH, AgentQualityReworkPolicy.Disposition.REPAIR,
                AgentQualityReworkPolicy.Scope.TIMELINE_RENDER),
            Arguments.of("content high fail", List.of("content.script_integrity"),
                TimelineOutputQualityDTO.Confidence.HIGH,
                AgentQualityReworkPolicy.Disposition.CONDITIONAL_APPROVAL,
                AgentQualityReworkPolicy.Scope.SCRIPT_DOWNSTREAM),
            Arguments.of("perceptual voice high fail", List.of("perceptual.voice_consistency"),
                TimelineOutputQualityDTO.Confidence.HIGH,
                AgentQualityReworkPolicy.Disposition.CONDITIONAL_APPROVAL,
                AgentQualityReworkPolicy.Scope.VOICE_DOWNSTREAM),
            Arguments.of("perceptual video high fail", List.of("perceptual.lip_sync"),
                TimelineOutputQualityDTO.Confidence.HIGH,
                AgentQualityReworkPolicy.Disposition.CONDITIONAL_APPROVAL,
                AgentQualityReworkPolicy.Scope.VIDEO_DOWNSTREAM),
            Arguments.of("subjective style high fail", List.of("style.tone_match"),
                TimelineOutputQualityDTO.Confidence.HIGH,
                AgentQualityReworkPolicy.Disposition.CONDITIONAL_APPROVAL,
                AgentQualityReworkPolicy.Scope.NONE),
            Arguments.of("low confidence fail", List.of("media.playable"),
                TimelineOutputQualityDTO.Confidence.LOW,
                AgentQualityReworkPolicy.Disposition.CONDITIONAL_APPROVAL,
                AgentQualityReworkPolicy.Scope.RENDER)
        );
    }

    @Test
    void sendsACompleteResultWithoutHighFailuresToFinalApprovalEvenWithLowReviews() {
        TimelineOutputQualityDTO current = quality(List.of(), TimelineOutputQualityDTO.Confidence.HIGH);
        current = replace(current, criterion("perceptual.lip_sync", TimelineOutputQualityDTO.Verdict.REVIEW,
            TimelineOutputQualityDTO.Confidence.LOW));

        AgentQualityReworkPolicy.Decision decision = POLICY.decide(current, null, 0);

        assertThat(decision.disposition()).isEqualTo(AgentQualityReworkPolicy.Disposition.FINAL_APPROVAL);
        assertThat(decision.scope()).isEqualTo(AgentQualityReworkPolicy.Scope.NONE);
    }

    @Test
    void enforcesTheRepairLimitBelowAtAndAboveTwo() {
        TimelineOutputQualityDTO first = quality(List.of("media.playable", "media.duration"),
            TimelineOutputQualityDTO.Confidence.HIGH);
        TimelineOutputQualityDTO improved = quality(List.of("media.duration"),
            TimelineOutputQualityDTO.Confidence.HIGH);

        assertThat(POLICY.decide(first, null, 0).disposition())
            .isEqualTo(AgentQualityReworkPolicy.Disposition.REPAIR);
        assertThat(POLICY.decide(improved, first, 1).disposition())
            .isEqualTo(AgentQualityReworkPolicy.Disposition.REPAIR);
        assertThat(POLICY.decide(improved, first, 2).disposition())
            .isEqualTo(AgentQualityReworkPolicy.Disposition.CONDITIONAL_APPROVAL);
        assertThat(POLICY.decide(improved, first, 3).disposition())
            .isEqualTo(AgentQualityReworkPolicy.Disposition.CONDITIONAL_APPROVAL);
    }

    @Test
    void requiresSameRulesOneFailToPassAndNoNewHighFailureAfterFirstRepair() {
        TimelineOutputQualityDTO previous = quality(List.of("media.playable", "subtitle.timing"),
            TimelineOutputQualityDTO.Confidence.HIGH);
        TimelineOutputQualityDTO improved = quality(List.of("subtitle.timing"),
            TimelineOutputQualityDTO.Confidence.HIGH);
        assertThat(POLICY.decide(improved, previous, 1).disposition())
            .isEqualTo(AgentQualityReworkPolicy.Disposition.REPAIR);

        assertConditional(quality(List.of("media.playable", "subtitle.timing"),
            TimelineOutputQualityDTO.Confidence.HIGH), previous);
        assertConditional(quality(List.of("subtitle.timing", "media.duration"),
            TimelineOutputQualityDTO.Confidence.HIGH), previous);

        TimelineOutputQualityDTO changedRuleSet = withRuleSet(improved, "t5-quality-2");
        assertConditional(changedRuleSet, previous);
        TimelineOutputQualityDTO changedRule = replace(improved,
            new TimelineOutputQualityDTO.Criterion("media.playable", TimelineOutputQualityDTO.Layer.MEDIA,
                "media-ffmpeg-v2", TimelineOutputQualityDTO.Verdict.PASS,
                TimelineOutputQualityDTO.Confidence.HIGH, Map.of()));
        assertConditional(changedRule, previous);
    }

    @Test
    void doesNotTreatArtifactShaOrEvidenceChangesAsMeasurableImprovement() {
        TimelineOutputQualityDTO previous = quality(List.of("media.playable"),
            TimelineOutputQualityDTO.Confidence.HIGH);
        TimelineOutputQualityDTO current = replace(previous,
            new TimelineOutputQualityDTO.Criterion("media.playable", TimelineOutputQualityDTO.Layer.MEDIA,
                "media-ffmpeg-v1", TimelineOutputQualityDTO.Verdict.FAIL,
                TimelineOutputQualityDTO.Confidence.HIGH, Map.of("fullyDecoded", false, "attempt", 2)));
        current = new TimelineOutputQualityDTO(current.taskId(), current.assetId(), "c".repeat(64),
            current.inputVersionId(), current.timelineContentHash(), current.ruleSetVersion(), current.criteria());

        assertConditional(current, previous);
    }

    @Test
    void failsClosedForUnknownMissingDuplicateWrongLayerAndNegativeCount() {
        TimelineOutputQualityDTO valid = quality(List.of("media.playable"),
            TimelineOutputQualityDTO.Confidence.HIGH);
        List<TimelineOutputQualityDTO> invalid = new ArrayList<>();
        List<TimelineOutputQualityDTO.Criterion> unknown = new ArrayList<>(valid.criteria());
        unknown.set(0, new TimelineOutputQualityDTO.Criterion("quality.unknown",
            TimelineOutputQualityDTO.Layer.MEDIA, "rule-v1", TimelineOutputQualityDTO.Verdict.FAIL,
            TimelineOutputQualityDTO.Confidence.HIGH, Map.of()));
        invalid.add(copy(valid, unknown));
        invalid.add(new TimelineOutputQualityDTO(valid.taskId(), valid.assetId(), valid.artifactSha256(),
            valid.inputVersionId(), valid.timelineContentHash(), valid.ruleSetVersion(),
            valid.criteria().subList(0, 15)));
        List<TimelineOutputQualityDTO.Criterion> duplicate = new ArrayList<>(valid.criteria());
        duplicate.set(15, duplicate.get(0));
        invalid.add(copy(valid, duplicate));
        invalid.add(replace(valid, new TimelineOutputQualityDTO.Criterion("media.playable",
            TimelineOutputQualityDTO.Layer.PERCEPTUAL, "media-ffmpeg-v1", TimelineOutputQualityDTO.Verdict.FAIL,
            TimelineOutputQualityDTO.Confidence.HIGH, Map.of())));

        assertThat(invalid).allSatisfy(result -> assertThat(POLICY.decide(result, null, 0).disposition())
            .isEqualTo(AgentQualityReworkPolicy.Disposition.CONDITIONAL_APPROVAL));
        assertThat(POLICY.decide(valid, null, -1).disposition())
            .isEqualTo(AgentQualityReworkPolicy.Disposition.CONDITIONAL_APPROVAL);
    }

    private static void assertConditional(TimelineOutputQualityDTO current, TimelineOutputQualityDTO previous) {
        assertThat(POLICY.decide(current, previous, 1).disposition())
            .isEqualTo(AgentQualityReworkPolicy.Disposition.CONDITIONAL_APPROVAL);
    }

    private static TimelineOutputQualityDTO quality(List<String> failures,
                                                    TimelineOutputQualityDTO.Confidence confidence) {
        List<TimelineOutputQualityDTO.Criterion> criteria = CONTRACT.stream()
            .map(seed -> new TimelineOutputQualityDTO.Criterion(seed.code(), seed.layer(), seed.ruleVersion(),
                failures.contains(seed.code()) ? TimelineOutputQualityDTO.Verdict.FAIL
                    : seed.code().startsWith("perceptual.") || seed.code().startsWith("style.")
                    ? TimelineOutputQualityDTO.Verdict.REVIEW : TimelineOutputQualityDTO.Verdict.PASS,
                failures.contains(seed.code()) ? confidence
                    : seed.code().startsWith("perceptual.") || seed.code().startsWith("style.")
                    ? TimelineOutputQualityDTO.Confidence.LOW : TimelineOutputQualityDTO.Confidence.HIGH,
                Map.of("source", "fixture")))
            .toList();
        return new TimelineOutputQualityDTO("201", "401", "b".repeat(64), "301", "a".repeat(64),
            RULE_SET_VERSION, criteria);
    }

    private static TimelineOutputQualityDTO.Criterion criterion(String code,
                                                                 TimelineOutputQualityDTO.Verdict verdict,
                                                                 TimelineOutputQualityDTO.Confidence confidence) {
        CriterionSeed seed = CONTRACT.stream().filter(item -> item.code().equals(code)).findFirst().orElseThrow();
        return new TimelineOutputQualityDTO.Criterion(code, seed.layer(), seed.ruleVersion(), verdict, confidence,
            Map.of("source", "fixture"));
    }

    private static TimelineOutputQualityDTO replace(TimelineOutputQualityDTO quality,
                                                     TimelineOutputQualityDTO.Criterion replacement) {
        return copy(quality, quality.criteria().stream()
            .map(criterion -> criterion.code().equals(replacement.code()) ? replacement : criterion).toList());
    }

    private static TimelineOutputQualityDTO copy(TimelineOutputQualityDTO quality,
                                                  List<TimelineOutputQualityDTO.Criterion> criteria) {
        return new TimelineOutputQualityDTO(quality.taskId(), quality.assetId(), quality.artifactSha256(),
            quality.inputVersionId(), quality.timelineContentHash(), quality.ruleSetVersion(), criteria);
    }

    private static TimelineOutputQualityDTO withRuleSet(TimelineOutputQualityDTO quality, String ruleSetVersion) {
        return new TimelineOutputQualityDTO(quality.taskId(), quality.assetId(), quality.artifactSha256(),
            quality.inputVersionId(), quality.timelineContentHash(), ruleSetVersion, quality.criteria());
    }

    private static CriterionSeed seed(String code, TimelineOutputQualityDTO.Layer layer, String ruleVersion) {
        return new CriterionSeed(code, layer, ruleVersion);
    }

    private record CriterionSeed(String code, TimelineOutputQualityDTO.Layer layer, String ruleVersion) {
    }
}
