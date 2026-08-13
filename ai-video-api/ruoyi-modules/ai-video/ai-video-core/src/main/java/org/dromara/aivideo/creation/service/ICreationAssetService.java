package org.dromara.aivideo.creation.service;

import org.dromara.aivideo.creation.dto.CreationAssetDTO;
import org.dromara.aivideo.creation.dto.CreationAssetQueryDTO;
import org.dromara.aivideo.creation.dto.CreationAssetResolveDTO;
import org.dromara.aivideo.creation.dto.CreationAssetUploadDTO;
import org.dromara.aivideo.creation.dto.DigitalHumanCreationSourceDTO;
import org.dromara.aivideo.creation.dto.PendingRenderOutputDTO;
import org.dromara.aivideo.creation.dto.RegisterPendingRenderOutputDTO;
import org.dromara.aivideo.creation.dto.RenderOutputFailureDTO;
import org.dromara.aivideo.creation.dto.RenderOutputReadyDTO;
import org.dromara.aivideo.timeline.enums.TimelineAssetUsageType;
import org.dromara.aivideo.timeline.service.TimelineRenderOutputHandle;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.mybatis.core.page.PageQuery;

import java.io.InputStream;
import java.time.Instant;
import java.util.List;

public interface ICreationAssetService {
    CreationAssetDTO uploadOwned(long actorId, CreationAssetUploadDTO command, InputStream input);
    PageResult<CreationAssetDTO> pageOwned(long actorId, CreationAssetQueryDTO query, PageQuery pageQuery);
    CreationAssetDTO getOwned(long actorId, String assetId);
    CreationAssetResolveDTO resolveOwned(long actorId, String assetId, TimelineAssetUsageType usageType);
    CreationMediaHandle openOwnedMedia(long actorId, String assetId, TimelineAssetUsageType usageType);
    CreationMediaHandle openOwnedMediaRange(long actorId, String assetId, String singleRangeHeader);
    DigitalHumanCreationSourceDTO resolveDigitalHumanSource(long actorId, String sourceId);
    PendingRenderOutputDTO registerPendingRenderOutput(long actorId, RegisterPendingRenderOutputDTO command);
    RenderOutputReadyDTO storePendingRenderContent(
        long actorId, String assetId, TimelineRenderOutputHandle output);
    void markPendingRenderFailed(long actorId, RenderOutputFailureDTO command);
    void assertAssetDeletable(long actorId, String assetId);
    void deleteOwned(long actorId, String assetId);
    List<PendingRenderOutputDTO> findCompensatablePending(Instant olderThan, int limit);
}
