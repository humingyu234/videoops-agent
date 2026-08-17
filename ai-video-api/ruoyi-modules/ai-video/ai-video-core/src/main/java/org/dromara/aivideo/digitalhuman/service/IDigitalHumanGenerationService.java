package org.dromara.aivideo.digitalhuman.service;

import org.dromara.aivideo.digitalhuman.dto.CreateDigitalHumanVideoJobDTO;
import org.dromara.aivideo.digitalhuman.dto.CreateVoiceGenerationJobDTO;
import org.dromara.aivideo.digitalhuman.dto.DigitalHumanJobDTO;
import org.dromara.aivideo.digitalhuman.dto.DigitalHumanMediaContentDTO;
import org.dromara.aivideo.digitalhuman.dto.DigitalHumanOwnerDTO;

public interface IDigitalHumanGenerationService {
    DigitalHumanJobDTO createVoiceJob(CreateVoiceGenerationJobDTO request);

    DigitalHumanJobDTO confirmVoiceJob(Long jobId, DigitalHumanOwnerDTO owner);

    DigitalHumanJobDTO createVideoJob(CreateDigitalHumanVideoJobDTO request);

    DigitalHumanJobDTO getJob(Long jobId, DigitalHumanOwnerDTO owner);

    /** Reads the persisted owner-scoped job without polling or dispatching a Provider. */
    DigitalHumanJobDTO getStoredJob(Long jobId, DigitalHumanOwnerDTO owner);

    DigitalHumanMediaContentDTO getOutputMedia(Long jobId, DigitalHumanOwnerDTO owner);
}
