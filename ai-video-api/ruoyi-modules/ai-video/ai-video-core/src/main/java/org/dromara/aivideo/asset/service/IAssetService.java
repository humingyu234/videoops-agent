package org.dromara.aivideo.asset.service;

import org.dromara.aivideo.asset.dto.AssetAccessUrlDTO;
import org.dromara.aivideo.asset.dto.AssetDTO;
import org.dromara.aivideo.asset.dto.UploadPortraitImageDTO;
import org.dromara.aivideo.asset.dto.UploadVoiceSampleDTO;
import org.dromara.aivideo.identity.dto.AppPrincipalSnapshotDTO;

import java.time.LocalDateTime;

/** 用户端私有文件资产服务。 */
public interface IAssetService {
    AssetDTO uploadPortraitImage(UploadPortraitImageDTO command, AppPrincipalSnapshotDTO principal);
    AssetDTO requireOwnedReadyPortraitAsset(String assetId, AppPrincipalSnapshotDTO principal);
    AssetDTO requireOwnedPortraitAsset(String assetId, AppPrincipalSnapshotDTO principal);
    AssetDTO uploadVoiceSample(UploadVoiceSampleDTO command, AppPrincipalSnapshotDTO principal);
    AssetDTO requireOwnedReadyVoiceAsset(String assetId, AppPrincipalSnapshotDTO principal);
    <T> T readOwnedVoiceAsset(String assetId, AppPrincipalSnapshotDTO principal, VoiceAssetReader<T> reader);
    <T> T readOwnedPortraitAsset(String assetId, AppPrincipalSnapshotDTO principal, PortraitAssetReader<T> reader);
    String createVoiceAccessUrl(String assetId, AppPrincipalSnapshotDTO principal);
    AssetAccessUrlDTO createPortraitAccessUrl(String assetId, AppPrincipalSnapshotDTO principal);
    AssetAccessUrlDTO createWorkflowAccessUrl(String assetId, AppPrincipalSnapshotDTO principal);
    void deleteOwnedAsset(String assetId, AppPrincipalSnapshotDTO principal);
    void markDeletePending(String assetId, AppPrincipalSnapshotDTO principal);
    void markDeleteFailed(String assetId, String reason, AppPrincipalSnapshotDTO principal);
    void deleteObject(String assetId, AppPrincipalSnapshotDTO principal);
    void deleteAssetRecord(String assetId, AppPrincipalSnapshotDTO principal);
    int cleanupUnboundPortraitAssets(LocalDateTime cutoff, int limit);
    void tombstoneOwnedVoiceAssetAndPurgeAfterCommit(
        String voiceId, String assetId, AppPrincipalSnapshotDTO principal);
}
