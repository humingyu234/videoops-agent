package org.dromara.aivideo.timeline.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.dromara.aivideo.creation.domain.CreationProject;
import org.dromara.aivideo.creation.mapper.CreationProjectMapper;
import org.dromara.aivideo.timeline.domain.TimelineAssetRef;
import org.dromara.aivideo.timeline.domain.TimelineDraft;
import org.dromara.aivideo.timeline.domain.TimelineVersion;
import org.dromara.aivideo.timeline.domain.TimelineWriteReceipt;
import org.dromara.aivideo.timeline.dto.TimelineDocumentDTO;
import org.dromara.aivideo.timeline.mapper.TimelineAssetRefMapper;
import org.dromara.aivideo.timeline.mapper.TimelineDraftMapper;
import org.dromara.aivideo.timeline.mapper.TimelineVersionMapper;
import org.dromara.aivideo.timeline.mapper.TimelineWriteReceiptMapper;
import org.dromara.aivideo.timeline.service.ITimelineDocumentService;
import org.dromara.aivideo.timeline.service.ITimelineVersionService;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("dev")
class TimelineVersionServiceImplTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Mock
    private CreationProjectMapper projectMapper;
    @Mock
    private TimelineDraftMapper draftMapper;
    @Mock
    private TimelineVersionMapper versionMapper;
    @Mock
    private TimelineAssetRefMapper assetRefMapper;
    @Mock
    private TimelineWriteReceiptMapper receiptMapper;
    @Mock
    private ITimelineDocumentService documentService;

    @Test
    void manualVersionCopiesTheCanonicalDraftAndImmutableAssetReferences() throws Exception {
        TimelineDocumentDTO timeline = timeline();
        TimelineDraft draft = draft(timeline, 3L);
        stubProjectAndDraft(draft);
        when(receiptMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(versionMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        when(versionMapper.insert(any(TimelineVersion.class))).thenReturn(1);
        when(assetRefMapper.insert(any(TimelineAssetRef.class))).thenReturn(1);
        when(receiptMapper.insert(any(TimelineWriteReceipt.class))).thenReturn(1);

        ITimelineVersionService.TimelineVersionView result = service().createManualVersion(7L, "90071992547409931",
            new ITimelineVersionService.CreateManualVersionCommand("manual-v1", "3"));

        assertThat(result.versionNo()).isEqualTo("1");
        assertThat(result.sourceDraftRevision()).isEqualTo("3");
        assertThat(result.versionReason()).isEqualTo("manual_save");
        assertThat(result.replayed()).isFalse();
        ArgumentCaptor<TimelineVersion> versionCaptor = ArgumentCaptor.forClass(TimelineVersion.class);
        verify(versionMapper).insert(versionCaptor.capture());
        assertThat(versionCaptor.getValue().getContentJson()).isEqualTo(draft.getContentJson());
        assertThat(versionCaptor.getValue().getContentHash()).isEqualTo(draft.getContentHash());
        assertThat(versionCaptor.getValue().getSourceDraftRevision()).isEqualTo(3L);
        verify(assetRefMapper, atLeastOnce()).insert(any(TimelineAssetRef.class));
        ArgumentCaptor<TimelineWriteReceipt> receiptCaptor = ArgumentCaptor.forClass(TimelineWriteReceipt.class);
        verify(receiptMapper).insert(receiptCaptor.capture());
        assertThat(receiptCaptor.getValue().getOperationType()).isEqualTo("manual_version");
    }

    @Test
    void conflictCopyRevalidatesTheFrozenLocalDocumentWithoutMutatingCurrentDraft() throws Exception {
        TimelineDocumentDTO timeline = timeline();
        when(projectMapper.selectOne(any(Wrapper.class))).thenReturn(project());
        when(receiptMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(versionMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        when(versionMapper.insert(any(TimelineVersion.class))).thenReturn(1);
        when(assetRefMapper.insert(any(TimelineAssetRef.class))).thenReturn(1);
        when(receiptMapper.insert(any(TimelineWriteReceipt.class))).thenReturn(1);
        when(documentService.validate(eq(7L), any(ITimelineDocumentService.ProjectContext.class), any(JsonNode.class)))
            .thenReturn(validated(timeline));

        ITimelineVersionService.TimelineVersionView result = service().createConflictCopy(7L, "90071992547409931",
            new ITimelineVersionService.CreateConflictCopyCommand("conflict-v1", "2", "timeline-1", timelineNode()));

        assertThat(result.sourceDraftRevision()).isEqualTo("2");
        assertThat(result.versionReason()).isEqualTo("conflict_copy");
        verify(documentService).validate(eq(7L), any(ITimelineDocumentService.ProjectContext.class), any(JsonNode.class));
        verify(draftMapper, never()).update(any(TimelineDraft.class), any(LambdaUpdateWrapper.class));
        verify(assetRefMapper, never()).delete(any(Wrapper.class));
        ArgumentCaptor<TimelineWriteReceipt> receiptCaptor = ArgumentCaptor.forClass(TimelineWriteReceipt.class);
        verify(receiptMapper).insert(receiptCaptor.capture());
        assertThat(receiptCaptor.getValue().getOperationType()).isEqualTo("conflict_version");
    }

    @Test
    void restoreCreatesANewFactAndUpdatesOnlyTheCurrentDraftProjection() throws Exception {
        TimelineDocumentDTO timeline = timeline();
        TimelineDraft draft = draft(timeline, 3L);
        TimelineVersion source = version(101L, timeline, 1L, "manual_save", null);
        stubProjectAndDraft(draft);
        when(receiptMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(versionMapper.selectOne(any(Wrapper.class))).thenReturn(source);
        when(versionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(source));
        when(documentService.validate(eq(7L), any(ITimelineDocumentService.ProjectContext.class), any(JsonNode.class)))
            .thenReturn(validated(timeline));
        when(draftMapper.update(any(TimelineDraft.class), any(LambdaUpdateWrapper.class))).thenReturn(1);
        when(assetRefMapper.insert(any(TimelineAssetRef.class))).thenReturn(1);
        when(versionMapper.insert(any(TimelineVersion.class))).thenReturn(1);
        when(receiptMapper.insert(any(TimelineWriteReceipt.class))).thenReturn(1);

        var result = service().restoreVersion(7L, "90071992547409931", "101",
            new ITimelineVersionService.RestoreTimelineVersionCommand("restore-v1", "3"));

        assertThat(result.revision()).isEqualTo("4");
        assertThat(result.replayed()).isFalse();
        ArgumentCaptor<TimelineDraft> draftCaptor = ArgumentCaptor.forClass(TimelineDraft.class);
        verify(draftMapper).update(draftCaptor.capture(), any(LambdaUpdateWrapper.class));
        assertThat(draftCaptor.getValue().getRevision()).isEqualTo(4L);
        ArgumentCaptor<TimelineVersion> versionCaptor = ArgumentCaptor.forClass(TimelineVersion.class);
        verify(versionMapper).insert(versionCaptor.capture());
        assertThat(versionCaptor.getValue().getVersionReason()).isEqualTo("restored");
        assertThat(versionCaptor.getValue().getSourceVersionId()).isEqualTo(101L);
        verify(versionMapper, never()).update(any(TimelineVersion.class), any(LambdaUpdateWrapper.class));
    }

    @Test
    void rejectsCrossOwnerVersionGuessingAndConflictingIdempotencyReuse() throws Exception {
        TimelineDocumentDTO timeline = timeline();
        TimelineDraft draft = draft(timeline, 3L);
        stubProjectAndDraft(draft);
        when(versionMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        assertThatThrownBy(() -> service().restoreVersion(7L, "90071992547409931", "101",
            new ITimelineVersionService.RestoreTimelineVersionCommand("restore-v1", "3")))
            .isInstanceOfSatisfying(ServiceException.class, exception -> assertThat(exception.getCode()).isEqualTo(46608));
        verify(draftMapper, never()).update(any(TimelineDraft.class), any(LambdaUpdateWrapper.class));

        TimelineWriteReceipt receipt = receipt("manual_version", "manual-v1", "0".repeat(64), 3L, 101L);
        when(receiptMapper.selectOne(any(Wrapper.class))).thenReturn(receipt);
        assertThatThrownBy(() -> service().createManualVersion(7L, "90071992547409931",
            new ITimelineVersionService.CreateManualVersionCommand("manual-v1", "4")))
            .isInstanceOfSatisfying(ServiceException.class, exception -> assertThat(exception.getCode()).isEqualTo(46609));
    }

    @Test
    void pagesOwnerVersionsWithTheFixedVersionOrdering() throws Exception {
        TimelineVersion item = version(101L, timeline(), 3L, "manual_save", null);
        item.setVersionNo(2L);
        Page<TimelineVersion> databasePage = new Page<>(1, 20);
        databasePage.setRecords(List.of(item));
        databasePage.setTotal(3L);
        when(projectMapper.selectOne(any(Wrapper.class))).thenReturn(project());
        when(versionMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(databasePage);

        var page = service().pageOwnedVersions(7L, "90071992547409931", new PageQuery(20, 1));

        assertThat(page.getTotal()).isEqualTo(3L);
        assertThat(page.getRows()).extracting(ITimelineVersionService.TimelineVersionView::versionId)
            .containsExactly("101");
        ArgumentCaptor<Wrapper> wrapper = ArgumentCaptor.forClass(Wrapper.class);
        verify(versionMapper).selectPage(any(Page.class), wrapper.capture());
        assertThat(wrapper.getValue().getSqlSegment())
            .contains("owner_user_id", "project_id", "ORDER BY version_no DESC,timeline_version_id DESC");
    }

    private TimelineVersionServiceImpl service() {
        return new TimelineVersionServiceImpl(projectMapper, draftMapper, versionMapper, assetRefMapper, receiptMapper,
            documentService, jsonMapper);
    }

    private void stubProjectAndDraft(TimelineDraft draft) {
        when(projectMapper.selectOne(any(Wrapper.class))).thenReturn(project());
        when(draftMapper.selectOne(any(Wrapper.class))).thenReturn(draft);
    }

    private CreationProject project() {
        CreationProject project = new CreationProject();
        project.setProjectId(90071992547409931L);
        project.setOwnerUserId(7L);
        project.setProjectStatus("editing");
        project.setBaseVideoAssetId(90071992547410003L);
        project.setPrimaryAudioAssetId(90071992547410004L);
        project.setScriptTextSnapshot("欢迎使用 AI 视频！三步做出专业视频。");
        project.setCanvasWidth(1080);
        project.setCanvasHeight(1920);
        project.setFrameRate(30);
        project.setDurationMs(30_000L);
        project.setDelFlag("0");
        return project;
    }

    private TimelineDraft draft(TimelineDocumentDTO timeline, long revision) throws Exception {
        TimelineDraft draft = new TimelineDraft();
        draft.setTimelineDraftId(901L);
        draft.setOwnerUserId(7L);
        draft.setProjectId(90071992547409931L);
        draft.setRevision(revision);
        draft.setSchemaVersion("timeline-1");
        draft.setContentJson(jsonMapper.writeValueAsString(timeline));
        draft.setContentHash("a".repeat(64));
        draft.setDurationMs(30_000L);
        draft.setDelFlag("0");
        return draft;
    }

    private TimelineVersion version(long id, TimelineDocumentDTO timeline, long sourceRevision, String reason,
                                    Long sourceVersionId) throws Exception {
        TimelineVersion version = new TimelineVersion();
        version.setTimelineVersionId(id);
        version.setOwnerUserId(7L);
        version.setProjectId(90071992547409931L);
        version.setVersionNo(1L);
        version.setSourceDraftRevision(sourceRevision);
        version.setVersionReason(reason);
        version.setIdempotencyKey("source-v1");
        version.setRequestDigest("b".repeat(64));
        version.setSchemaVersion("timeline-1");
        version.setContentJson(jsonMapper.writeValueAsString(timeline));
        version.setContentHash("a".repeat(64));
        version.setDurationMs(30_000L);
        version.setSourceVersionId(sourceVersionId);
        return version;
    }

    private TimelineWriteReceipt receipt(String operationType, String key, String digest, long resultRevision,
                                         long resultVersionId) {
        TimelineWriteReceipt receipt = new TimelineWriteReceipt();
        receipt.setTimelineWriteReceiptId(801L);
        receipt.setOwnerUserId(7L);
        receipt.setProjectId(90071992547409931L);
        receipt.setOperationType(operationType);
        receipt.setIdempotencyKey(key);
        receipt.setRequestDigest(digest);
        receipt.setExpectedRevision(resultRevision);
        receipt.setResultRevision(resultRevision);
        receipt.setResultVersionId(resultVersionId);
        receipt.setResponseSummaryJson("{\"contentHash\":\"" + "a".repeat(64) + "\"}");
        return receipt;
    }

    private ITimelineDocumentService.ValidatedTimeline validated(TimelineDocumentDTO timeline) throws Exception {
        return new ITimelineDocumentService.ValidatedTimeline(timeline, jsonMapper.writeValueAsString(timeline),
            "a".repeat(64), List.of(), List.of());
    }

    private TimelineDocumentDTO timeline() throws Exception {
        return jsonMapper.readValue(jsonMapper.writeValueAsString(timelineNode()), TimelineDocumentDTO.class);
    }

    private JsonNode timelineNode() throws Exception {
        try (InputStream input = getClass().getClassLoader()
            .getResourceAsStream("contracts/creation-timeline/timeline-draft.example.json")) {
            return jsonMapper.readTree(input).required("timeline");
        }
    }
}
