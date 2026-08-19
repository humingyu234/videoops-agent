package org.dromara.aivideo.studio.draft.service.impl;

import org.dromara.aivideo.identity.dto.AppPrincipalSnapshotDTO;
import org.dromara.aivideo.identity.dto.AppWorkspaceSessionSnapshotDTO;
import org.dromara.aivideo.studio.draft.domain.StudioWorkflowDraft;
import org.dromara.aivideo.studio.draft.mapper.StudioWorkflowDraftMapper;
import org.dromara.aivideo.studio.draft.service.IStudioWorkflowDraftService;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("dev")
class StudioWorkflowDraftServiceImplTest {

    @Test
    void freshServiceReadsTheSameOwnerScopedDraftWithoutCreatingWork() {
        StudioWorkflowDraftMapper mapper = mock(StudioWorkflowDraftMapper.class);
        StudioWorkflowDraft stored = draft(9L, 7L, 1L, 3);
        when(mapper.selectOwned(9L, 7L)).thenReturn(null, stored);
        when(mapper.insert(any(StudioWorkflowDraft.class))).thenAnswer(invocation -> {
            StudioWorkflowDraft inserted = invocation.getArgument(0);
            assertThat(inserted.getTenantId()).isEqualTo(9L);
            assertThat(inserted.getOwnerUserId()).isEqualTo(7L);
            assertThat(inserted.getRevision()).isEqualTo(1L);
            return 1;
        });
        var first = service(mapper);
        var second = service(mapper);

        assertThat(first.save(command(0, 3), principal(7L, 9L)).revision()).isEqualTo("1");
        assertThat(second.getCurrent(principal(7L, 9L))).satisfies(result -> {
            assertThat(result.revision()).isEqualTo("1");
            assertThat(result.currentStep()).isEqualTo(3);
        });

        verify(mapper).insert(any(StudioWorkflowDraft.class));
    }

    @Test
    void staleRevisionFailsClosedAndDoesNotOverwrite() {
        StudioWorkflowDraftMapper mapper = mock(StudioWorkflowDraftMapper.class);
        when(mapper.selectOwned(9L, 7L)).thenReturn(draft(9L, 7L, 4L, 3));

        assertThatThrownBy(() -> service(mapper).save(command(3, 4), principal(7L, 9L)))
            .isInstanceOf(ServiceException.class)
            .extracting("code").isEqualTo(StudioWorkflowDraftServiceImpl.REVISION_CONFLICT);

        verify(mapper, never()).updateOwned(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void ownerAndTenantAlwaysComeFromAuthenticatedPrincipal() {
        StudioWorkflowDraftMapper mapper = mock(StudioWorkflowDraftMapper.class);
        when(mapper.selectOwned(11L, 8L)).thenReturn(draft(11L, 8L, 2L, 2));

        var result = service(mapper).getCurrent(principal(8L, 11L));

        assertThat(result.revision()).isEqualTo("2");
        verify(mapper).selectOwned(11L, 8L);
        verify(mapper, never()).selectOwned(9L, 7L);
    }

    @Test
    void rejectsNonPersonalWorkspaceAndInvalidSnapshot() {
        StudioWorkflowDraftMapper mapper = mock(StudioWorkflowDraftMapper.class);
        AppWorkspaceSessionSnapshotDTO shared = new AppWorkspaceSessionSnapshotDTO(
            "shared", "team", 9L, "organization", 99L, "organization", 99L, "member",
            Set.of("aivideo:studio:generate"), 1L, 1L);
        AppPrincipalSnapshotDTO principal = new AppPrincipalSnapshotDTO(7L, "user", "web",
            1L, 1L, 1L, 1L, shared);

        assertThatThrownBy(() -> service(mapper).save(command(0, 0), principal))
            .isInstanceOf(ServiceException.class).extracting("code").isEqualTo(403);
        assertThatThrownBy(() -> service(mapper).save(
            new IStudioWorkflowDraftService.SaveCommand(0, 0, "studio-workflow-1", "[]"),
            principal(7L, 9L)))
            .isInstanceOf(ServiceException.class).extracting("code").isEqualTo(400);
    }

    private StudioWorkflowDraftServiceImpl service(StudioWorkflowDraftMapper mapper) {
        return new StudioWorkflowDraftServiceImpl(mapper, JsonMapper.builder().build());
    }

    private IStudioWorkflowDraftService.SaveCommand command(long revision, int step) {
        return new IStudioWorkflowDraftService.SaveCommand(revision, step, "studio-workflow-1",
            "{\"schemaVersion\":\"studio-workflow-1\",\"step\":" + step + "}");
    }

    private StudioWorkflowDraft draft(long tenantId, long ownerId, long revision, int step) {
        StudioWorkflowDraft draft = new StudioWorkflowDraft();
        draft.setId(100L);
        draft.setTenantId(tenantId);
        draft.setOwnerUserId(ownerId);
        draft.setRevision(revision);
        draft.setCurrentStep(step);
        draft.setSchemaVersion("studio-workflow-1");
        draft.setSnapshotJson("{\"schemaVersion\":\"studio-workflow-1\",\"step\":" + step + "}");
        return draft;
    }

    private AppPrincipalSnapshotDTO principal(long userId, long tenantId) {
        var workspace = new AppWorkspaceSessionSnapshotDTO("personal-" + userId, "personal", tenantId,
            "app_user", userId, "app_user", userId, "creator",
            Set.of("aivideo:studio:query", "aivideo:studio:generate"), 1L, null);
        return new AppPrincipalSnapshotDTO(userId, "user", "web", 1L, 1L, 1L, 1L, workspace);
    }
}
