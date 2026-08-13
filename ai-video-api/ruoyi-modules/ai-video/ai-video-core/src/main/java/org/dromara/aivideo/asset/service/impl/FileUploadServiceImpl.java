package org.dromara.aivideo.asset.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.aivideo.asset.domain.AssetFile;
import org.dromara.aivideo.asset.domain.UploadSession;
import org.dromara.aivideo.asset.dto.CompleteUploadDTO;
import org.dromara.aivideo.asset.dto.CreateUploadSessionDTO;
import org.dromara.aivideo.asset.dto.RunningHubUploadedFileDTO;
import org.dromara.aivideo.asset.dto.UploadSessionDTO;
import org.dromara.aivideo.asset.mapper.AssetFileMapper;
import org.dromara.aivideo.asset.mapper.UploadSessionMapper;
import org.dromara.aivideo.asset.service.IFileUploadService;
import org.dromara.aivideo.asset.service.IRunningHubFileTransferService;
import org.dromara.aivideo.identity.dto.AppPrincipalSnapshotDTO;
import org.dromara.aivideo.identity.dto.AppWorkspaceSessionSnapshotDTO;
import org.dromara.aivideo.workflow.dto.WorkflowTemplateDTOs;
import org.dromara.aivideo.workflow.service.IWorkflowTemplateService;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;

/** Coordinates an owner-scoped upload that is streamed to RunningHub without a local input-file copy. */
@Service
@RequiredArgsConstructor
public class FileUploadServiceImpl implements IFileUploadService {

    private static final int INVALID_UPLOAD = 46211;
    private static final int UPLOAD_CREATE_FAILED = 46214;
    private static final int MAX_FILE_SIZE_BYTES = 300 * 1024 * 1024;
    private static final String WORKFLOW_INPUT_SCOPE = "workflow_order";

    private final UploadSessionMapper uploadSessionMapper;
    private final AssetFileMapper assetFileMapper;
    private final IRunningHubFileTransferService runningHubFileTransferService;
    private final IWorkflowTemplateService workflowTemplateService;

    @Override
    public UploadSessionDTO createWorkflowInputSession(CreateUploadSessionDTO command, AppPrincipalSnapshotDTO principal) {
        AppWorkspaceSessionSnapshotDTO workspace = requireUploadPermission(principal);
        validate(command);
        validateTemplateInput(command);
        UploadSession existingSession = uploadSessionMapper.selectOne(new LambdaQueryWrapper<UploadSession>()
            .eq(UploadSession::getTenantId, workspace.tenantId())
            .eq(UploadSession::getWorkspaceId, workspace.workspaceKey())
            .eq(UploadSession::getOwnerUserId, principal.appUserId())
            .eq(UploadSession::getIdempotencyKey, command.idempotencyKey()));
        if (existingSession != null) {
            return reuseSession(existingSession, command, workspace, principal.appUserId());
        }

        UploadSession session = new UploadSession();
        session.setTenantId(workspace.tenantId());
        session.setWorkspaceId(workspace.workspaceKey());
        session.setContextScope(WORKFLOW_INPUT_SCOPE);
        session.setOwnerUserId(principal.appUserId());
        session.setTemplateId(parseId(command.templateId()));
        session.setSchemaHash(command.schemaHash());
        session.setInputKey(command.inputKey());
        session.setOriginalFileName(safeFileName(command.fileName()));
        session.setDeclaredContentType(normalizeContentType(command.declaredContentType()));
        session.setDeclaredSizeBytes(command.sizeBytes());
        session.setIdempotencyKey(command.idempotencyKey());
        session.setStatus("created");
        session.setExpiresAt(LocalDateTime.now().plusMinutes(15));
        if (uploadSessionMapper.insert(session) != 1 || session.getUploadSessionId() == null) {
            throw new ServiceException("工作流输入上传会话创建失败", UPLOAD_CREATE_FAILED);
        }
        return initializedSession(session);
    }

    @Override
    public UploadSessionDTO transferWorkflowInputContent(String uploadId, String contentType, Long contentLength,
                                                         InputStream content, AppPrincipalSnapshotDTO principal) {
        AppWorkspaceSessionSnapshotDTO workspace = requireUploadPermission(principal);
        UploadSession session = requireOwnedSession(uploadId, workspace, principal.appUserId());
        if ("completed".equals(session.getStatus())) {
            return completedSession(session, workspace, principal.appUserId());
        }
        validateActiveSession(session);
        validateContentRequest(session, contentType, contentLength, content);
        if (uploadSessionMapper.update(null, new LambdaUpdateWrapper<UploadSession>()
            .eq(UploadSession::getUploadSessionId, session.getUploadSessionId())
            .eq(UploadSession::getStatus, "created")
            .set(UploadSession::getStatus, "uploading")) != 1) {
            throw new ServiceException("上传会话状态已变化", INVALID_UPLOAD);
        }

        CountingInputStream countedContent = new CountingInputStream(content);
        try {
            RunningHubUploadedFileDTO uploaded = runningHubFileTransferService.uploadWorkflowInput(
                Long.toString(session.getTemplateId()), session.getOriginalFileName(), session.getDeclaredContentType(),
                session.getDeclaredSizeBytes(), countedContent);
            if (uploaded == null || isBlank(uploaded.fileName())
                || countedContent.count() != session.getDeclaredSizeBytes()) {
                throw new ServiceException("RunningHub 文件上传失败", UPLOAD_CREATE_FAILED);
            }
            AssetFile asset = new AssetFile();
            asset.setTenantId(workspace.tenantId());
            asset.setWorkspaceId(workspace.workspaceKey());
            asset.setOwnerId(principal.appUserId());
            asset.setCategory("workflow_input");
            asset.setObjectKey(uploaded.fileName());
            asset.setOriginalName(session.getOriginalFileName());
            asset.setContentType(session.getDeclaredContentType());
            asset.setFileFormat(fileExtension(session.getOriginalFileName()));
            asset.setWidth(0);
            asset.setHeight(0);
            asset.setFileSize(session.getDeclaredSizeBytes());
            asset.setStatus("ready");
            if (assetFileMapper.insert(asset) != 1 || asset.getAssetId() == null) {
                throw new ServiceException("工作流输入素材创建失败", UPLOAD_CREATE_FAILED);
            }
            if (uploadSessionMapper.update(null, new LambdaUpdateWrapper<UploadSession>()
                .eq(UploadSession::getUploadSessionId, session.getUploadSessionId())
                .eq(UploadSession::getStatus, "uploading")
                .set(UploadSession::getAssetId, asset.getAssetId())
                .set(UploadSession::getRunninghubFileName, uploaded.fileName())
                .set(UploadSession::getStatus, "completed")) != 1) {
                throw new ServiceException("上传会话完成状态写入失败", UPLOAD_CREATE_FAILED);
            }
            session.setAssetId(asset.getAssetId());
            session.setRunninghubFileName(uploaded.fileName());
            session.setStatus("completed");
            return completedSession(session, workspace, principal.appUserId());
        } catch (RuntimeException exception) {
            uploadSessionMapper.update(null, new LambdaUpdateWrapper<UploadSession>()
                .eq(UploadSession::getUploadSessionId, session.getUploadSessionId())
                .eq(UploadSession::getStatus, "uploading")
                .set(UploadSession::getStatus, "created"));
            throw exception;
        }
    }

    @Override
    public UploadSessionDTO completeWorkflowInputSession(String uploadId, CompleteUploadDTO command,
                                                         AppPrincipalSnapshotDTO principal) {
        AppWorkspaceSessionSnapshotDTO workspace = requireUploadPermission(principal);
        if (command == null || !"single".equals(command.mode())) {
            throw new ServiceException("工作流输入上传完成参数无效", INVALID_UPLOAD);
        }
        UploadSession session = requireOwnedSession(uploadId, workspace, principal.appUserId());
        if ("completed".equals(session.getStatus())) {
            return completedSession(session, workspace, principal.appUserId());
        }
        validateActiveSession(session);
        throw new ServiceException("上传内容尚未提交", INVALID_UPLOAD);
    }

    private UploadSessionDTO initializedSession(UploadSession session) {
        return new UploadSessionDTO(Long.toString(session.getUploadSessionId()), null,
            "single", "initialized", session.getExpiresAt(), null, Map.of(), null, null);
    }

    private UploadSessionDTO completedSession(UploadSession session, AppWorkspaceSessionSnapshotDTO workspace,
                                              Long ownerUserId) {
        if (session.getAssetId() == null) {
            throw new ServiceException("上传会话素材不可用", INVALID_UPLOAD);
        }
        AssetFile asset = assetFileMapper.selectOne(new LambdaQueryWrapper<AssetFile>()
            .eq(AssetFile::getAssetId, session.getAssetId())
            .eq(AssetFile::getTenantId, workspace.tenantId())
            .eq(AssetFile::getWorkspaceId, workspace.workspaceKey())
            .eq(AssetFile::getOwnerId, ownerUserId)
            .eq(AssetFile::getCategory, "workflow_input")
            .eq(AssetFile::getDelFlag, "0"));
        if (asset == null || asset.getAssetId() == null || !"ready".equals(asset.getStatus())) {
            throw new ServiceException("上传会话素材不可用", INVALID_UPLOAD);
        }
        return new UploadSessionDTO(Long.toString(session.getUploadSessionId()), null,
            "single", "completed", session.getExpiresAt(), null, Map.of(), Long.toString(asset.getAssetId()), "ready");
    }

    private UploadSessionDTO reuseSession(UploadSession session, CreateUploadSessionDTO command,
                                          AppWorkspaceSessionSnapshotDTO workspace, Long ownerUserId) {
        if ("completed".equals(session.getStatus())) {
            return completedSession(session, workspace, ownerUserId);
        }
        validateActiveSession(session);
        if (!sameWorkflowInput(session, command)) {
            throw new ServiceException("幂等键已用于不同的上传文件", 409);
        }
        return initializedSession(session);
    }

    private UploadSession requireOwnedSession(String uploadId, AppWorkspaceSessionSnapshotDTO workspace, Long ownerUserId) {
        long id = parseId(uploadId);
        UploadSession session = uploadSessionMapper.selectOne(new LambdaQueryWrapper<UploadSession>()
            .eq(UploadSession::getUploadSessionId, id)
            .eq(UploadSession::getTenantId, workspace.tenantId())
            .eq(UploadSession::getWorkspaceId, workspace.workspaceKey())
            .eq(UploadSession::getOwnerUserId, ownerUserId));
        if (session == null) throw new ServiceException("上传会话不存在", 404);
        return session;
    }

    private void validateActiveSession(UploadSession session) {
        if (session.getExpiresAt() == null || session.getExpiresAt().isBefore(LocalDateTime.now())) {
            uploadSessionMapper.update(null, new LambdaUpdateWrapper<UploadSession>()
                .eq(UploadSession::getUploadSessionId, session.getUploadSessionId())
                .eq(UploadSession::getStatus, session.getStatus())
                .set(UploadSession::getStatus, "expired"));
            throw new ServiceException("上传会话已过期", 46212);
        }
        if (!"created".equals(session.getStatus())) {
            throw new ServiceException("上传会话不可用", INVALID_UPLOAD);
        }
    }

    private void validateContentRequest(UploadSession session, String contentType, Long contentLength, InputStream content) {
        if (content == null || contentLength == null || contentLength <= 0
            || !contentLength.equals(session.getDeclaredSizeBytes())
            || !normalizeContentType(contentType).equals(session.getDeclaredContentType())) {
            throw new ServiceException("上传文件与会话声明不一致", INVALID_UPLOAD);
        }
    }

    private void validate(CreateUploadSessionDTO command) {
        if (command == null || isBlank(command.templateId()) || isBlank(command.schemaHash())
            || isBlank(command.fileName()) || isBlank(command.declaredContentType())
            || command.sizeBytes() == null || command.sizeBytes() <= 0 || command.sizeBytes() > MAX_FILE_SIZE_BYTES
            || isBlank(command.idempotencyKey()) || command.idempotencyKey().length() > 128 || isBlank(command.inputKey())
            || command.inputKey().length() > 48 || isBlank(safeFileName(command.fileName()))) {
            throw new ServiceException("工作流输入上传参数无效", INVALID_UPLOAD);
        }
    }

    private void validateTemplateInput(CreateUploadSessionDTO command) {
        WorkflowTemplateDTOs.CreationConfig config = workflowTemplateService.queryCreationConfig(command.templateId());
        if (config == null || !command.schemaHash().equals(config.schemaHash())) {
            throw new ServiceException("模板表单已更新，请刷新后重新上传", INVALID_UPLOAD);
        }
        WorkflowTemplateDTOs.InputField input = config.fields().stream()
            .filter(field -> command.inputKey().equals(field.inputKey()))
            .findFirst().orElseThrow(() -> new ServiceException("上传字段不属于当前模板", INVALID_UPLOAD));
        if (!("asset".equals(input.valueType()) || "asset_array".equals(input.valueType()))) {
            throw new ServiceException("当前字段不支持上传文件", INVALID_UPLOAD);
        }
        WorkflowTemplateDTOs.InputConstraints constraints = input.constraints();
        String contentType = normalizeContentType(command.declaredContentType());
        if (constraints != null && constraints.allowedContentTypes() != null && !constraints.allowedContentTypes().isEmpty()
            && !constraints.allowedContentTypes().contains(contentType)) {
            throw new ServiceException("文件类型不符合模板要求", INVALID_UPLOAD);
        }
        if (constraints != null && !isBlank(constraints.maxBytesPerAsset())) {
            try {
                if (command.sizeBytes() > Long.parseLong(constraints.maxBytesPerAsset())) {
                    throw new ServiceException("文件大小超过模板限制", INVALID_UPLOAD);
                }
            } catch (NumberFormatException exception) {
                throw new ServiceException("模板文件大小配置无效", INVALID_UPLOAD);
            }
        }
    }

    private boolean sameWorkflowInput(UploadSession session, CreateUploadSessionDTO command) {
        return session.getTemplateId() != null && session.getTemplateId() == parseId(command.templateId())
            && command.schemaHash().equals(session.getSchemaHash()) && command.inputKey().equals(session.getInputKey())
            && safeFileName(command.fileName()).equals(session.getOriginalFileName())
            && normalizeContentType(command.declaredContentType()).equals(session.getDeclaredContentType())
            && command.sizeBytes().equals(session.getDeclaredSizeBytes());
    }

    private AppWorkspaceSessionSnapshotDTO requireUploadPermission(AppPrincipalSnapshotDTO principal) {
        if (principal == null || principal.appUserId() == null || principal.appUserId() <= 0
            || principal.workspace() == null || principal.workspace().tenantId() == null
            || isBlank(principal.workspace().workspaceKey())
            || !principal.workspace().permissions().contains("aivideo:asset:upload")) {
            throw new ServiceException("无素材上传权限", 403);
        }
        return principal.workspace();
    }

    private String safeFileName(String fileName) {
        if (fileName == null) return "";
        String normalized = fileName.replace('\\', '/');
        normalized = normalized.substring(normalized.lastIndexOf('/') + 1).trim();
        if (normalized.isEmpty() || normalized.chars().anyMatch(Character::isISOControl)) return "";
        return normalized.substring(0, Math.min(normalized.length(), 255));
    }

    private String normalizeContentType(String contentType) {
        if (contentType == null) return "";
        int separator = contentType.indexOf(';');
        return contentType.substring(0, separator < 0 ? contentType.length() : separator).strip().toLowerCase(Locale.ROOT);
    }

    private String fileExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 && dot < fileName.length() - 1 ? fileName.substring(dot + 1).toLowerCase(Locale.ROOT) : "bin";
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private long parseId(String value) {
        try {
            long id = Long.parseLong(value);
            if (id <= 0) throw new NumberFormatException();
            return id;
        } catch (NumberFormatException exception) {
            throw new ServiceException("上传会话不存在", 404);
        }
    }

    private static final class CountingInputStream extends InputStream {
        private final InputStream delegate;
        private long count;

        private CountingInputStream(InputStream delegate) {
            this.delegate = delegate;
        }

        @Override
        public int read() throws IOException {
            int result = delegate.read();
            if (result >= 0) count++;
            return result;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int result = delegate.read(buffer, offset, length);
            if (result > 0) count += result;
            return result;
        }

        private long count() {
            return count;
        }
    }
}
