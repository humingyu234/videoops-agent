package org.dromara.aivideo.timeline.service;

import org.dromara.aivideo.timeline.dto.TimelineProgressDTO;

@FunctionalInterface
public interface TimelineTaskProgressListener {
    void onProgress(TimelineProgressDTO progress);
}
