package org.dromara.aivideo.timeline.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.dromara.aivideo.creation.domain.CreationProject;
import org.dromara.aivideo.creation.mapper.CreationProjectMapper;
import org.dromara.aivideo.timeline.domain.TimelineAssetRef;
import org.dromara.aivideo.timeline.domain.TimelineDraft;
import org.dromara.aivideo.timeline.domain.TimelineWriteReceipt;
import org.dromara.aivideo.timeline.dto.TimelineCanvasDTO;
import org.dromara.aivideo.timeline.dto.TimelineDocumentDTO;
import org.dromara.aivideo.timeline.dto.TimelineMainVideoElementDTO;
import org.dromara.aivideo.timeline.dto.TimelineTrackDTO;
import org.dromara.aivideo.timeline.enums.TimelineElementType;
import org.dromara.aivideo.timeline.enums.TimelineFitMode;
import org.dromara.aivideo.timeline.enums.TimelineTrackArea;
import org.dromara.aivideo.timeline.enums.TimelineTrackType;
import org.dromara.aivideo.timeline.mapper.TimelineAssetRefMapper;
import org.dromara.aivideo.timeline.mapper.TimelineDraftMapper;
import org.dromara.aivideo.timeline.mapper.TimelineWriteReceiptMapper;
import org.dromara.aivideo.timeline.service.ITimelineDocumentService;
import org.dromara.aivideo.timeline.service.ITimelineDraftService;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("dev")
class TimelineDraftServiceImplTest {

    private static final String NEW_HASH = "a".repeat(64);
    private static final String ADVANCED_HASH = "b".repeat(64);
    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    @Mock
    private CreationProjectMapper projectMapper;
    @Mock
    private TimelineDraftMapper draftMapper;
    @Mock
    private TimelineAssetRefMapper assetRefMapper;
    @Mock
    private TimelineWriteReceiptMapper receiptMapper;
    @Mock
    private ITimelineDocumentService documentService;

    @Test
    void rejectsMismatchedExpectedRevisionBeforeChangingDraftReferencesOrReceipts() {
        when(projectMapper.selectOne(any(Wrapper.class))).thenReturn(project());
        when(draftMapper.selectOne(any(Wrapper.class))).thenReturn(draft(2L, "old"));

        assertThatThrownBy(() -> service().save(7L, "101", command("save-1", "1")))
            .isInstanceOfSatisfying(ServiceException.class, exception -> assertThat(exception.getCode()).isEqualTo(46603));

        verify(documentService, never()).validate(anyLong(), any(), any(JsonNode.class));
        verify(assetRefMapper, never()).insert(any(TimelineAssetRef.class));
        verify(receiptMapper, never()).insert(any(TimelineWriteReceipt.class));
    }

    @Test
    void savesTheCanonicalDraftRebuildsReferencesAndWritesOneReceipt() {
        TimelineDraft current = draft(1L, "old");
        when(projectMapper.selectOne(any(Wrapper.class))).thenReturn(project());
        when(draftMapper.selectOne(any(Wrapper.class))).thenReturn(current);
        when(documentService.validate(anyLong(), any(), any(JsonNode.class))).thenReturn(validated(NEW_HASH));
        when(draftMapper.update(any(TimelineDraft.class), any(Wrapper.class))).thenReturn(1);
        when(assetRefMapper.insert(any(TimelineAssetRef.class))).thenReturn(1);
        when(receiptMapper.insert(any(TimelineWriteReceipt.class))).thenReturn(1);

        ITimelineDraftService.TimelineWriteResult result = service().save(7L, "101", command("save-1", "1"));

        ArgumentCaptor<TimelineDraft> draftCaptor = ArgumentCaptor.forClass(TimelineDraft.class);
        ArgumentCaptor<TimelineWriteReceipt> receiptCaptor = ArgumentCaptor.forClass(TimelineWriteReceipt.class);
        verify(draftMapper).update(draftCaptor.capture(), any(Wrapper.class));
        verify(assetRefMapper).delete(any(Wrapper.class));
        verify(assetRefMapper).insert(any(TimelineAssetRef.class));
        verify(receiptMapper).insert(receiptCaptor.capture());
        assertThat(draftCaptor.getValue().getRevision()).isEqualTo(2L);
        assertThat(draftCaptor.getValue().getContentHash()).isEqualTo(NEW_HASH);
        assertThat(receiptCaptor.getValue().getOperationType()).isEqualTo("draft_save");
        assertThat(result.replayed()).isFalse();
        assertThat(result.superseded()).isFalse();
        assertThat(result.timeline()).isNotNull();
    }

    @Test
    void replaysASupersededReceiptWithoutReturningItsOldTimeline() {
        TimelineDraft original = draft(1L, "old");
        when(projectMapper.selectOne(any(Wrapper.class))).thenReturn(project());
        when(draftMapper.selectOne(any(Wrapper.class))).thenReturn(original);
        when(documentService.validate(anyLong(), any(), any(JsonNode.class))).thenReturn(validated(NEW_HASH));
        when(draftMapper.update(any(TimelineDraft.class), any(Wrapper.class))).thenReturn(1);
        when(assetRefMapper.insert(any(TimelineAssetRef.class))).thenReturn(1);
        when(receiptMapper.insert(any(TimelineWriteReceipt.class))).thenReturn(1);
        ITimelineDraftService.TimelineWriteResult first = service().save(7L, "101", command("save-1", "1"));
        ArgumentCaptor<TimelineWriteReceipt> receiptCaptor = ArgumentCaptor.forClass(TimelineWriteReceipt.class);
        verify(receiptMapper).insert(receiptCaptor.capture());

        TimelineDraft advanced = draft(3L, ADVANCED_HASH);
        reset(draftMapper, assetRefMapper, receiptMapper);
        when(draftMapper.selectOne(any(Wrapper.class))).thenReturn(advanced);
        when(receiptMapper.selectOne(any(Wrapper.class))).thenReturn(receiptCaptor.getValue());
        when(documentService.validate(anyLong(), any(), any(JsonNode.class))).thenReturn(validated(NEW_HASH));

        ITimelineDraftService.TimelineWriteResult replay = service().save(7L, "101", command("save-1", "1"));

        assertThat(first.revision()).isEqualTo("2");
        assertThat(replay.replayed()).isTrue();
        assertThat(replay.superseded()).isTrue();
        assertThat(replay.timeline()).isNull();
        assertThat(replay.operationResultRevision()).isEqualTo("2");
        assertThat(replay.currentRevision()).isEqualTo("3");
    }

    @Test
    void rejectsSameWriteKeyWithADifferentCanonicalRequest() {
        TimelineWriteReceipt receipt = new TimelineWriteReceipt();
        receipt.setRequestDigest("f".repeat(64));
        receipt.setResultRevision(2L);
        receipt.setResponseSummaryJson("{\"contentHash\":\"old\"}");
        when(projectMapper.selectOne(any(Wrapper.class))).thenReturn(project());
        when(draftMapper.selectOne(any(Wrapper.class))).thenReturn(draft(1L, "old"));
        when(documentService.validate(anyLong(), any(), any(JsonNode.class))).thenReturn(validated(NEW_HASH));
        when(receiptMapper.selectOne(any(Wrapper.class))).thenReturn(receipt);

        assertThatThrownBy(() -> service().save(7L, "101", command("save-1", "1")))
            .isInstanceOfSatisfying(ServiceException.class, exception -> assertThat(exception.getCode()).isEqualTo(46609));
        verify(draftMapper, never()).update(any(TimelineDraft.class), any(Wrapper.class));
    }

    private TimelineDraftServiceImpl service() {
        return new TimelineDraftServiceImpl(projectMapper, draftMapper, assetRefMapper, receiptMapper,
            documentService, jsonMapper);
    }

    private ITimelineDraftService.SaveTimelineDraftCommand command(String idempotencyKey, String expectedRevision) {
        return new ITimelineDraftService.SaveTimelineDraftCommand(idempotencyKey, expectedRevision, "timeline-1",
            jsonMapper.valueToTree(timeline()));
    }

    private ITimelineDocumentService.ValidatedTimeline validated(String contentHash) {
        TimelineDocumentDTO timeline = timeline();
        return new ITimelineDocumentService.ValidatedTimeline(timeline, jsonMapper.writeValueAsString(timeline),
            contentHash, List.of(), List.of());
    }

    private TimelineDocumentDTO timeline() {
        return new TimelineDocumentDTO("timeline-1", new TimelineCanvasDTO(1080, 1920, 30, 1_000L,
            new BigDecimal("0.05")), List.of(new TimelineTrackDTO("main", TimelineTrackType.MAIN_VIDEO,
            TimelineTrackArea.CENTER, 0, true, false, List.of(new TimelineMainVideoElementDTO("main-video",
                TimelineElementType.MAIN_VIDEO, 0L, 1_000L, 0, true, true, "main", "501", 1_000L, 0L,
                TimelineFitMode.COVER)))));
    }

    private CreationProject project() {
        CreationProject project = new CreationProject();
        project.setProjectId(101L);
        project.setOwnerUserId(7L);
        project.setBaseVideoAssetId(501L);
        project.setScriptTextSnapshot("script");
        project.setCanvasWidth(1080);
        project.setCanvasHeight(1920);
        project.setFrameRate(30);
        project.setDurationMs(1_000L);
        project.setProjectStatus("editing");
        project.setDelFlag("0");
        return project;
    }

    private TimelineDraft draft(long revision, String contentHash) {
        TimelineDraft draft = new TimelineDraft();
        draft.setTimelineDraftId(201L);
        draft.setOwnerUserId(7L);
        draft.setProjectId(101L);
        draft.setRevision(revision);
        draft.setSchemaVersion("timeline-1");
        draft.setContentJson(jsonMapper.writeValueAsString(timeline()));
        draft.setContentHash(contentHash);
        draft.setDurationMs(1_000L);
        draft.setDelFlag("0");
        return draft;
    }
}
