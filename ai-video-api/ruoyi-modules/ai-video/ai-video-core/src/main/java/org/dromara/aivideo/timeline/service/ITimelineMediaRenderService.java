package org.dromara.aivideo.timeline.service;

import org.dromara.aivideo.creation.service.CreationMediaHandle;
import org.dromara.aivideo.timeline.dto.TimelineMediaProbeDTO;
import org.dromara.aivideo.timeline.dto.TimelineRenderCommandDTO;
import org.dromara.aivideo.timeline.dto.TimelineTextMeasureCommandDTO;
import org.dromara.aivideo.timeline.dto.TimelineTextMeasureResultDTO;

import java.util.List;
import java.util.function.BooleanSupplier;

public interface ITimelineMediaRenderService {
    TimelineMediaProbeDTO probe(CreationMediaHandle input);
    TimelineTextMeasureResultDTO measureText(TimelineTextMeasureCommandDTO command);
    TimelineRenderOutputHandle render(TimelineRenderCommandDTO command,
        List<CreationMediaHandle> inputs, TimelineTaskProgressListener progress,
        BooleanSupplier cancellationRequested);
    void cancel(String executionId, String attemptId);
}
