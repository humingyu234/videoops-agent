package org.dromara.aivideo.user.timeline.controller;

import org.dromara.aivideo.identity.dto.AppPrincipalSnapshotDTO;
import org.dromara.aivideo.identity.security.AppLoginHelper;
import org.dromara.aivideo.identity.security.AppLoginUser;
import org.dromara.aivideo.timeline.dto.TimelineDocumentDTO;
import org.dromara.aivideo.timeline.service.ITimelineDraftService;
import org.dromara.aivideo.user.timeline.domain.bo.SaveTimelineDraftBo;
import org.dromara.common.log.annotation.Log;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("dev")
class TimelineDraftControllerTest {

    @Test
    void readsAndSavesTheOwnerScopedDraftWithNoRawRequestLogging() throws Exception {
        var get = TimelineDraftController.class.getDeclaredMethod("get", String.class);
        assertThat(get.getAnnotation(GetMapping.class).value()).containsExactly("/{projectId}/timeline-draft");
        assertThat(get.getAnnotation(Log.class)).isNull();

        var save = TimelineDraftController.class.getDeclaredMethod("save", String.class, SaveTimelineDraftBo.class);
        assertThat(save.getAnnotation(PutMapping.class).value()).containsExactly("/{projectId}/timeline-draft");
        assertThat(save.getAnnotation(Log.class).isSaveRequestData()).isFalse();
        assertThat(save.getAnnotation(Log.class).isSaveResponseData()).isFalse();
    }

    @Test
    void savesOnlyTheTimelineAllowlistForTheAuthenticatedActor() throws Exception {
        ITimelineDraftService service = mock(ITimelineDraftService.class);
        JsonMapper mapper = JsonMapper.builder().build();
        SaveTimelineDraftBo body = new SaveTimelineDraftBo();
        body.setIdempotencyKey("draft-key");
        body.setExpectedRevision("3");
        body.setSchemaVersion("timeline-1");
        body.setTimeline(mapper.readTree("{\"schemaVersion\":\"timeline-1\",\"tracks\":[]}"));
        when(service.save(any(Long.class), any(String.class), any(ITimelineDraftService.SaveTimelineDraftCommand.class)))
            .thenReturn(writeResult());

        new TimelineDraftController(service, login(7L)).save("88", body);

        verify(service).save(eq(7L), eq("88"), any(ITimelineDraftService.SaveTimelineDraftCommand.class));
    }

    @Test
    void strictDraftRequestRejectsUnrecognizedProperties() {
        assertThatThrownBy(() -> JsonMapper.builder().build().readValue("""
            {"idempotencyKey":"draft-key","expectedRevision":"3","schemaVersion":"timeline-1",
             "timeline":{"schemaVersion":"timeline-1","tracks":[]},"storageKey":"forged"}
            """, SaveTimelineDraftBo.class)).isInstanceOf(Exception.class);
    }

    private AppLoginHelper login(long actorId) {
        AppLoginHelper helper = mock(AppLoginHelper.class);
        AppPrincipalSnapshotDTO principal = mock(AppPrincipalSnapshotDTO.class);
        when(principal.appUserId()).thenReturn(actorId);
        when(helper.getLoginUser()).thenReturn(new AppLoginUser(principal, "session"));
        return helper;
    }

    private ITimelineDraftService.TimelineWriteResult writeResult() {
        TimelineDocumentDTO timeline = new TimelineDocumentDTO("timeline-1", null, List.of());
        return new ITimelineDraftService.TimelineWriteResult("88", "99", "4", "timeline-1", "a".repeat(64),
            timeline, Instant.EPOCH, false, false, null, null, null, List.of());
    }
}
