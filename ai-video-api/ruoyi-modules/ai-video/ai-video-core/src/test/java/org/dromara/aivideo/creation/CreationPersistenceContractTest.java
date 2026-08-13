package org.dromara.aivideo.creation;

import org.dromara.aivideo.creation.domain.CreationAsset;
import org.dromara.aivideo.creation.domain.CreationProject;
import org.dromara.aivideo.timeline.domain.TimelineAssetRef;
import org.dromara.aivideo.timeline.domain.TimelineDraft;
import org.dromara.aivideo.timeline.domain.TimelineVersion;
import org.dromara.aivideo.timeline.domain.TimelineWriteReceipt;
import org.dromara.aivideo.task.domain.AiTask;
import org.dromara.aivideo.task.domain.AiTaskAttempt;
import org.dromara.aivideo.task.domain.AiTaskExecution;
import org.dromara.common.mybatis.audit.AppAuditRequired;
import org.dromara.common.mybatis.core.domain.BaseEntity;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@Tag("dev")
class CreationPersistenceContractTest {
    @Test
    void allTimelineEntitiesUseBaseEntityAndAppAudit() {
        assertThat(List.of(CreationAsset.class, CreationProject.class, TimelineDraft.class,
                TimelineVersion.class, TimelineAssetRef.class, TimelineWriteReceipt.class,
                AiTask.class, AiTaskExecution.class, AiTaskAttempt.class))
            .allSatisfy(type -> {
                assertThat(type.getSuperclass()).isEqualTo(BaseEntity.class);
                assertThat(type.isAnnotationPresent(AppAuditRequired.class)).isTrue();
                assertThatCode(() -> type.getDeclaredField("ownerUserId"))
                    .doesNotThrowAnyException();
            });
    }
}
