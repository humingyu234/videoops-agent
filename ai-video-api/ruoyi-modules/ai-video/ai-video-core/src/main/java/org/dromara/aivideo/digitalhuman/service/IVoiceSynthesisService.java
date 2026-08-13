package org.dromara.aivideo.digitalhuman.service;

import org.dromara.aivideo.digitalhuman.dto.VoiceSynthesisRequestDTO;
import org.dromara.aivideo.digitalhuman.dto.VoiceSynthesisResultDTO;

public interface IVoiceSynthesisService {
    VoiceSynthesisResultDTO synthesize(VoiceSynthesisRequestDTO request);
}
