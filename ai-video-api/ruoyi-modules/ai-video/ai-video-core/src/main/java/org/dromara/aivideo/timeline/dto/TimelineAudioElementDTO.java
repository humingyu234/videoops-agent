package org.dromara.aivideo.timeline.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.dromara.aivideo.timeline.enums.TimelineAssetUsageType;
import org.dromara.aivideo.timeline.enums.TimelineElementType;

import java.math.BigDecimal;

public record TimelineAudioElementDTO(
    String elementId,
    TimelineElementType elementType,
    long startMs,
    long endMs,
    int zIndex,
    boolean enabled,
    boolean locked,
    String label,
    String assetId,
    TimelineAssetUsageType usageType,
    long sourceDurationMs,
    long sourceStartMs,
    long sourceEndMs,
    BigDecimal volumeRatio,
    TimelineFadeDTO fade,
    boolean loopWhenOverflow,
    boolean duckingEnabled,
    @JsonInclude(JsonInclude.Include.NON_NULL) BigDecimal targetGainRatio,
    @JsonInclude(JsonInclude.Include.NON_DEFAULT) int attackMs,
    @JsonInclude(JsonInclude.Include.NON_DEFAULT) int releaseMs
) implements TimelineElementDTO {

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public static TimelineAudioElementDTO fromJson(
        @JsonProperty("elementId") String elementId,
        @JsonProperty("elementType") TimelineElementType elementType,
        @JsonProperty("startMs") long startMs,
        @JsonProperty("endMs") long endMs,
        @JsonProperty("zIndex") int zIndex,
        @JsonProperty("enabled") boolean enabled,
        @JsonProperty("locked") boolean locked,
        @JsonProperty("label") String label,
        @JsonProperty("assetId") String assetId,
        @JsonProperty("usageType") TimelineAssetUsageType usageType,
        @JsonProperty("sourceDurationMs") long sourceDurationMs,
        @JsonProperty("sourceStartMs") long sourceStartMs,
        @JsonProperty("sourceEndMs") long sourceEndMs,
        @JsonProperty("volumeRatio") BigDecimal volumeRatio,
        @JsonProperty("fade") TimelineFadeDTO fade,
        @JsonProperty("loopWhenOverflow") boolean loopWhenOverflow,
        @JsonProperty("duckingEnabled") boolean duckingEnabled,
        @JsonProperty("targetGainRatio") BigDecimal targetGainRatio,
        @JsonProperty("attackMs") Integer attackMs,
        @JsonProperty("releaseMs") Integer releaseMs
    ) {
        return new TimelineAudioElementDTO(
            elementId, elementType, startMs, endMs, zIndex, enabled, locked, label, assetId, usageType,
            sourceDurationMs, sourceStartMs, sourceEndMs, volumeRatio, fade, loopWhenOverflow, duckingEnabled,
            targetGainRatio, attackMs == null ? 0 : attackMs, releaseMs == null ? 0 : releaseMs
        );
    }
}
