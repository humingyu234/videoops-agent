package org.dromara.aivideo.asset.service;

import org.dromara.aivideo.asset.dto.RunningHubUploadedFileDTO;

import java.io.InputStream;

/** Streams a user-owned workflow input to the single RunningHub execution configuration. */
public interface IRunningHubFileTransferService {

    RunningHubUploadedFileDTO uploadWorkflowInput(String templateId, String fileName, String contentType,
                                                  long sizeBytes, InputStream content);
}
