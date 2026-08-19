package org.dromara.aivideo.studio.draft.service;

import org.dromara.aivideo.identity.dto.AppPrincipalSnapshotDTO;
import org.dromara.aivideo.studio.draft.dto.StudioWorkflowDraftDTO;

/** 当前用户工作台草稿的 owner-scoped 读写服务。 */
public interface IStudioWorkflowDraftService {

    StudioWorkflowDraftDTO getCurrent(AppPrincipalSnapshotDTO principal);

    StudioWorkflowDraftDTO save(SaveCommand command, AppPrincipalSnapshotDTO principal);

    void clear(AppPrincipalSnapshotDTO principal);

    record SaveCommand(long expectedRevision, int currentStep, String schemaVersion, String snapshotJson) {
    }
}
