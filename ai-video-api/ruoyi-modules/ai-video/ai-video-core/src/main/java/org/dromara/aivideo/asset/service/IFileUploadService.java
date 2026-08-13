package org.dromara.aivideo.asset.service;

import org.dromara.aivideo.asset.dto.CompleteUploadDTO;
import org.dromara.aivideo.asset.dto.CreateUploadSessionDTO;
import org.dromara.aivideo.asset.dto.UploadSessionDTO;
import org.dromara.aivideo.identity.dto.AppPrincipalSnapshotDTO;

import java.io.InputStream;

/** Owner-scoped workflow input upload session service. */
public interface IFileUploadService {

    UploadSessionDTO createWorkflowInputSession(CreateUploadSessionDTO command, AppPrincipalSnapshotDTO principal);

    UploadSessionDTO completeWorkflowInputSession(String uploadId, CompleteUploadDTO command,
                                                  AppPrincipalSnapshotDTO principal);

    UploadSessionDTO transferWorkflowInputContent(String uploadId, String contentType, Long contentLength,
                                                  InputStream content, AppPrincipalSnapshotDTO principal);
}
