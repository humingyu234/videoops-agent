package org.dromara.aivideo.infra.timeline.render;

import org.dromara.aivideo.creation.service.CreationMediaHandle;
import org.dromara.aivideo.timeline.constant.TimelineErrorCodes;
import org.dromara.aivideo.timeline.dto.TimelineMediaProbeDTO;
import org.dromara.aivideo.timeline.dto.TimelineMediaQualityInspectionDTO;
import org.dromara.aivideo.timeline.dto.TimelineRenderCommandDTO;
import org.dromara.aivideo.timeline.dto.TimelineTextMeasureCommandDTO;
import org.dromara.aivideo.timeline.dto.TimelineTextMeasureResultDTO;
import org.dromara.aivideo.timeline.service.ITimelineMediaRenderService;
import org.dromara.aivideo.timeline.service.TimelineRenderOutputHandle;
import org.dromara.aivideo.timeline.service.TimelineTaskProgressListener;
import org.dromara.common.core.exception.ServiceException;

import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Fail-closed timeline renderer used whenever local media infrastructure is disabled.
 */
public final class UnavailableTimelineMediaRenderService implements ITimelineMediaRenderService {

    @Override
    public TimelineMediaProbeDTO probe(CreationMediaHandle input) {
        throw unavailable();
    }

    @Override
    public TimelineMediaQualityInspectionDTO inspectQuality(CreationMediaHandle input) {
        throw unavailable();
    }

    @Override
    public TimelineTextMeasureResultDTO measureText(TimelineTextMeasureCommandDTO command) {
        throw unavailable();
    }

    @Override
    public TimelineRenderOutputHandle render(TimelineRenderCommandDTO command, List<CreationMediaHandle> inputs,
                                             TimelineTaskProgressListener progress,
                                             BooleanSupplier cancellationRequested) {
        throw unavailable();
    }

    @Override
    public void cancel(String executionId, String attemptId) {
        throw unavailable();
    }

    private static ServiceException unavailable() {
        return new ServiceException("时间轴媒体渲染能力暂不可用", TimelineErrorCodes.TIMELINE_RENDER_UNAVAILABLE);
    }
}
