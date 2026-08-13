package org.dromara.aivideo.timeline.dto;

import java.util.List;

public record TimelineDocumentDTO(
    String schemaVersion,
    TimelineCanvasDTO canvas,
    List<TimelineTrackDTO> tracks
) {
}
