package org.dromara.aivideo.timeline.dto;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.dromara.aivideo.timeline.enums.TimelineElementType;

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "elementType",
    visible = true
)
@JsonSubTypes({
    @JsonSubTypes.Type(value = TimelineMainVideoElementDTO.class, name = "main_video"),
    @JsonSubTypes.Type(value = TimelineImageElementDTO.class, name = "image_overlay"),
    @JsonSubTypes.Type(value = TimelinePipVideoElementDTO.class, name = "pip_video"),
    @JsonSubTypes.Type(value = TimelineSubtitleElementDTO.class, name = "subtitle"),
    @JsonSubTypes.Type(value = TimelineFancyTextElementDTO.class, name = "fancy_text"),
    @JsonSubTypes.Type(value = TimelineAudioElementDTO.class, name = "audio"),
    @JsonSubTypes.Type(value = TimelineVisualEffectElementDTO.class, name = "visual_effect")
})
public sealed interface TimelineElementDTO permits
    TimelineMainVideoElementDTO,
    TimelineImageElementDTO,
    TimelinePipVideoElementDTO,
    TimelineSubtitleElementDTO,
    TimelineFancyTextElementDTO,
    TimelineAudioElementDTO,
    TimelineVisualEffectElementDTO {

    String elementId();

    TimelineElementType elementType();

    long startMs();

    long endMs();

    int zIndex();

    boolean enabled();

    boolean locked();

    String label();
}
