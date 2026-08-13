package org.dromara.aivideo.digitalhuman.service.impl;

import lombok.RequiredArgsConstructor;
import org.dromara.aivideo.asset.dto.AssetDTO;
import org.dromara.aivideo.asset.service.IAssetService;
import org.dromara.aivideo.digitalhuman.dto.CreateDigitalHumanVideoByResourceDTO;
import org.dromara.aivideo.digitalhuman.dto.CreateDigitalHumanVideoJobDTO;
import org.dromara.aivideo.digitalhuman.dto.CreateVoiceGenerationByResourceDTO;
import org.dromara.aivideo.digitalhuman.dto.CreateVoiceGenerationJobDTO;
import org.dromara.aivideo.digitalhuman.dto.DigitalHumanJobDTO;
import org.dromara.aivideo.digitalhuman.dto.DigitalHumanOwnerDTO;
import org.dromara.aivideo.digitalhuman.service.IDigitalHumanGenerationService;
import org.dromara.aivideo.digitalhuman.service.IDigitalHumanResourceGenerationService;
import org.dromara.aivideo.identity.dto.AppPrincipalSnapshotDTO;
import org.dromara.aivideo.identity.dto.AppWorkspaceSessionSnapshotDTO;
import org.dromara.aivideo.identity.security.ConditionalOnAppSecurityEnabled;
import org.dromara.aivideo.portrait.dto.PortraitDTO;
import org.dromara.aivideo.portrait.service.IPortraitService;
import org.dromara.aivideo.voice.dto.VoiceDTO;
import org.dromara.aivideo.voice.service.IVoiceService;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;

@Service
@ConditionalOnAppSecurityEnabled
@RequiredArgsConstructor
public class DigitalHumanResourceGenerationServiceImpl implements IDigitalHumanResourceGenerationService {
    private final IDigitalHumanGenerationService generationService;
    private final IVoiceService voiceService;
    private final IPortraitService portraitService;
    private final IAssetService assetService;

    @Override
    public DigitalHumanJobDTO createVoiceJob(CreateVoiceGenerationByResourceDTO request) {
        if (request == null) throw new ServiceException("声音生成参数不能为空");
        AppPrincipalSnapshotDTO principal = requirePrincipal(request.principal());
        VoiceDTO voice = voiceService.queryById(request.referenceVoiceId(), principal);
        if (!"origin".equals(voice.voiceType())) {
            throw new ServiceException("参考声音必须为原声音");
        }
        return assetService.readOwnedVoiceAsset(voice.assetId(), principal, (asset, input) ->
            generationService.createVoiceJob(new CreateVoiceGenerationJobDTO(
                owner(principal), request.idempotencyKey(), request.scriptText(),
                asset.originalName(), asset.contentType(), readBytes(input, "参考声音读取失败"))));
    }

    @Override
    public DigitalHumanJobDTO createVideoJob(CreateDigitalHumanVideoByResourceDTO request) {
        if (request == null) throw new ServiceException("视频生成参数不能为空");
        AppPrincipalSnapshotDTO principal = requirePrincipal(request.principal());
        PortraitDTO portrait = portraitService.queryById(request.portraitId(), principal);
        if (!"ready".equals(portrait.availabilityStatus())) {
            throw new ServiceException("人物形象当前不可用");
        }
        return assetService.readOwnedPortraitAsset(portrait.assetId(), principal, (asset, input) ->
            generationService.createVideoJob(new CreateDigitalHumanVideoJobDTO(
                owner(principal), request.idempotencyKey(), request.voiceJobId(),
                asset.originalName(), asset.contentType(), readBytes(input, "人物形象读取失败"))));
    }

    private AppPrincipalSnapshotDTO requirePrincipal(AppPrincipalSnapshotDTO principal) {
        if (principal == null || principal.appUserId() == null || principal.appUserId() <= 0) {
            throw new ServiceException("登录用户不能为空");
        }
        AppWorkspaceSessionSnapshotDTO workspace = principal.workspace();
        if (workspace == null || workspace.tenantId() == null || workspace.tenantId() <= 0
            || workspace.workspaceKey() == null || workspace.workspaceKey().isBlank()) {
            throw new ServiceException("当前工作区无效");
        }
        return principal;
    }

    private DigitalHumanOwnerDTO owner(AppPrincipalSnapshotDTO principal) {
        return new DigitalHumanOwnerDTO(principal.workspace().tenantId(), principal.appUserId());
    }

    private byte[] readBytes(InputStream input, String message) {
        try {
            return input.readAllBytes();
        } catch (IOException exception) {
            throw new ServiceException(message, exception);
        }
    }
}
