package org.dromara.aivideo.infra.timeline.render;

import org.dromara.aivideo.creation.enums.CreationAssetType;
import org.dromara.aivideo.timeline.constant.TimelineContractLimits;
import org.dromara.aivideo.timeline.enums.TimelineAssetUsageType;
import org.dromara.aivideo.timeline.enums.TimelineOutputQuality;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Internal render description containing only generated aliases and controlled scripts.
 * It deliberately has no original file names, object keys, or storage paths.
 */
public record TimelineRenderPlan(
    String executionId,
    String attemptId,
    long durationMs,
    List<Input> inputs,
    List<RenderInput> renderInputs,
    List<PipTail> pipTails,
    String assScript,
    String filterScript,
    TimelineOutputQuality quality
) {

    public static final String ASS_FILE_NAME = "overlay.ass";
    public static final String FILTER_FILE_NAME = "filter.txt";
    public static final String FONTS_DIRECTORY_NAME = "fonts";
    public static final String OUTPUT_FILE_NAME = "output.mp4";

    private static final Pattern INPUT_ALIAS = Pattern.compile("input-[0-9]{4}\\.(mp4|png|wav)");
    private static final Pattern PIP_ALIAS = Pattern.compile("pip-[0-9]{4}\\.mp4");
    private static final Pattern DECIMAL_ID = Pattern.compile("[1-9][0-9]*");
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    private static final long MAX_DURATION_MS = TimelineContractLimits.NUMERIC_LIMITS
        .get("maxDurationMs").longValueExact();

    public TimelineRenderPlan {
        executionId = identifier(executionId);
        attemptId = identifier(attemptId);
        if (durationMs < 1 || durationMs > MAX_DURATION_MS) {
            throw invalid();
        }
        inputs = immutable(inputs, true);
        renderInputs = immutable(renderInputs, true);
        pipTails = immutable(pipTails, false);
        validateTopology(inputs, renderInputs, pipTails);
        assScript = script(assScript);
        filterScript = script(filterScript);
        quality = Objects.requireNonNull(quality, "quality");
    }

    public record Input(
        String alias,
        String assetId,
        String sha256,
        long sizeBytes,
        CreationAssetType assetType,
        TimelineAssetUsageType usageType,
        boolean loopInput
    ) {

        public Input {
            if (alias == null || !INPUT_ALIAS.matcher(alias).matches()) {
                throw invalid();
            }
            if (assetId == null || !DECIMAL_ID.matcher(assetId).matches()) {
                throw invalid();
            }
            if (sha256 == null || !SHA256.matcher(sha256).matches() || sizeBytes <= 0) {
                throw invalid();
            }
            assetType = Objects.requireNonNull(assetType, "assetType");
            usageType = Objects.requireNonNull(usageType, "usageType");
            if (!alias.endsWith('.' + extension(assetType)) || !validUsage(assetType, usageType, loopInput)) {
                throw invalid();
            }
        }

        private static boolean validUsage(CreationAssetType assetType,
                                          TimelineAssetUsageType usageType,
                                          boolean loopInput) {
            return switch (usageType) {
                case BASE_VIDEO -> assetType == CreationAssetType.VIDEO && !loopInput;
                case IMAGE -> assetType == CreationAssetType.IMAGE && !loopInput;
                case PIP_VIDEO -> assetType == CreationAssetType.VIDEO && loopInput;
                case PRIMARY_AUDIO -> assetType == CreationAssetType.AUDIO && !loopInput;
                case BACKGROUND_MUSIC -> assetType == CreationAssetType.AUDIO && loopInput;
                case SOUND_EFFECT -> assetType == CreationAssetType.AUDIO && !loopInput;
            };
        }
    }

    /** A final FFmpeg input; PIP tails are the only stream-looped inputs. */
    public record RenderInput(
        String alias,
        CreationAssetType assetType,
        TimelineAssetUsageType usageType,
        boolean streamLoop
    ) {

        public RenderInput {
            assetType = Objects.requireNonNull(assetType, "assetType");
            usageType = Objects.requireNonNull(usageType, "usageType");
            boolean valid = switch (usageType) {
                case BASE_VIDEO -> inputAlias(alias, CreationAssetType.VIDEO) && !streamLoop;
                case IMAGE -> inputAlias(alias, CreationAssetType.IMAGE) && !streamLoop;
                case PIP_VIDEO -> alias != null && PIP_ALIAS.matcher(alias).matches()
                    && assetType == CreationAssetType.VIDEO && streamLoop;
                case PRIMARY_AUDIO, BACKGROUND_MUSIC, SOUND_EFFECT -> inputAlias(alias, CreationAssetType.AUDIO)
                    && !streamLoop;
            };
            if (!valid) {
                throw invalid();
            }
        }
    }

    /** A bounded, re-encoded tail prepared before the final PIP input is looped. */
    public record PipTail(String sourceAlias, String renderAlias, long sourceStartMs, long sourceEndMs) {

        public PipTail {
            if (sourceAlias == null || !INPUT_ALIAS.matcher(sourceAlias).matches()
                || renderAlias == null || !PIP_ALIAS.matcher(renderAlias).matches()
                || sourceStartMs < 0 || sourceEndMs <= sourceStartMs || sourceEndMs > MAX_DURATION_MS) {
                throw invalid();
            }
        }
    }

    static String extension(CreationAssetType assetType) {
        return switch (assetType) {
            case VIDEO -> "mp4";
            case IMAGE -> "png";
            case AUDIO -> "wav";
        };
    }

    private static <T> List<T> immutable(List<T> values, boolean required) {
        if (values == null || (required && values.isEmpty())) {
            throw invalid();
        }
        List<T> copy = List.copyOf(values);
        if (copy.stream().anyMatch(Objects::isNull)) {
            throw invalid();
        }
        return copy;
    }

    private static void validateTopology(List<Input> inputs,
                                         List<RenderInput> renderInputs,
                                         List<PipTail> pipTails) {
        Map<String, Input> sourceByAlias = new HashMap<>();
        for (Input input : inputs) {
            if (sourceByAlias.putIfAbsent(input.alias(), input) != null) {
                throw invalid();
            }
        }
        Map<String, RenderInput> renderByAlias = new HashMap<>();
        for (RenderInput renderInput : renderInputs) {
            if (renderByAlias.putIfAbsent(renderInput.alias(), renderInput) != null) {
                throw invalid();
            }
        }
        Set<String> tailAliases = new HashSet<>();
        Set<String> expectedRenderAliases = new HashSet<>();
        for (PipTail tail : pipTails) {
            Input source = sourceByAlias.get(tail.sourceAlias());
            RenderInput render = renderByAlias.get(tail.renderAlias());
            if (source == null || source.assetType() != CreationAssetType.VIDEO
                || source.usageType() != TimelineAssetUsageType.PIP_VIDEO || render == null
                || render.assetType() != CreationAssetType.VIDEO
                || render.usageType() != TimelineAssetUsageType.PIP_VIDEO || !render.streamLoop()
                || !tailAliases.add(tail.renderAlias())) {
                throw invalid();
            }
            expectedRenderAliases.add(tail.renderAlias());
        }
        for (Input source : inputs) {
            if (source.usageType() == TimelineAssetUsageType.PIP_VIDEO) {
                continue;
            }
            expectedRenderAliases.add(source.alias());
            RenderInput render = renderByAlias.get(source.alias());
            if (render == null || render.assetType() != source.assetType() || render.usageType() != source.usageType()
                || render.streamLoop()) {
                throw invalid();
            }
        }
        if (!renderByAlias.keySet().equals(expectedRenderAliases)) {
            throw invalid();
        }
        for (RenderInput render : renderInputs) {
            if (render.usageType() == TimelineAssetUsageType.PIP_VIDEO && !tailAliases.contains(render.alias())) {
                throw invalid();
            }
        }
    }

    private static boolean inputAlias(String alias, CreationAssetType assetType) {
        return alias != null && INPUT_ALIAS.matcher(alias).matches() && alias.endsWith('.' + extension(assetType));
    }

    private static String identifier(String value) {
        if (value == null || value.isBlank() || value.length() > 128 || value.indexOf('\0') >= 0) {
            throw invalid();
        }
        return value;
    }

    private static String script(String value) {
        if (value == null || value.isBlank() || value.indexOf('\0') >= 0) {
            throw invalid();
        }
        return value;
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("invalid timeline render plan");
    }
}
