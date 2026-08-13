package org.dromara.aivideo.digitalhuman.service;

import org.dromara.aivideo.digitalhuman.dto.CreateDigitalHumanVideoByResourceDTO;
import org.dromara.aivideo.digitalhuman.dto.CreateVoiceGenerationByResourceDTO;
import org.dromara.aivideo.digitalhuman.dto.DigitalHumanJobDTO;

public interface IDigitalHumanResourceGenerationService {
    DigitalHumanJobDTO createVoiceJob(CreateVoiceGenerationByResourceDTO request);

    DigitalHumanJobDTO createVideoJob(CreateDigitalHumanVideoByResourceDTO request);
}
