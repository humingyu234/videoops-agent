package org.dromara.aivideo.digitalhuman.service;

import org.dromara.aivideo.digitalhuman.dto.DigitalHumanVideoPollDTO;
import org.dromara.aivideo.digitalhuman.dto.DigitalHumanVideoSubmitDTO;

public interface IDigitalHumanVideoService {
    String submit(DigitalHumanVideoSubmitDTO request);

    DigitalHumanVideoPollDTO poll(String providerJobId);
}
