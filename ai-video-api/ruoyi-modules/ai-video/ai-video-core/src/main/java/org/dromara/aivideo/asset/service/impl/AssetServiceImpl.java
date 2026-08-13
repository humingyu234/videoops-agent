package org.dromara.aivideo.asset.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.aivideo.asset.PortraitImageMetadata;
import org.dromara.aivideo.asset.PortraitImageValidator;
import org.dromara.aivideo.asset.VoiceSampleMetadata;
import org.dromara.aivideo.asset.VoiceSampleValidator;
import org.dromara.aivideo.asset.domain.AssetFile;
import org.dromara.aivideo.asset.dto.AssetAccessUrlDTO;
import org.dromara.aivideo.asset.dto.AssetDTO;
import org.dromara.aivideo.asset.dto.UploadPortraitImageDTO;
import org.dromara.aivideo.asset.dto.UploadVoiceSampleDTO;
import org.dromara.aivideo.asset.mapper.AssetFileMapper;
import org.dromara.aivideo.asset.service.IAssetService;
import org.dromara.aivideo.asset.service.PortraitAssetReader;
import org.dromara.aivideo.asset.service.VoiceAssetReader;
import org.dromara.aivideo.identity.dto.AppPrincipalSnapshotDTO;
import org.dromara.aivideo.identity.dto.AppWorkspaceSessionSnapshotDTO;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.oss.client.OssClient;
import org.dromara.common.oss.factory.OssFactory;
import org.dromara.common.oss.model.Options;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/** 私有文件上传、访问和删除编排。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AssetServiceImpl implements IAssetService {
    private static final String PORTRAIT_CATEGORY = "portrait_image";
    private static final String VOICE_CATEGORY = "voice_sample";
    private static final int ASSET_INVALID = 46302;
    private static final int ASSET_DELETE_FAILED = 46211;
    private final AssetFileMapper assetMapper;
    private final PortraitImageValidator imageValidator;
    private final VoiceSampleValidator voiceValidator;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AssetDTO uploadPortraitImage(UploadPortraitImageDTO command, AppPrincipalSnapshotDTO principal) {
        requirePermission(principal, "aivideo:portrait:add");
        if (command == null) {
            throw new ServiceException("人物照片不能为空", PortraitImageValidator.FILE_TYPE_NOT_ALLOWED);
        }
        PortraitImageMetadata metadata = imageValidator.validate(command.fileName(), command.contentType(), command.content());
        AppWorkspaceSessionSnapshotDTO workspace = requireWorkspace(principal);
        OssClient client = OssFactory.instance();
        String key = client.buildPathKey("portraits/" + principal.appUserId(), UUID.randomUUID() + metadata.fileSuffix());
        try {
            client.upload(key, command.content(), Options.builder().setContentType(metadata.contentType()));
        } catch (RuntimeException exception) {
            throw new ServiceException("人物照片上传失败", exception);
        }
        AtomicBoolean cleanupPending = new AtomicBoolean(true);
        registerRollbackCleanup(client, key, cleanupPending);
        AssetFile asset = new AssetFile();
        asset.setTenantId(workspace.tenantId());
        asset.setWorkspaceId(workspace.workspaceKey());
        asset.setOwnerId(principal.appUserId());
        asset.setCategory(PORTRAIT_CATEGORY);
        asset.setObjectKey(key);
        asset.setOriginalName(safeOriginalName(command.fileName()));
        asset.setContentType(metadata.contentType());
        asset.setFileFormat(metadata.format());
        asset.setWidth(metadata.width());
        asset.setHeight(metadata.height());
        asset.setFileSize(metadata.size());
        asset.setStatus("ready");
        asset.setCreateBy(principal.appUserId());
        asset.setUpdateBy(principal.appUserId());
        try {
            if (assetMapper.insert(asset) != 1 || asset.getAssetId() == null) {
                throw new ServiceException("人物照片资产创建失败", ASSET_INVALID);
            }
        } catch (RuntimeException | Error exception) {
            if (cleanupPending.compareAndSet(true, false)) safeDeleteObject(client, key);
            throw exception;
        }
        return toDTO(asset);
    }

    private void registerRollbackCleanup(OssClient client, String key, AtomicBoolean cleanupPending) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_COMMITTED) {
                    cleanupPending.set(false);
                } else if (cleanupPending.compareAndSet(true, false)) {
                    safeDeleteObject(client, key);
                }
            }
        });
    }

    private void safeDeleteObject(OssClient client, String key) {
        try {
            if (!client.delete(key)) log.error("人物照片上传补偿删除失败，objectKey={}", key);
        } catch (RuntimeException exception) {
            log.error("人物照片上传补偿删除异常，objectKey={}", key, exception);
        }
    }

    @Override
    public AssetDTO requireOwnedReadyPortraitAsset(String assetId, AppPrincipalSnapshotDTO principal) {
        AppWorkspaceSessionSnapshotDTO workspace = requireWorkspace(principal);
        AssetFile asset = assetMapper.selectOwnedPortraitAssetForUpdate(parseId(assetId), workspace.tenantId(),
            workspace.workspaceKey(), principal.appUserId());
        if (asset == null) throw invalidAsset();
        if (!"ready".equals(asset.getStatus()) || !PORTRAIT_CATEGORY.equals(asset.getCategory())) {
            throw invalidAsset();
        }
        return toDTO(asset);
    }

    @Override
    public AssetDTO requireOwnedPortraitAsset(String assetId, AppPrincipalSnapshotDTO principal) {
        AssetFile asset = requireOwned(assetId, principal);
        if (!PORTRAIT_CATEGORY.equals(asset.getCategory())) {
            throw invalidAsset();
        }
        return toDTO(asset);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AssetDTO uploadVoiceSample(UploadVoiceSampleDTO command, AppPrincipalSnapshotDTO principal) {
        requirePermission(principal, "aivideo:voice:upload");
        if (command == null || command.content() == null) {
            throw new ServiceException("声音文件不能为空", VoiceSampleValidator.FILE_TYPE_NOT_ALLOWED);
        }
        AppWorkspaceSessionSnapshotDTO workspace = requireWorkspace(principal);
        OssClient client = OssFactory.instance();
        try (BufferedInputStream input = new BufferedInputStream(command.content())) {
            VoiceSampleMetadata metadata = voiceValidator.validate(
                command.fileName(), command.contentType(), command.fileSize(), input);
            String key = client.buildPathKey("voices/" + principal.appUserId(),
                UUID.randomUUID() + "." + metadata.format());
            client.upload(key, input, metadata.size());
            AssetFile asset = new AssetFile();
            asset.setTenantId(workspace.tenantId());
            asset.setWorkspaceId(workspace.workspaceKey());
            asset.setOwnerId(principal.appUserId());
            asset.setCategory(VOICE_CATEGORY);
            asset.setObjectKey(key);
            asset.setOriginalName(safeOriginalName(command.fileName(), "voice"));
            asset.setContentType(canonicalVoiceContentType(metadata.format()));
            asset.setFileFormat(metadata.format());
            asset.setWidth(0);
            asset.setHeight(0);
            asset.setFileSize(metadata.size());
            asset.setStatus("ready");
            asset.setCreateBy(principal.appUserId());
            asset.setUpdateBy(principal.appUserId());
            if (assetMapper.insert(asset) != 1 || asset.getAssetId() == null) {
                client.delete(key);
                throw new ServiceException("声音资产创建失败", ASSET_INVALID);
            }
            return toDTO(asset);
        } catch (ServiceException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new ServiceException("声音文件上传失败", exception);
        }
    }

    @Override
    public AssetDTO requireOwnedReadyVoiceAsset(String assetId, AppPrincipalSnapshotDTO principal) {
        AssetFile asset = requireOwned(assetId, principal);
        if (!"ready".equals(asset.getStatus()) || !VOICE_CATEGORY.equals(asset.getCategory())) {
            throw invalidAsset();
        }
        return toDTO(asset);
    }

    @Override
    public <T> T readOwnedVoiceAsset(String assetId, AppPrincipalSnapshotDTO principal, VoiceAssetReader<T> reader) {
        AssetFile asset = requireOwned(assetId, principal);
        if (!"ready".equals(asset.getStatus()) || !VOICE_CATEGORY.equals(asset.getCategory())) {
            throw invalidAsset();
        }
        AssetDTO dto = toDTO(asset);
        return OssFactory.instance().download(asset.getObjectKey(), (ignored, input) -> reader.read(dto, input));
    }

    @Override
    public <T> T readOwnedPortraitAsset(String assetId, AppPrincipalSnapshotDTO principal,
                                        PortraitAssetReader<T> reader) {
        AssetFile asset = requireOwned(assetId, principal);
        if (!"ready".equals(asset.getStatus()) || !PORTRAIT_CATEGORY.equals(asset.getCategory())) {
            throw invalidAsset();
        }
        AssetDTO dto = toDTO(asset);
        return OssFactory.instance().download(asset.getObjectKey(), (ignored, input) -> reader.read(dto, input));
    }

    @Override
    public String createVoiceAccessUrl(String assetId, AppPrincipalSnapshotDTO principal) {
        AssetFile asset = requireOwned(assetId, principal);
        if (!"ready".equals(asset.getStatus()) || !VOICE_CATEGORY.equals(asset.getCategory())) {
            throw new ServiceException("人物照片尚不可预览", 46209);
        }
        return OssFactory.instance().presignGetUrl(asset.getObjectKey(), Duration.ofSeconds(120));
    }

    @Override
    public AssetAccessUrlDTO createPortraitAccessUrl(String assetId, AppPrincipalSnapshotDTO principal) {
        AssetFile asset = requireOwned(assetId, principal);
        if (!"ready".equals(asset.getStatus()) || !PORTRAIT_CATEGORY.equals(asset.getCategory())) {
            throw new ServiceException("人物照片尚不可预览", 46209);
        }
        LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(120);
        String url = OssFactory.instance().presignGetUrl(asset.getObjectKey(), Duration.ofSeconds(120));
        return new AssetAccessUrlDTO(url, expiresAt, asset.getContentType());
    }

    @Override
    public AssetAccessUrlDTO createWorkflowAccessUrl(String assetId, AppPrincipalSnapshotDTO principal) {
        AssetFile asset = requireOwned(assetId, principal);
        if (!"ready".equals(asset.getStatus()) || !"workflow_output".equals(asset.getCategory())) {
            throw new ServiceException("工作流素材尚不可访问", 46209);
        }
        LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(120);
        String url = OssFactory.instance().presignGetUrl(asset.getObjectKey(), Duration.ofSeconds(120));
        return new AssetAccessUrlDTO(url, expiresAt, asset.getContentType());
    }

    @Override
    public void deleteOwnedAsset(String assetId, AppPrincipalSnapshotDTO principal) {
        AssetFile asset = requireOwned(assetId, principal);
        try {
            if (!OssFactory.instance().delete(asset.getObjectKey())) {
                throw new ServiceException("人物照片文件删除失败", ASSET_DELETE_FAILED);
            }
        } catch (ServiceException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ServiceException("人物照片文件删除失败", ASSET_DELETE_FAILED);
        }
        if (assetMapper.deleteById(asset.getAssetId()) != 1) {
            throw new ServiceException("人物照片资产删除失败", ASSET_DELETE_FAILED);
        }
    }

    @Override
    public void markDeletePending(String assetId, AppPrincipalSnapshotDTO principal) {
        updateDeleteStatus(assetId, "delete_pending", null, principal);
    }

    @Override
    public void markDeleteFailed(String assetId, String reason, AppPrincipalSnapshotDTO principal) {
        String safeReason = reason == null ? "人物照片文件删除失败" : reason.substring(0, Math.min(500, reason.length()));
        updateDeleteStatus(assetId, "delete_failed", safeReason, principal);
    }

    @Override
    public void deleteObject(String assetId, AppPrincipalSnapshotDTO principal) {
        AssetFile asset = requireOwned(assetId, principal);
        try {
            if (!OssFactory.instance().delete(asset.getObjectKey())) {
                throw new ServiceException("人物照片文件删除失败", ASSET_DELETE_FAILED);
            }
        } catch (ServiceException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ServiceException("人物照片文件删除失败", ASSET_DELETE_FAILED);
        }
    }

    @Override
    public void deleteAssetRecord(String assetId, AppPrincipalSnapshotDTO principal) {
        AssetFile asset = requireOwned(assetId, principal);
        if (assetMapper.deleteById(asset.getAssetId()) != 1) {
            throw new ServiceException("人物照片资产删除失败", ASSET_DELETE_FAILED);
        }
    }

    @Override
    public int cleanupUnboundPortraitAssets(LocalDateTime cutoff, int limit) {
        if (cutoff == null || limit <= 0) return 0;
        int safeLimit = Math.min(limit, 100);
        List<AssetFile> candidates = assetMapper.selectUnboundPortraitAssetsBefore(cutoff, safeLimit);
        if (candidates == null || candidates.isEmpty()) return 0;
        OssClient client = OssFactory.instance();
        int cleaned = 0;
        for (AssetFile candidate : candidates) {
            if (candidate == null || candidate.getAssetId() == null || candidate.getObjectKey() == null) continue;
            Long assetId = candidate.getAssetId();
            if (assetMapper.reserveUnboundPortraitAsset(assetId, cutoff) != 1) continue;
            try {
                if (!client.delete(candidate.getObjectKey())) {
                    markCleanupDeleteFailed(assetId, "人物照片文件删除失败");
                    continue;
                }
            } catch (RuntimeException exception) {
                markCleanupDeleteFailed(assetId, "人物照片文件删除异常");
                log.warn("未绑定人物照片对象删除异常，assetId={}", assetId, exception);
                continue;
            }
            if (assetMapper.logicalDeleteUnboundPortraitAsset(assetId) == 1) {
                cleaned++;
            } else {
                log.error("未绑定人物照片对象已删除但数据库收口失败，assetId={}", assetId);
            }
        }
        return cleaned;
    }

    private void markCleanupDeleteFailed(Long assetId, String reason) {
        try {
            if (assetMapper.markUnboundPortraitAssetDeleteFailed(assetId, reason) != 1) {
                log.error("未绑定人物照片删除失败状态写入未生效，assetId={}", assetId);
            }
        } catch (RuntimeException exception) {
            log.error("未绑定人物照片删除失败状态写入异常，assetId={}", assetId, exception);
        }
    }

    private void updateDeleteStatus(String assetId, String status, String failureReason,
                                    AppPrincipalSnapshotDTO principal) {
        AssetFile asset = requireOwned(assetId, principal);
        int affected = assetMapper.update(null, new LambdaUpdateWrapper<AssetFile>()
            .eq(AssetFile::getAssetId, asset.getAssetId())
            .eq(AssetFile::getOwnerId, principal.appUserId())
            .eq(AssetFile::getDelFlag, "0")
            .set(AssetFile::getStatus, status)
            .set(AssetFile::getFailureReason, failureReason)
            .set(AssetFile::getUpdateBy, principal.appUserId()));
        if (affected != 1) {
            throw new ServiceException("人物照片状态更新失败", ASSET_DELETE_FAILED);
        }
    }

    @Override
    public void tombstoneOwnedVoiceAssetAndPurgeAfterCommit(
            String voiceId, String assetId, AppPrincipalSnapshotDTO principal) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()
            || !TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("voice asset deletion requires an active transaction");
        }
        AppWorkspaceSessionSnapshotDTO workspace = requireWorkspace(principal);
        long parsedVoiceId = parseId(voiceId);
        long parsedId = parseId(assetId);
        AssetFile asset = assetMapper.selectOne(ownedVoiceAssetDeleteWrapper(parsedId, principal, workspace));
        if (asset == null) {
            throw invalidVoiceAsset();
        }
        if (assetMapper.delete(ownedVoiceAssetDeleteWrapper(parsedId, principal, workspace)) != 1) {
            throw invalidVoiceAsset();
        }
        String objectKey = asset.getObjectKey();
        Long stableVoiceId = parsedVoiceId;
        Long stableAssetId = asset.getAssetId();
        Long tenantId = workspace.tenantId();
        String workspaceId = workspace.workspaceKey();
        Long ownerId = principal.appUserId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    if (!OssFactory.instance().delete(objectKey)) {
                        log.error("voice asset object purge failed: voiceId={}, assetId={}, tenantId={}, workspaceId={}, ownerId={}, errorType={}",
                            stableVoiceId, stableAssetId, tenantId, workspaceId, ownerId, "DeleteReturnedFalse");
                    }
                } catch (RuntimeException exception) {
                    log.error("voice asset object purge failed: voiceId={}, assetId={}, tenantId={}, workspaceId={}, ownerId={}, errorType={}",
                        stableVoiceId, stableAssetId, tenantId, workspaceId, ownerId,
                        exception.getClass().getSimpleName());
                }
            }
        });
    }

    private AssetFile requireOwned(String assetId, AppPrincipalSnapshotDTO principal) {
        AppWorkspaceSessionSnapshotDTO workspace = requireWorkspace(principal);
        long parsedId = parseId(assetId);
        AssetFile asset = assetMapper.selectOne(new LambdaQueryWrapper<AssetFile>()
            .eq(AssetFile::getAssetId, parsedId)
            .eq(AssetFile::getTenantId, workspace.tenantId())
            .eq(AssetFile::getWorkspaceId, workspace.workspaceKey())
            .eq(AssetFile::getOwnerId, principal.appUserId())
            .eq(AssetFile::getDelFlag, "0"));
        if (asset == null) {
            throw invalidAsset();
        }
        return asset;
    }

    private LambdaQueryWrapper<AssetFile> ownedVoiceAssetDeleteWrapper(
            long assetId, AppPrincipalSnapshotDTO principal, AppWorkspaceSessionSnapshotDTO workspace) {
        return new LambdaQueryWrapper<AssetFile>()
            .eq(AssetFile::getAssetId, assetId)
            .eq(AssetFile::getTenantId, workspace.tenantId())
            .eq(AssetFile::getWorkspaceId, workspace.workspaceKey())
            .eq(AssetFile::getOwnerId, principal.appUserId())
            .eq(AssetFile::getCategory, VOICE_CATEGORY)
            .eq(AssetFile::getDelFlag, "0");
    }

    private AppWorkspaceSessionSnapshotDTO requireWorkspace(AppPrincipalSnapshotDTO principal) {
        if (principal == null || principal.appUserId() == null || principal.appUserId() <= 0
            || principal.workspace() == null || principal.workspace().tenantId() == null
            || principal.workspace().workspaceKey() == null || principal.workspace().workspaceKey().isBlank()) {
            throw new ServiceException("当前创作工作区不可用", 403);
        }
        return principal.workspace();
    }

    private void requirePermission(AppPrincipalSnapshotDTO principal, String permission) {
        AppWorkspaceSessionSnapshotDTO workspace = requireWorkspace(principal);
        if (!workspace.permissions().contains(permission)) {
            throw new ServiceException("无人物形象操作权限", 403);
        }
    }

    private long parseId(String id) {
        try {
            long value = Long.parseLong(id);
            if (value <= 0) throw invalidAsset();
            return value;
        } catch (NumberFormatException exception) {
            throw invalidAsset();
        }
    }

    private String safeOriginalName(String fileName) {
        return safeOriginalName(fileName, "portrait");
    }

    private String safeOriginalName(String fileName, String fallback) {
        String normalized = fileName == null ? fallback : fileName.replace('\\', '/');
        normalized = normalized.substring(normalized.lastIndexOf('/') + 1).trim();
        return normalized.isEmpty() ? fallback : normalized.substring(0, Math.min(255, normalized.length()));
    }

    private String canonicalVoiceContentType(String format) {
        return switch (format) {
            case "mp3" -> "audio/mpeg";
            case "wav" -> "audio/wav";
            case "m4a" -> "audio/mp4";
            default -> throw new ServiceException(
                "声音文件类型不支持", VoiceSampleValidator.FILE_TYPE_NOT_ALLOWED);
        };
    }

    private AssetDTO toDTO(AssetFile asset) {
        return new AssetDTO(Long.toString(asset.getAssetId()), asset.getStatus(), asset.getFailureReason(),
            asset.getOriginalName(), asset.getContentType(), asset.getFileFormat(), asset.getWidth(), asset.getHeight(),
            asset.getFileSize(), asset.getCreateTime());
    }

    private ServiceException invalidAsset() {
        return new ServiceException("图片素材不可用于创建形象", ASSET_INVALID);
    }

    private ServiceException invalidVoiceAsset() {
        return new ServiceException("声音资产不可删除", ASSET_INVALID);
    }
}
