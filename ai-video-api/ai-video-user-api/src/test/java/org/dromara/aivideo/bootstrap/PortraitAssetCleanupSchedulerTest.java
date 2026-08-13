package org.dromara.aivideo.bootstrap;

import org.dromara.DromaraApplication;
import org.dromara.aivideo.asset.service.IAssetService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@Tag("dev")
class PortraitAssetCleanupSchedulerTest {

    @Test
    void schedulesOneHundredItemCleanupWithTwentyFourHourCutoff() throws Exception {
        IAssetService assetService = mock(IAssetService.class);
        PortraitAssetCleanupScheduler scheduler = new PortraitAssetCleanupScheduler(assetService);
        LocalDateTime before = LocalDateTime.now().minusHours(24).minusSeconds(1);

        scheduler.cleanup();

        ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(assetService).cleanupUnboundPortraitAssets(cutoff.capture(), org.mockito.ArgumentMatchers.eq(100));
        assertThat(cutoff.getValue()).isAfter(before)
            .isBefore(LocalDateTime.now().minusHours(24).plusSeconds(1));
        assertThat(PortraitAssetCleanupScheduler.class.getMethod("cleanup")
            .isAnnotationPresent(Scheduled.class)).isTrue();
        assertThat(DromaraApplication.class.isAnnotationPresent(EnableScheduling.class)).isTrue();
    }
}
