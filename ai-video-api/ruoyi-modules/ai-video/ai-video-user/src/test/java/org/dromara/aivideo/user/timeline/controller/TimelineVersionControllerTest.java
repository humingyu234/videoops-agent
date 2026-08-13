package org.dromara.aivideo.user.timeline.controller;

import org.dromara.aivideo.identity.dto.AppPrincipalSnapshotDTO;
import org.dromara.aivideo.identity.security.AppLoginHelper;
import org.dromara.aivideo.identity.security.AppLoginUser;
import org.dromara.aivideo.timeline.service.ITimelineVersionService;
import org.dromara.aivideo.user.timeline.domain.bo.CreateTimelineVersionBo;
import org.dromara.aivideo.user.timeline.domain.bo.RestoreTimelineVersionBo;
import org.dromara.aivideo.user.timeline.domain.bo.TimelineVersionQueryBo;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("dev")
class TimelineVersionControllerTest {

    @Test
    void versionRoutesUseImmutableResourceNamesAndSafeMutationLogs() throws Exception {
        var list = TimelineVersionController.class.getDeclaredMethod("list", String.class, TimelineVersionQueryBo.class);
        assertThat(list.getAnnotation(GetMapping.class).value()).containsExactly("/{projectId}/timeline-versions");
        assertThat(list.getAnnotation(Log.class)).isNull();

        var create = TimelineVersionController.class.getDeclaredMethod("create", String.class, CreateTimelineVersionBo.class);
        assertThat(create.getAnnotation(PostMapping.class).value()).containsExactly("/{projectId}/timeline-versions");
        assertThat(create.getAnnotation(Log.class).isSaveRequestData()).isFalse();
        assertThat(create.getAnnotation(Log.class).isSaveResponseData()).isFalse();

        var restore = TimelineVersionController.class.getDeclaredMethod("restore", String.class, String.class,
            RestoreTimelineVersionBo.class);
        assertThat(restore.getAnnotation(PostMapping.class).value())
            .containsExactly("/{projectId}/timeline-versions/{versionId}/restorations");
    }

    @Test
    void listsOnlyVersionsReturnedByTheOwnerScopedServiceUsingFixedDefaults() {
        ITimelineVersionService service = mock(ITimelineVersionService.class);
        when(service.pageOwnedVersions(eq(7L), eq("88"), org.mockito.ArgumentMatchers.any(PageQuery.class)))
            .thenReturn(PageResult.build(List.of(version()), 1));

        var response = new TimelineVersionController(service, login(7L)).list("88", null);

        assertThat(response.getData().getRows()).hasSize(1);
        assertThat(response.getData().getRows().iterator().next().versionId()).isEqualTo("99");
        org.mockito.ArgumentCaptor<PageQuery> page = org.mockito.ArgumentCaptor.forClass(PageQuery.class);
        verify(service).pageOwnedVersions(eq(7L), eq("88"), page.capture());
        assertThat(page.getValue().getPageNum()).isEqualTo(1);
        assertThat(page.getValue().getPageSize()).isEqualTo(20);
    }

    private AppLoginHelper login(long actorId) {
        AppLoginHelper helper = mock(AppLoginHelper.class);
        AppPrincipalSnapshotDTO principal = mock(AppPrincipalSnapshotDTO.class);
        when(principal.appUserId()).thenReturn(actorId);
        when(helper.getLoginUser()).thenReturn(new AppLoginUser(principal, "session"));
        return helper;
    }

    private ITimelineVersionService.TimelineVersionView version() {
        return new ITimelineVersionService.TimelineVersionView("99", "88", "1", "3", "timeline-1",
            "a".repeat(64), "manual_save", null, Instant.EPOCH, false);
    }
}
