package org.dromara.aivideo.infra.timeline.render;

import org.dromara.aivideo.creation.dto.CreationAssetResolveDTO;
import org.dromara.aivideo.creation.enums.CreationAssetType;
import org.dromara.aivideo.timeline.dto.TimelineAssetReferenceDTO;
import org.dromara.aivideo.timeline.dto.TimelineDocumentDTO;
import org.dromara.aivideo.timeline.dto.TimelineFancyTextElementDTO;
import org.dromara.aivideo.timeline.dto.TimelineRenderCommandDTO;
import org.dromara.aivideo.timeline.dto.TimelineTrackDTO;
import org.dromara.aivideo.timeline.dto.TimelineVisualEffectElementDTO;
import org.dromara.aivideo.timeline.enums.TimelineAssetUsageType;
import org.dromara.aivideo.timeline.enums.TimelineExecutionFailureCode;
import org.dromara.aivideo.timeline.enums.TimelineOutputQuality;
import org.dromara.aivideo.timeline.enums.TimelineVisualEffectCode;
import org.dromara.aivideo.timeline.exception.TimelineExecutionException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("dev")
class TimelineRenderPlanBuilderTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String SHA = "a".repeat(64);

    private final TimelineRenderPlanBuilder builder = new TimelineRenderPlanBuilder();

    @Test
    void buildsAWhitelistedPlanForEveryFrozenMediaCategory() throws IOException {
        Fixture fixture = fixture();

        TimelineRenderPlan plan = builder.build(fixture.command(), fixture.resolvedInputs());

        assertThat(plan.inputs()).extracting(TimelineRenderPlan.Input::alias).containsExactly(
            "input-0001.mp4", "input-0002.png", "input-0003.mp4",
            "input-0004.wav", "input-0005.wav", "input-0006.wav"
        );
        assertThat(plan.inputs()).extracting(TimelineRenderPlan.Input::loopInput).containsExactly(
            false, false, true, false, true, false
        );
        assertThat(plan.renderInputs()).extracting(TimelineRenderPlan.RenderInput::alias).containsExactly(
            "input-0001.mp4", "input-0002.png", "pip-0001.mp4",
            "input-0004.wav", "input-0005.wav", "input-0006.wav"
        );
        assertThat(plan.pipTails()).extracting(TimelineRenderPlan.PipTail::sourceAlias,
            TimelineRenderPlan.PipTail::renderAlias, TimelineRenderPlan.PipTail::sourceStartMs,
            TimelineRenderPlan.PipTail::sourceEndMs).containsExactly(
            org.assertj.core.groups.Tuple.tuple("input-0003.mp4", "pip-0001.mp4", 0L, 5_000L)
        );
        assertThat(plan.assScript()).contains("[Script Info]", "Dialogue:");
        assertThat(plan.filterScript()).contains(
            "crop=", "overlay=", "fade=t=in:st=0:d=1.5", "ass=overlay.ass:fontsdir=fonts",
            "volume='if(lt(t,0),0.3", "0.105", "t-0)/0.12", "t-29.6",
            "amix=inputs=3:duration=longest:dropout_transition=0:normalize=0", "[vout]", "[aout]",
            "atrim=start=0:end=12,asetpts=PTS-STARTPTS,aresample=48000,aloop=loop=-1:size=576000:start=0,atrim=duration=30"
        );
        assertThat(plan.quality()).isEqualTo(TimelineOutputQuality.STANDARD);
        assertThat(plan.durationMs()).isEqualTo(30_000L);
    }

    @Test
    void mapsEveryVisualEffectCodeToAControlledFilter() throws IOException {
        Fixture fixture = fixture();

        for (TimelineVisualEffectCode effectCode : TimelineVisualEffectCode.values()) {
            TimelineRenderPlan plan = builder.build(
                fixture.commandWithTimeline(withEffectCode(fixture.command().timeline(), effectCode)),
                fixture.resolvedInputs()
            );
            assertThat(plan.filterScript()).contains(effectMarker(effectCode));
        }
    }

    @Test
    void keepsVisualEffectsInsideTheirDeclaredEndExclusiveWindows() throws IOException {
        Fixture fixture = fixture();

        TimelineRenderPlan zoom = builder.build(fixture.commandWithTimeline(withEffectWindow(
            fixture.command().timeline(), TimelineVisualEffectCode.GENTLE_ZOOM_IN, 1_050L, 5_000L, 1_000L
        )), fixture.resolvedInputs());
        assertThat(zoom.filterScript()).contains("between(on,32,61)").doesNotContain("between(on,31,150)");

        TimelineRenderPlan blur = builder.build(fixture.commandWithTimeline(withEffectWindow(
            fixture.command().timeline(), TimelineVisualEffectCode.LIGHT_BLUR, 1_050L, 5_000L, 1_000L
        )), fixture.resolvedInputs());
        assertThat(blur.filterScript()).contains("enable='gte(t,1.05)*lt(t,2.05)'")
            .doesNotContain("between(t,1.05,5)");
    }

    @Test
    void fadesRgbaVisualLayersThroughTheirAlphaChannel() throws IOException {
        Fixture fixture = fixture();
        TimelineDocumentDTO fadedImage = mutate(fixture.command().timeline(), element -> {
            if (element instanceof org.dromara.aivideo.timeline.dto.TimelineImageElementDTO image) {
                return new org.dromara.aivideo.timeline.dto.TimelineImageElementDTO(image.elementId(),
                    image.elementType(), image.startMs(), image.endMs(), image.zIndex(), image.enabled(),
                    image.locked(), image.label(), image.assetId(), image.transform(), image.fitMode(), image.crop(),
                    new org.dromara.aivideo.timeline.dto.TimelineFadeDTO(1_500L, 500L), image.sourceStartOffset(),
                    image.sourceEndOffset(), image.adoptedPrompt(), image.sourceTaskId());
            }
            return element;
        });

        TimelineRenderPlan plan = builder.build(fixture.commandWithTimeline(fadedImage), fixture.resolvedInputs());

        assertThat(plan.filterScript()).contains("fade=t=in:st=0:d=1.5:alpha=1",
            "fade=t=out:st=3.5:d=0.5:alpha=1");
    }

    @Test
    void preparesPipTailBeforeInputLevelLoopingWithoutCachingFrames() throws IOException {
        Fixture fixture = fixture();
        TimelineRenderPlan plan = builder.build(fixture.commandWithTimeline(
            withPipSourceStart(fixture.command().timeline(), 1_000L)
        ), fixture.resolvedInputs());

        assertThat(plan.pipTails()).extracting(TimelineRenderPlan.PipTail::sourceAlias,
            TimelineRenderPlan.PipTail::renderAlias, TimelineRenderPlan.PipTail::sourceStartMs,
            TimelineRenderPlan.PipTail::sourceEndMs).containsExactly(
            org.assertj.core.groups.Tuple.tuple("input-0003.mp4", "pip-0001.mp4", 1_000L, 5_000L)
        );
        assertThat(plan.renderInputs()).extracting(TimelineRenderPlan.RenderInput::alias).contains("pip-0001.mp4")
            .doesNotContain("input-0003.mp4");
        assertThat(plan.filterScript()).contains("[2:v]trim=duration=12,setpts=PTS-STARTPTS,fps=30")
            .doesNotContain("fps=30,loop=loop=");
    }

    @Test
    void preparesOneBoundedTailPerPipElementWhileMaterializingTheSourceOnlyOnce() throws IOException {
        Fixture fixture = fixture();
        TimelineDocumentDTO timeline = withSecondPip(fixture.command().timeline(), 1_000L);
        List<TimelineAssetReferenceDTO> references = withAdditionalPipElement(fixture.command().assets(), "pip_0002");

        TimelineRenderPlan plan = builder.build(
            fixture.commandWithTimelineAndReferences(timeline, references), fixture.resolvedInputs());

        assertThat(plan.inputs()).extracting(TimelineRenderPlan.Input::alias).contains("input-0003.mp4");
        assertThat(plan.renderInputs()).extracting(TimelineRenderPlan.RenderInput::alias).containsExactly(
            "input-0001.mp4", "input-0002.png", "pip-0001.mp4", "pip-0002.mp4",
            "input-0004.wav", "input-0005.wav", "input-0006.wav"
        ).doesNotContain("input-0003.mp4");
        assertThat(plan.pipTails()).extracting(TimelineRenderPlan.PipTail::sourceAlias,
            TimelineRenderPlan.PipTail::renderAlias, TimelineRenderPlan.PipTail::sourceStartMs,
            TimelineRenderPlan.PipTail::sourceEndMs).containsExactly(
            org.assertj.core.groups.Tuple.tuple("input-0003.mp4", "pip-0001.mp4", 0L, 5_000L),
            org.assertj.core.groups.Tuple.tuple("input-0003.mp4", "pip-0002.mp4", 1_000L, 5_000L)
        );
        assertThat(plan.filterScript()).contains("[2:v]trim=duration=12,setpts=PTS-STARTPTS,fps=30",
            "[3:v]trim=duration=12,setpts=PTS-STARTPTS,fps=30");
    }

    @Test
    void rejectsC0RatiosThatQuantizeToZeroCropPixels() throws IOException {
        Fixture fixture = fixture();
        TimelineDocumentDTO zeroPixelCrop = mutate(fixture.command().timeline(), element -> {
            if (element instanceof org.dromara.aivideo.timeline.dto.TimelineImageElementDTO image) {
                return new org.dromara.aivideo.timeline.dto.TimelineImageElementDTO(image.elementId(),
                    image.elementType(), image.startMs(), image.endMs(), image.zIndex(), image.enabled(),
                    image.locked(), image.label(), image.assetId(), image.transform(), image.fitMode(),
                    new org.dromara.aivideo.timeline.dto.TimelineCropDTO(BigDecimal.ZERO, BigDecimal.ZERO,
                        new BigDecimal("0.0001"), BigDecimal.ONE), image.fade(), image.sourceStartOffset(),
                    image.sourceEndOffset(), image.adoptedPrompt(), image.sourceTaskId());
            }
            return element;
        });

        assertInputInvalid(() -> builder.build(fixture.commandWithTimeline(zeroPixelCrop), fixture.resolvedInputs()));
    }

    @Test
    void rejectsForgedAssetFactsAndAmbiguousPrimaryAudio() throws IOException {
        Fixture fixture = fixture();
        List<CreationAssetResolveDTO> forged = new ArrayList<>(fixture.resolvedInputs());
        CreationAssetResolveDTO base = forged.getFirst();
        forged.set(0, new CreationAssetResolveDTO(base.assetId(), base.mimeType(), "b".repeat(64),
            base.assetType(), base.usageType(), base.sizeBytes(), base.durationMs(), base.width(), base.height(),
            base.hasVideoStream(), base.hasAudioStream()));

        assertInputInvalid(() -> builder.build(fixture.command(), forged));

        List<CreationAssetResolveDTO> duplicatePrimary = new ArrayList<>(fixture.resolvedInputs());
        base = duplicatePrimary.getFirst();
        duplicatePrimary.set(0, new CreationAssetResolveDTO(base.assetId(), base.mimeType(), base.sha256(),
            base.assetType(), base.usageType(), base.sizeBytes(), base.durationMs(), base.width(), base.height(),
            true, true));
        assertInputInvalid(() -> builder.build(fixture.command(), duplicatePrimary));

        List<TimelineAssetReferenceDTO> mismatchedReferences = new ArrayList<>(fixture.command().assets());
        TimelineAssetReferenceDTO reference = mismatchedReferences.getFirst();
        mismatchedReferences.set(0, new TimelineAssetReferenceDTO(reference.assetId(), reference.usageType(),
            List.of("unexpected-element"), reference.sha256(), reference.fileSize()));
        TimelineRenderCommandDTO mismatchedCommand = new TimelineRenderCommandDTO(
            fixture.command().taskId(), fixture.command().executionId(), fixture.command().attemptId(),
            fixture.command().inputVersionId(), fixture.command().fontRegistryVersion(),
            fixture.command().fontRegistrySha256(), fixture.command().timeline(), fixture.command().outputConfig(),
            mismatchedReferences
        );
        assertInputInvalid(() -> builder.build(mismatchedCommand, fixture.resolvedInputs()));
    }

    @Test
    void ducksOnlyDuringTheSelectedPrimaryAudioWindowAndCarriesAttackRelease() throws IOException {
        Fixture fixture = fixture();
        TimelineDocumentDTO externalWindow = withPrimaryWindow(fixture.command().timeline(), 10_000L, 20_000L);
        TimelineRenderPlan externalPlan = builder.build(fixture.commandWithTimeline(externalWindow), fixture.resolvedInputs());
        assertThat(externalPlan.filterScript()).contains("if(lt(t,10),0.3", "t-10)/0.12", "t-19.6)/0.4");

        TimelineDocumentDTO noOverlap = withBackgroundMusicWindow(externalWindow, 0L, 5_000L);
        TimelineRenderPlan noOverlapPlan = builder.build(fixture.commandWithTimeline(noOverlap), fixture.resolvedInputs());
        assertThat(noOverlapPlan.filterScript()).contains("volume='0.3':eval=frame[abgm0]");
        assertThat(noOverlapPlan.filterScript()).doesNotContain("0.105");

        TimelineDocumentDTO baseAudioTimeline = withTrackMuted(fixture.command().timeline(),
            org.dromara.aivideo.timeline.enums.TimelineTrackType.PRIMARY_AUDIO);
        List<CreationAssetResolveDTO> baseAudioFacts = withBaseAudio(fixture.resolvedInputs());
        TimelineRenderPlan baseAudioPlan = builder.build(fixture.commandWithTimeline(baseAudioTimeline), baseAudioFacts);
        assertThat(baseAudioPlan.filterScript()).contains("[0:a]atrim=", "volume='if(lt(t,0),0.3");
    }

    @Test
    void permitsSeparatedPrimaryClipsButRejectsOverlapAndAllowsSilentMixes() throws IOException {
        Fixture fixture = fixture();
        TimelineDocumentDTO separated = withSecondPrimary(withPrimaryWindow(fixture.command().timeline(), 0L, 10_000L),
            10_000L, 20_000L);
        List<TimelineAssetReferenceDTO> twoPrimaryReferences = withAdditionalPrimaryElement(
            fixture.command().assets(), "audio_primary_0002");

        TimelineRenderPlan separatedPlan = builder.build(
            fixture.commandWithTimelineAndReferences(separated, twoPrimaryReferences), fixture.resolvedInputs());
        assertThat(separatedPlan.filterScript()).contains("[aprimary0]", "[aprimary1]", "amix=inputs=4");

        TimelineDocumentDTO overlapping = withSecondPrimary(withPrimaryWindow(fixture.command().timeline(), 0L, 10_000L),
            9_999L, 20_000L);
        assertInputInvalid(() -> builder.build(
            fixture.commandWithTimelineAndReferences(overlapping, twoPrimaryReferences), fixture.resolvedInputs()));

        TimelineDocumentDTO silentPrimary = withTrackMuted(fixture.command().timeline(),
            org.dromara.aivideo.timeline.enums.TimelineTrackType.PRIMARY_AUDIO);
        TimelineRenderPlan silentPlan = builder.build(fixture.commandWithTimeline(silentPrimary), fixture.resolvedInputs());
        assertThat(silentPlan.filterScript()).contains("volume='0.3':eval=frame[abgm0]")
            .doesNotContain("aprimary");
    }

    @Test
    void rejectsMediaOutsideTheFrozenMimeAndResourcePolicy() throws IOException {
        Fixture fixture = fixture();

        assertInputInvalid(() -> builder.build(fixture.command(), withFactAt(fixture.resolvedInputs(), 0,
            fact -> copyFact(fact, "video/x-foo", fact.sizeBytes(), fact.durationMs(), fact.width(), fact.height()))));
        assertInputInvalid(() -> builder.build(fixture.command(), withFactAt(fixture.resolvedInputs(), 0,
            fact -> copyFact(fact, fact.mimeType(), fact.sizeBytes(), fact.durationMs(), 3_841, fact.height()))));
        assertInputInvalid(() -> builder.build(fixture.command(), withFactAt(fixture.resolvedInputs(), 1,
            fact -> copyFact(fact, fact.mimeType(), fact.sizeBytes(), fact.durationMs(), 8_193, fact.height()))));

        TimelineDocumentDTO oversizedPip = withPipSourceDuration(fixture.command().timeline(), 120_001L);
        assertInputInvalid(() -> builder.build(fixture.commandWithTimeline(oversizedPip), withFactAt(
            fixture.resolvedInputs(), 2, fact -> copyFact(fact, fact.mimeType(), fact.sizeBytes(), 120_001L,
                fact.width(), fact.height())
        )));

        long oversizedBytes = 1_073_741_825L;
        List<TimelineAssetReferenceDTO> oversizedReferences = withReferenceSize(fixture.command().assets(), 0,
            oversizedBytes);
        assertInputInvalid(() -> builder.build(fixture.commandWithReferences(oversizedReferences), withFactAt(
            fixture.resolvedInputs(), 0, fact -> copyFact(fact, fact.mimeType(), oversizedBytes, fact.durationMs(),
                fact.width(), fact.height())
        )));
    }

    @Test
    void keepsLabelsTextAndTemplateNamesOutOfTheFilterGraph() throws IOException {
        Fixture fixture = fixture();
        String maliciousLabel = "label; -filter_complex [owned]";
        String maliciousText = "text{\\bord99}';[owned]";
        TimelineDocumentDTO attacked = mutate(fixture.command().timeline(), element -> {
            if (element instanceof TimelineFancyTextElementDTO fancy) {
                return new TimelineFancyTextElementDTO(fancy.elementId(), fancy.elementType(), fancy.startMs(),
                    fancy.endMs(), fancy.zIndex(), fancy.enabled(), fancy.locked(), maliciousLabel, maliciousText,
                    fancy.templateCode(), fancy.fontCode(), fancy.fontVersion(), fancy.fontSha256(), fancy.color(),
                    fancy.accentColor(), fancy.transform(), fancy.animationIntensity(), fancy.enterDurationMs(),
                    fancy.exitDurationMs(), fancy.suggestionTaskId(), fancy.suggestionReason());
            }
            return element;
        });

        TimelineRenderPlan plan = builder.build(fixture.commandWithTimeline(attacked), fixture.resolvedInputs());

        assertThat(plan.filterScript()).doesNotContain(maliciousLabel, maliciousText, "keyword_pop");
        assertThat(plan.assScript()).doesNotContain(maliciousText).contains("\\{");
        assertThatThrownBy(() -> builder.build(fixture.commandWithTimeline(attacked), List.of(
            new CreationAssetResolveDTO("1; -i owned", "video/mp4", SHA, CreationAssetType.VIDEO,
                TimelineAssetUsageType.BASE_VIDEO, 1_000L, 30_000L, 1920, 1080, true, false)
        ))).isInstanceOf(TimelineExecutionException.class);
    }

    private static void assertInputInvalid(ThrowingAction action) {
        assertThatThrownBy(action::run)
            .isInstanceOf(TimelineExecutionException.class)
            .extracting(error -> ((TimelineExecutionException) error).code())
            .isEqualTo(TimelineExecutionFailureCode.INPUT_INVALID);
    }

    private static TimelineDocumentDTO withEffectCode(TimelineDocumentDTO document,
                                                       TimelineVisualEffectCode code) {
        return mutate(document, element -> {
            if (element instanceof TimelineVisualEffectElementDTO effect) {
                BigDecimal scale = switch (code) {
                    case GENTLE_ZOOM_IN, GENTLE_ZOOM_OUT -> new BigDecimal("1.1");
                    default -> null;
                };
                BigDecimal radius = code == TimelineVisualEffectCode.LIGHT_BLUR ? new BigDecimal("2") : null;
                return new TimelineVisualEffectElementDTO(effect.elementId(), effect.elementType(), effect.startMs(),
                    effect.endMs(), effect.zIndex(), effect.enabled(), effect.locked(), effect.label(), code,
                    effect.durationMs(), scale, radius);
            }
            return element;
        });
    }

    private static TimelineDocumentDTO withEffectWindow(TimelineDocumentDTO document,
                                                         TimelineVisualEffectCode code,
                                                         long startMs,
                                                         long endMs,
                                                         long durationMs) {
        return mutate(document, element -> {
            if (element instanceof TimelineVisualEffectElementDTO effect) {
                BigDecimal scale = switch (code) {
                    case GENTLE_ZOOM_IN, GENTLE_ZOOM_OUT -> new BigDecimal("1.1");
                    default -> null;
                };
                BigDecimal radius = code == TimelineVisualEffectCode.LIGHT_BLUR ? new BigDecimal("2") : null;
                return new TimelineVisualEffectElementDTO(effect.elementId(), effect.elementType(), startMs, endMs,
                    effect.zIndex(), effect.enabled(), effect.locked(), effect.label(), code, durationMs, scale,
                    radius);
            }
            return element;
        });
    }

    private static TimelineDocumentDTO mutate(TimelineDocumentDTO document,
                                              UnaryOperator<org.dromara.aivideo.timeline.dto.TimelineElementDTO> mapper) {
        return new TimelineDocumentDTO(document.schemaVersion(), document.canvas(), document.tracks().stream()
            .map(track -> new TimelineTrackDTO(track.trackId(), track.trackType(), track.area(), track.order(),
                track.locked(), track.muted(), track.elements().stream().map(mapper).toList()))
            .toList());
    }

    private static TimelineDocumentDTO withPrimaryWindow(TimelineDocumentDTO document, long startMs, long endMs) {
        return mutate(document, element -> {
            if (element instanceof org.dromara.aivideo.timeline.dto.TimelineAudioElementDTO audio
                && audio.usageType() == TimelineAssetUsageType.PRIMARY_AUDIO) {
                return new org.dromara.aivideo.timeline.dto.TimelineAudioElementDTO(audio.elementId(),
                    audio.elementType(), startMs, endMs, audio.zIndex(), audio.enabled(), audio.locked(), audio.label(),
                    audio.assetId(), audio.usageType(), audio.sourceDurationMs(), audio.sourceStartMs(),
                    audio.sourceEndMs(), audio.volumeRatio(), audio.fade(), audio.loopWhenOverflow(),
                    audio.duckingEnabled(), audio.targetGainRatio(), audio.attackMs(), audio.releaseMs());
            }
            return element;
        });
    }

    private static TimelineDocumentDTO withSecondPrimary(TimelineDocumentDTO document, long startMs, long endMs) {
        return new TimelineDocumentDTO(document.schemaVersion(), document.canvas(), document.tracks().stream()
            .map(track -> {
                if (track.trackType() != org.dromara.aivideo.timeline.enums.TimelineTrackType.PRIMARY_AUDIO) {
                    return track;
                }
                List<org.dromara.aivideo.timeline.dto.TimelineElementDTO> elements = new ArrayList<>(track.elements());
                org.dromara.aivideo.timeline.dto.TimelineAudioElementDTO primary =
                    (org.dromara.aivideo.timeline.dto.TimelineAudioElementDTO) elements.getFirst();
                elements.add(new org.dromara.aivideo.timeline.dto.TimelineAudioElementDTO("audio_primary_0002",
                    primary.elementType(), startMs, endMs, primary.zIndex(), primary.enabled(), primary.locked(),
                    "second-primary", primary.assetId(), primary.usageType(), primary.sourceDurationMs(),
                    primary.sourceStartMs(), primary.sourceEndMs(), primary.volumeRatio(), primary.fade(),
                    primary.loopWhenOverflow(), primary.duckingEnabled(), primary.targetGainRatio(), primary.attackMs(),
                    primary.releaseMs()));
                return new TimelineTrackDTO(track.trackId(), track.trackType(), track.area(), track.order(),
                    track.locked(), track.muted(), elements);
            })
            .toList());
    }

    private static TimelineDocumentDTO withBackgroundMusicWindow(TimelineDocumentDTO document,
                                                                   long startMs,
                                                                   long endMs) {
        return mutate(document, element -> {
            if (element instanceof org.dromara.aivideo.timeline.dto.TimelineAudioElementDTO audio
                && audio.usageType() == TimelineAssetUsageType.BACKGROUND_MUSIC) {
                return new org.dromara.aivideo.timeline.dto.TimelineAudioElementDTO(audio.elementId(),
                    audio.elementType(), startMs, endMs, audio.zIndex(), audio.enabled(), audio.locked(), audio.label(),
                    audio.assetId(), audio.usageType(), audio.sourceDurationMs(), audio.sourceStartMs(),
                    audio.sourceEndMs(), audio.volumeRatio(), audio.fade(), audio.loopWhenOverflow(),
                    audio.duckingEnabled(), audio.targetGainRatio(), audio.attackMs(), audio.releaseMs());
            }
            return element;
        });
    }

    private static TimelineDocumentDTO withTrackMuted(TimelineDocumentDTO document,
                                                       org.dromara.aivideo.timeline.enums.TimelineTrackType trackType) {
        return new TimelineDocumentDTO(document.schemaVersion(), document.canvas(), document.tracks().stream()
            .map(track -> track.trackType() == trackType
                ? new TimelineTrackDTO(track.trackId(), track.trackType(), track.area(), track.order(), track.locked(),
                    true, track.elements())
                : track)
            .toList());
    }

    private static TimelineDocumentDTO withPipSourceDuration(TimelineDocumentDTO document, long sourceDurationMs) {
        return mutate(document, element -> {
            if (element instanceof org.dromara.aivideo.timeline.dto.TimelinePipVideoElementDTO pip) {
                return new org.dromara.aivideo.timeline.dto.TimelinePipVideoElementDTO(pip.elementId(),
                    pip.elementType(), pip.startMs(), pip.endMs(), pip.zIndex(), pip.enabled(), pip.locked(),
                    pip.label(), pip.assetId(), pip.transform(), pip.fitMode(), pip.crop(), pip.fade(),
                    sourceDurationMs, pip.sourceStartMs(), pip.loopWhenOverflow(), pip.audioEnabled());
            }
            return element;
        });
    }

    private static TimelineDocumentDTO withPipSourceStart(TimelineDocumentDTO document, long sourceStartMs) {
        return mutate(document, element -> {
            if (element instanceof org.dromara.aivideo.timeline.dto.TimelinePipVideoElementDTO pip) {
                return new org.dromara.aivideo.timeline.dto.TimelinePipVideoElementDTO(pip.elementId(),
                    pip.elementType(), pip.startMs(), pip.endMs(), pip.zIndex(), pip.enabled(), pip.locked(),
                    pip.label(), pip.assetId(), pip.transform(), pip.fitMode(), pip.crop(), pip.fade(),
                    pip.sourceDurationMs(), sourceStartMs, pip.loopWhenOverflow(), pip.audioEnabled());
            }
            return element;
        });
    }

    private static TimelineDocumentDTO withSecondPip(TimelineDocumentDTO document, long sourceStartMs) {
        return new TimelineDocumentDTO(document.schemaVersion(), document.canvas(), document.tracks().stream()
            .map(track -> {
                if (track.trackType() != org.dromara.aivideo.timeline.enums.TimelineTrackType.PIP_VIDEO) {
                    return track;
                }
                List<org.dromara.aivideo.timeline.dto.TimelineElementDTO> elements = new ArrayList<>(track.elements());
                org.dromara.aivideo.timeline.dto.TimelinePipVideoElementDTO pip =
                    (org.dromara.aivideo.timeline.dto.TimelinePipVideoElementDTO) elements.getFirst();
                elements.add(new org.dromara.aivideo.timeline.dto.TimelinePipVideoElementDTO("pip_0002",
                    pip.elementType(), pip.startMs(), pip.endMs(), pip.zIndex() + 1, pip.enabled(), pip.locked(),
                    "second-pip", pip.assetId(), pip.transform(), pip.fitMode(), pip.crop(), pip.fade(),
                    pip.sourceDurationMs(), sourceStartMs, pip.loopWhenOverflow(), pip.audioEnabled()));
                return new TimelineTrackDTO(track.trackId(), track.trackType(), track.area(), track.order(),
                    track.locked(), track.muted(), elements);
            })
            .toList());
    }

    private static List<CreationAssetResolveDTO> withBaseAudio(List<CreationAssetResolveDTO> inputs) {
        List<CreationAssetResolveDTO> result = new ArrayList<>(inputs);
        CreationAssetResolveDTO base = result.getFirst();
        result.set(0, new CreationAssetResolveDTO(base.assetId(), base.mimeType(), base.sha256(), base.assetType(),
            base.usageType(), base.sizeBytes(), base.durationMs(), base.width(), base.height(), true, true));
        return result;
    }

    private static List<CreationAssetResolveDTO> withFactAt(List<CreationAssetResolveDTO> inputs,
                                                             int index,
                                                             UnaryOperator<CreationAssetResolveDTO> mapper) {
        List<CreationAssetResolveDTO> result = new ArrayList<>(inputs);
        result.set(index, mapper.apply(result.get(index)));
        return result;
    }

    private static CreationAssetResolveDTO copyFact(CreationAssetResolveDTO source,
                                                     String mimeType,
                                                     long sizeBytes,
                                                     Long durationMs,
                                                     Integer width,
                                                     Integer height) {
        return new CreationAssetResolveDTO(source.assetId(), mimeType, source.sha256(), source.assetType(),
            source.usageType(), sizeBytes, durationMs, width, height, source.hasVideoStream(),
            source.hasAudioStream());
    }

    private static List<TimelineAssetReferenceDTO> withReferenceSize(List<TimelineAssetReferenceDTO> references,
                                                                       int index,
                                                                       long fileSize) {
        List<TimelineAssetReferenceDTO> result = new ArrayList<>(references);
        TimelineAssetReferenceDTO reference = result.get(index);
        result.set(index, new TimelineAssetReferenceDTO(reference.assetId(), reference.usageType(),
            reference.elementIds(), reference.sha256(), fileSize));
        return result;
    }

    private static List<TimelineAssetReferenceDTO> withAdditionalPrimaryElement(List<TimelineAssetReferenceDTO> references,
                                                                                  String elementId) {
        return references.stream().map(reference -> reference.usageType() == TimelineAssetUsageType.PRIMARY_AUDIO
            ? new TimelineAssetReferenceDTO(reference.assetId(), reference.usageType(),
                List.of(reference.elementIds().getFirst(), elementId), reference.sha256(), reference.fileSize())
            : reference).toList();
    }

    private static List<TimelineAssetReferenceDTO> withAdditionalPipElement(List<TimelineAssetReferenceDTO> references,
                                                                              String elementId) {
        return references.stream().map(reference -> reference.usageType() == TimelineAssetUsageType.PIP_VIDEO
            ? new TimelineAssetReferenceDTO(reference.assetId(), reference.usageType(),
                List.of(reference.elementIds().getFirst(), elementId), reference.sha256(), reference.fileSize())
            : reference).toList();
    }

    private static String effectMarker(TimelineVisualEffectCode effectCode) {
        return switch (effectCode) {
            case FADE_IN, FADE_OUT -> "fade=t=";
            case GENTLE_ZOOM_IN, GENTLE_ZOOM_OUT -> "zoompan=";
            case LIGHT_BLUR -> "boxblur=";
        };
    }

    private static Fixture fixture() throws IOException {
        JsonNode root = JSON.readTree(repositoryFile("docs/contracts/creation-timeline/timeline-draft.example.json").toFile());
        TimelineDocumentDTO timeline = JSON.treeToValue(root.required("timeline"), TimelineDocumentDTO.class);
        List<TimelineAssetReferenceDTO> references = List.of(
            reference("90071992547410003", TimelineAssetUsageType.BASE_VIDEO, "main_video_0001"),
            reference("90071992547410001", TimelineAssetUsageType.IMAGE, "image_0001"),
            reference("90071992547410002", TimelineAssetUsageType.PIP_VIDEO, "pip_0001"),
            reference("90071992547410004", TimelineAssetUsageType.PRIMARY_AUDIO, "audio_primary_0001"),
            reference("90071992547410005", TimelineAssetUsageType.BACKGROUND_MUSIC, "audio_bgm_0001"),
            reference("90071992547410006", TimelineAssetUsageType.SOUND_EFFECT, "audio_sfx_0001")
        );
        TimelineRenderCommandDTO command = new TimelineRenderCommandDTO(
            "task-1", "execution-1", "attempt-1", "version-1", "timeline-fonts-1",
            "2e0198557dc5a00c4cdde6eb970a3c2282c298f169c3f6bd7349c275156a9e33", timeline,
            new org.dromara.aivideo.timeline.dto.TimelineOutputConfigDTO("match_canvas", 30,
                TimelineOutputQuality.STANDARD), references
        );
        List<CreationAssetResolveDTO> resolved = List.of(
            media("90071992547410003", CreationAssetType.VIDEO, TimelineAssetUsageType.BASE_VIDEO,
                30_000L, 1920, 1080, true, false),
            media("90071992547410001", CreationAssetType.IMAGE, TimelineAssetUsageType.IMAGE,
                null, 640, 480, true, false),
            media("90071992547410002", CreationAssetType.VIDEO, TimelineAssetUsageType.PIP_VIDEO,
                5_000L, 640, 360, true, false),
            media("90071992547410004", CreationAssetType.AUDIO, TimelineAssetUsageType.PRIMARY_AUDIO,
                30_000L, null, null, false, true),
            media("90071992547410005", CreationAssetType.AUDIO, TimelineAssetUsageType.BACKGROUND_MUSIC,
                12_000L, null, null, false, true),
            media("90071992547410006", CreationAssetType.AUDIO, TimelineAssetUsageType.SOUND_EFFECT,
                1_000L, null, null, false, true)
        );
        return new Fixture(command, resolved);
    }

    private static TimelineAssetReferenceDTO reference(String assetId, TimelineAssetUsageType usageType,
                                                        String elementId) {
        return new TimelineAssetReferenceDTO(assetId, usageType, List.of(elementId), SHA, 1_000L);
    }

    private static CreationAssetResolveDTO media(String assetId, CreationAssetType assetType,
                                                 TimelineAssetUsageType usageType, Long durationMs,
                                                 Integer width, Integer height, boolean video, boolean audio) {
        String mimeType = switch (assetType) {
            case VIDEO -> "video/mp4";
            case IMAGE -> "image/png";
            case AUDIO -> "audio/wav";
        };
        return new CreationAssetResolveDTO(assetId, mimeType, SHA, assetType, usageType, 1_000L, durationMs,
            width, height, video, audio);
    }

    private static Path repositoryFile(String relativePath) {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(relativePath);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("repository file is missing");
    }

    private record Fixture(TimelineRenderCommandDTO command, List<CreationAssetResolveDTO> resolvedInputs) {
        private TimelineRenderCommandDTO commandWithTimeline(TimelineDocumentDTO timeline) {
            return new TimelineRenderCommandDTO(command.taskId(), command.executionId(), command.attemptId(),
                command.inputVersionId(), command.fontRegistryVersion(), command.fontRegistrySha256(), timeline,
                command.outputConfig(), command.assets());
        }

        private TimelineRenderCommandDTO commandWithReferences(List<TimelineAssetReferenceDTO> references) {
            return new TimelineRenderCommandDTO(command.taskId(), command.executionId(), command.attemptId(),
                command.inputVersionId(), command.fontRegistryVersion(), command.fontRegistrySha256(),
                command.timeline(), command.outputConfig(), references);
        }

        private TimelineRenderCommandDTO commandWithTimelineAndReferences(TimelineDocumentDTO timeline,
                                                                            List<TimelineAssetReferenceDTO> references) {
            return new TimelineRenderCommandDTO(command.taskId(), command.executionId(), command.attemptId(),
                command.inputVersionId(), command.fontRegistryVersion(), command.fontRegistrySha256(), timeline,
                command.outputConfig(), references);
        }
    }

    @FunctionalInterface
    private interface ThrowingAction {
        void run();
    }
}
