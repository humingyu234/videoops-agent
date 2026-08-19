package org.dromara.aivideo.studio.draft.service.impl;

import lombok.RequiredArgsConstructor;
import org.dromara.aivideo.identity.dto.AppPrincipalSnapshotDTO;
import org.dromara.aivideo.identity.dto.AppWorkspaceSessionSnapshotDTO;
import org.dromara.aivideo.studio.draft.domain.StudioWorkflowDraft;
import org.dromara.aivideo.studio.draft.dto.StudioWorkflowDraftDTO;
import org.dromara.aivideo.studio.draft.mapper.StudioWorkflowDraftMapper;
import org.dromara.aivideo.studio.draft.service.IStudioWorkflowDraftService;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Objects;

/** 只持久化页面恢复所需快照；生成任务和项目仍由各自服务持有。 */
@Service
@RequiredArgsConstructor
public class StudioWorkflowDraftServiceImpl implements IStudioWorkflowDraftService {
    static final String SCHEMA_VERSION = "studio-workflow-1";
    static final int REVISION_CONFLICT = 46141;
    private static final int MAX_SNAPSHOT_BYTES = 128 * 1024;

    private final StudioWorkflowDraftMapper mapper;
    private final JsonMapper jsonMapper;

    @Override
    public StudioWorkflowDraftDTO getCurrent(AppPrincipalSnapshotDTO principal) {
        Owner owner = requireOwner(principal, "aivideo:studio:query");
        return toDTO(mapper.selectOwned(owner.tenantId(), owner.userId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StudioWorkflowDraftDTO save(SaveCommand command, AppPrincipalSnapshotDTO principal) {
        Owner owner = requireOwner(principal, "aivideo:studio:generate");
        SaveCommand safe = validate(command);
        StudioWorkflowDraft current = mapper.selectOwned(owner.tenantId(), owner.userId());
        if (current == null) {
            if (safe.expectedRevision() != 0) throw revisionConflict();
            StudioWorkflowDraft created = new StudioWorkflowDraft();
            LocalDateTime now = LocalDateTime.now();
            created.setTenantId(owner.tenantId());
            created.setOwnerUserId(owner.userId());
            created.setRevision(1L);
            created.setCurrentStep(safe.currentStep());
            created.setSchemaVersion(safe.schemaVersion());
            created.setSnapshotJson(safe.snapshotJson());
            created.setCreateBy(owner.userId());
            created.setUpdateBy(owner.userId());
            created.setCreateTime(now);
            created.setUpdateTime(now);
            try {
                if (mapper.insert(created) != 1) throw new ServiceException("工作台草稿保存失败", 500);
            } catch (DuplicateKeyException exception) {
                throw revisionConflict();
            }
            return toDTO(created);
        }
        if (!Objects.equals(current.getRevision(), safe.expectedRevision())
            || mapper.updateOwned(current.getId(), owner.tenantId(), owner.userId(), safe.expectedRevision(),
            safe.currentStep(), safe.schemaVersion(), safe.snapshotJson()) != 1) {
            throw revisionConflict();
        }
        current.setRevision(current.getRevision() + 1);
        current.setCurrentStep(safe.currentStep());
        current.setSchemaVersion(safe.schemaVersion());
        current.setSnapshotJson(safe.snapshotJson());
        current.setUpdateTime(LocalDateTime.now());
        return toDTO(current);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void clear(AppPrincipalSnapshotDTO principal) {
        Owner owner = requireOwner(principal, "aivideo:studio:generate");
        mapper.deleteOwned(owner.tenantId(), owner.userId());
    }

    private SaveCommand validate(SaveCommand command) {
        if (command == null || command.expectedRevision() < 0 || command.currentStep() < 0
            || command.currentStep() > 6 || !SCHEMA_VERSION.equals(command.schemaVersion())
            || command.snapshotJson() == null
            || command.snapshotJson().getBytes(StandardCharsets.UTF_8).length > MAX_SNAPSHOT_BYTES) {
            throw new ServiceException("工作台草稿参数无效", 400);
        }
        try {
            JsonNode root = jsonMapper.readTree(command.snapshotJson());
            if (root == null || !root.isObject()) throw new IllegalArgumentException();
        } catch (RuntimeException exception) {
            throw new ServiceException("工作台草稿格式无效", 400);
        }
        return command;
    }

    private Owner requireOwner(AppPrincipalSnapshotDTO principal, String permission) {
        if (principal == null || principal.appUserId() == null || principal.appUserId() <= 0
            || principal.workspace() == null) {
            throw new ServiceException("当前创作身份不可用", 403);
        }
        AppWorkspaceSessionSnapshotDTO workspace = principal.workspace();
        if (workspace.tenantId() == null || workspace.tenantId() <= 0
            || !"personal".equals(workspace.workspaceType())
            || !"app_user".equals(workspace.ownerType())
            || !Objects.equals(workspace.ownerId(), principal.appUserId())
            || !workspace.permissions().contains(permission)) {
            throw new ServiceException("无工作台草稿操作权限", 403);
        }
        return new Owner(workspace.tenantId(), principal.appUserId());
    }

    private StudioWorkflowDraftDTO toDTO(StudioWorkflowDraft draft) {
        if (draft == null) return null;
        return new StudioWorkflowDraftDTO(Long.toString(draft.getRevision()), draft.getCurrentStep(),
            draft.getSchemaVersion(), draft.getSnapshotJson(), draft.getUpdateTime());
    }

    private ServiceException revisionConflict() {
        return new ServiceException("工作台草稿已在其他页面更新，请刷新后重试", REVISION_CONFLICT);
    }

    private record Owner(Long tenantId, Long userId) {
    }
}
