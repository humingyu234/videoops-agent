package org.dromara.aivideo.bootstrap;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.aivideo.asset.service.IAssetService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** 清理超过保留期且未绑定人物形象的私有上传素材。 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PortraitAssetCleanupScheduler {
    private static final int BATCH_SIZE = 100;
    private final IAssetService assetService;

    @Scheduled(initialDelayString = "PT10M", fixedDelayString = "PT1H")
    public void cleanup() {
        int cleaned = assetService.cleanupUnboundPortraitAssets(LocalDateTime.now().minusHours(24), BATCH_SIZE);
        if (cleaned > 0) log.info("未绑定人物照片清理完成，count={}", cleaned);
    }
}
