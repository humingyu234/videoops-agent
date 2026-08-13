package org.dromara.aivideo.digitalhuman.service;

import org.dromara.aivideo.digitalhuman.dto.DigitalHumanMediaContentDTO;
import org.dromara.aivideo.digitalhuman.dto.DigitalHumanStoredMediaDTO;

public interface IDigitalHumanMediaStorageService {
    DigitalHumanStoredMediaDTO storeInput(Long jobId, String fileName, String mediaType, byte[] content);

    DigitalHumanStoredMediaDTO storeOutput(Long jobId, String fileName, String mediaType, byte[] content);

    DigitalHumanMediaContentDTO read(String key);

    /**
     * Idempotently deletes a private media object. Missing objects are treated as success.
     *
     * @param key storage-relative media key
     */
    void delete(String key);
}
