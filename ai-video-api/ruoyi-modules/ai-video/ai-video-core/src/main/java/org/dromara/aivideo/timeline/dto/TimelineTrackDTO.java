package org.dromara.aivideo.timeline.dto;

import org.dromara.aivideo.timeline.enums.TimelineTrackArea;
import org.dromara.aivideo.timeline.enums.TimelineTrackType;

import java.util.List;

public record TimelineTrackDTO(
    String trackId,
    TimelineTrackType trackType,
    TimelineTrackArea area,
    int order,
    boolean locked,
    boolean muted,
    List<TimelineElementDTO> elements
) {
}
