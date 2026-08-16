package org.dromara.aivideo.timeline.service;

import org.dromara.aivideo.creation.dto.CreationAssetDTO;
import org.dromara.aivideo.task.dto.AiTaskDTO;
import org.dromara.aivideo.timeline.dto.TimelineOutputQualityDTO;

public interface ITimelineOutputQualityService {

    TimelineOutputQualityDTO evaluate(long actorId, AiTaskDTO task, CreationAssetDTO asset);
}
