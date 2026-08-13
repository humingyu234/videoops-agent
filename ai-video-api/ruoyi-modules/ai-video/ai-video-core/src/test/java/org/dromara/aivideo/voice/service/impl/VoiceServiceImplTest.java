package org.dromara.aivideo.voice.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.aivideo.asset.service.IAssetService;
import org.dromara.aivideo.asset.dto.AssetDTO;
import org.dromara.aivideo.identity.dto.AppPrincipalSnapshotDTO;
import org.dromara.aivideo.identity.dto.AppWorkspaceSessionSnapshotDTO;
import org.dromara.aivideo.voice.domain.Voice;
import org.dromara.aivideo.voice.dto.CreateVoiceDTO;
import org.dromara.aivideo.voice.dto.VoiceDTO;
import org.dromara.aivideo.voice.dto.VoiceTranscriptCueDTO;
import org.dromara.aivideo.voice.dto.VoiceTranscriptionLeaseDTO;
import org.dromara.aivideo.voice.dto.VoiceTranscriptionResultDTO;
import org.dromara.aivideo.voice.dto.UpdateVoiceTranscriptDTO;
import org.dromara.aivideo.voice.dto.RetryVoiceTranscriptionDTO;
import org.dromara.aivideo.voice.dto.StartVoiceTranscriptionDTO;
import org.dromara.aivideo.voice.mapper.VoiceMapper;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.SpringUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.doAnswer;

@Tag("dev")
@ExtendWith(MockitoExtension.class)
class VoiceServiceImplTest {
    private static AnnotationConfigApplicationContext applicationContext;
    @Mock private VoiceMapper voiceMapper;
    @Mock private IAssetService assetService;
    private VoiceServiceImpl service;
    private AppPrincipalSnapshotDTO principal;

    @BeforeAll
    static void setUpJsonMapper() {
        if (TableInfoHelper.getTableInfo(Voice.class) == null) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Voice.class);
        }
        applicationContext = new AnnotationConfigApplicationContext();
        applicationContext.registerBean(SpringUtils.class);
        applicationContext.registerBean(JsonMapper.class, () -> JsonMapper.builder().build());
        applicationContext.refresh();
    }

    @AfterAll
    static void closeApplicationContext() {
        applicationContext.close();
    }

    @BeforeEach
    void setUp() {
        service = new VoiceServiceImpl(voiceMapper, assetService);
        AppWorkspaceSessionSnapshotDTO workspace = new AppWorkspaceSessionSnapshotDTO(
            "personal:7", "personal", 1L, "app_user", 7L, "app_user", 7L,
            "owner", Set.of("aivideo:voice:upload", "aivideo:voice:query", "aivideo:voice:edit",
                "aivideo:voice:transcribe", "aivideo:voice:delete"), 1L, null);
        principal = new AppPrincipalSnapshotDTO(7L, "tester", "web", 1L, 1L, 1L, 1L, workspace);
    }

    @Test
    void createRejectsBlankName() {
        CreateVoiceDTO command = new CreateVoiceDTO(
            "9", "idem", "fingerprint", " ", "female", "friendly", List.of(), null, true);
        assertThatThrownBy(() -> service.create(command, principal))
            .isInstanceOf(ServiceException.class)
            .extracting("code").isEqualTo(46402);
    }

    @Test
    void createWithoutTranscriptionRequestPersistsUnparsedAndScopesIdempotencyToWorkspace() {
        when(voiceMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(assetService.requireOwnedReadyVoiceAsset("9", principal)).thenReturn(new AssetDTO(
            "9", "ready", null, "sample.wav", "audio/wav", "wav", null, null, 128L, null));
        doAnswer(invocation -> {
            Voice inserted = invocation.getArgument(0);
            inserted.setVoiceId(99L);
            return 1;
        }).when(voiceMapper).insert(any(Voice.class));

        VoiceDTO result = service.create(new CreateVoiceDTO(
            "9", "idem", "fingerprint", "sample", "female", "friendly", List.of(), null, false), principal);

        ArgumentCaptor<LambdaQueryWrapper<Voice>> queryCaptor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        ArgumentCaptor<Voice> voiceCaptor = ArgumentCaptor.forClass(Voice.class);
        verify(voiceMapper).selectOne(queryCaptor.capture());
        verify(voiceMapper).insert(voiceCaptor.capture());
        assertThat(queryCaptor.getValue().getSqlSegment()).contains("tenant_id", "workspace_id", "owner_id", "idempotency_key");
        assertThat(queryCaptor.getValue().getParamNameValuePairs().values())
            .contains(1L, "personal:7", 7L, "idem");
        assertThat(voiceCaptor.getValue().getTranscriptionStatus()).isEqualTo("unparsed");
        assertThat(voiceCaptor.getValue().getNextAttemptAt()).isNull();
        assertThat(result.transcriptionStatus()).isEqualTo("unparsed");
    }

    @Test
    void startTranscriptionConditionallyMovesOwnedOriginUnparsedVoiceToPending() {
        Voice current = voice("unparsed", 3L);
        current.setVoiceType("origin");
        Voice updated = voice("pending", 4L);
        updated.setVoiceType("origin");
        when(voiceMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(current, updated);
        when(voiceMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(1);

        VoiceDTO result = service.startTranscription(new StartVoiceTranscriptionDTO("11", "3"), principal);

        ArgumentCaptor<LambdaUpdateWrapper<Voice>> updateCaptor = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(voiceMapper).update(isNull(), updateCaptor.capture());
        assertThat(updateCaptor.getValue().getSqlSegment())
            .contains("tenant_id", "workspace_id", "owner_id", "voice_type", "transcription_status", "record_revision", "del_flag");
        assertThat(updateCaptor.getValue().getParamNameValuePairs().values())
            .contains(1L, "personal:7", 7L, "origin", "unparsed", 3L, "0");
        assertThat(updateCaptor.getValue().getSqlSet()).contains("transcription_status", "next_attempt_at", "attempt_count");
        assertThat(result.transcriptionStatus()).isEqualTo("pending");
    }

    @Test
    void queryByIdReturnsPersistedTranscriptTimeline() {
        Voice voice = voice("ready", 3L);
        voice.setTranscriptTimelineJson("[{\"text\":\"微信\",\"startMillis\":120,\"endMillis\":480}]");
        when(voiceMapper.selectOne(any())).thenReturn(voice);

        VoiceDTO result = service.queryById("11", principal);

        assertThat(result.transcriptTimeline()).containsExactly(new VoiceTranscriptCueDTO("微信", 120L, 480L));
    }

    @Test
    void completeTranscriptionPersistsTimelineJson() {
        when(voiceMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(1);
        VoiceTranscriptionLeaseDTO lease = new VoiceTranscriptionLeaseDTO(
            "11", "12", 1L, "personal:7", 7L, "11:4:1", "worker", 4L, 1);
        VoiceTranscriptionResultDTO result = new VoiceTranscriptionResultDTO(
            "11:4:1", "微信公众号", "zh", 1000L,
            List.of(new VoiceTranscriptCueDTO("微信", 120L, 480L)));

        assertThat(service.completeTranscription(lease, result)).isTrue();

        ArgumentCaptor<LambdaUpdateWrapper<Voice>> captor = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(voiceMapper).update(isNull(), captor.capture());
        assertThat(captor.getValue().getSqlSet()).contains("transcript_timeline_json");
        assertThat(captor.getValue().getParamNameValuePairs().values())
            .anyMatch(value -> value != null && value.toString().contains("\"startMillis\":120"));
    }

    @Test
    void updateTranscriptClearsExistingTimeline() {
        Voice current = voice("ready", 3L);
        current.setTranscriptTimelineJson("[{\"text\":\"旧\",\"startMillis\":0,\"endMillis\":100}]");
        Voice updated = voice("ready", 4L);
        updated.setTranscriptText("人工修改文案");
        when(voiceMapper.selectOne(any())).thenReturn(current, updated);
        when(voiceMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(1);

        VoiceDTO result = service.updateTranscript(
            new UpdateVoiceTranscriptDTO("11", "人工修改文案", "3"), principal);

        ArgumentCaptor<LambdaUpdateWrapper<Voice>> captor = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(voiceMapper).update(isNull(), captor.capture());
        assertThat(captor.getValue().getSqlSet()).contains("transcript_timeline_json");
        assertThat(result.transcriptTimeline()).isEmpty();
    }

    @Test
    void resyncReadyVoiceReturnsItToPendingAndClearsTimeline() {
        Voice current = voice("ready", 3L);
        current.setTranscriptTimelineJson("[{\"text\":\"旧\",\"startMillis\":0,\"endMillis\":100}]");
        Voice updated = voice("pending", 4L);
        when(voiceMapper.selectOne(any())).thenReturn(current, updated);
        when(voiceMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(1);

        VoiceDTO result = service.resyncTranscription(new RetryVoiceTranscriptionDTO("11", "3"), principal);

        ArgumentCaptor<LambdaUpdateWrapper<Voice>> captor = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(voiceMapper).update(isNull(), captor.capture());
        assertThat(captor.getValue().getSqlSet())
            .contains("transcription_status", "transcript_timeline_json", "attempt_count");
        assertThat(captor.getValue().getSqlSegment()).contains("record_revision");
        assertThat(result.transcriptionStatus()).isEqualTo("pending");
        assertThat(result.transcriptTimeline()).isEmpty();
    }

    @Test
    void deleteOwnedVoiceDeletesVoiceThenTombstonesItsVoiceAsset() {
        Voice existing = ownedVoice("origin", "0", 91L);
        when(voiceMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);
        when(voiceMapper.delete(any(LambdaQueryWrapper.class))).thenReturn(1);

        service.deleteOwnedVoice("42", principal);

        ArgumentCaptor<LambdaQueryWrapper<Voice>> queryCaptor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        ArgumentCaptor<LambdaQueryWrapper<Voice>> deleteCaptor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(voiceMapper).selectOne(queryCaptor.capture());
        verify(voiceMapper).delete(deleteCaptor.capture());
        assertOwnedDeletableVoiceScope(queryCaptor.getValue(), 42L);
        assertOwnedDeletableVoiceScope(deleteCaptor.getValue(), 42L);
        var ordered = inOrder(voiceMapper, assetService);
        ordered.verify(voiceMapper).delete(any(LambdaQueryWrapper.class));
        ordered.verify(assetService).tombstoneOwnedVoiceAssetAndPurgeAfterCommit("42", "91", principal);
    }

    @Test
    void deleteOwnedVoiceDoesNotTouchMapperWithoutDeletePermission() {
        AppWorkspaceSessionSnapshotDTO workspace = new AppWorkspaceSessionSnapshotDTO(
            "personal:7", "personal", 1L, "app_user", 7L, "app_user", 7L,
            "owner", Set.of("aivideo:voice:query"), 1L, null);
        AppPrincipalSnapshotDTO withoutDelete = new AppPrincipalSnapshotDTO(
            7L, "tester", "web", 1L, 1L, 1L, 1L, workspace);

        assertThatThrownBy(() -> service.deleteOwnedVoice("42", withoutDelete))
            .isInstanceOf(ServiceException.class)
            .extracting("code").isEqualTo(403);
        verifyNoInteractions(voiceMapper, assetService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "abc", "9223372036854775808"})
    void deleteOwnedVoiceMasksInvalidIdsAsNotFound(String voiceId) {
        assertThatThrownBy(() -> service.deleteOwnedVoice(voiceId, principal))
            .isInstanceOf(ServiceException.class)
            .extracting("code").isEqualTo(46401);
        verifyNoInteractions(voiceMapper, assetService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"public", "missing", "cross-owner", "cross-workspace"})
    void deleteOwnedVoiceMasksInvisibleRowsAsNotFound(String scenario) {
        when(voiceMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        assertThatThrownBy(() -> service.deleteOwnedVoice("42", principal))
            .isInstanceOf(ServiceException.class)
            .extracting("code").isEqualTo(46401);
        verify(voiceMapper, never()).delete(any(LambdaQueryWrapper.class));
        verifyNoInteractions(assetService);
    }

    @Test
    void deleteOwnedVoiceReturnsNotFoundWhenConditionalDeleteLosesRace() {
        when(voiceMapper.selectOne(any(LambdaQueryWrapper.class)))
            .thenReturn(ownedVoice("clone", "0", 91L));
        when(voiceMapper.delete(any(LambdaQueryWrapper.class))).thenReturn(0);

        assertThatThrownBy(() -> service.deleteOwnedVoice("42", principal))
            .isInstanceOf(ServiceException.class)
            .extracting("code").isEqualTo(46401);
        verifyNoInteractions(assetService);
    }

    @Test
    void claimNextScopesCandidateQueryAndLeaseUpdateToUndeletedRows() {
        Voice candidate = ownedVoice("origin", "0", 91L);
        candidate.setTranscriptionStatus("pending");
        candidate.setAttemptCount(0);
        candidate.setRecordRevision(3L);
        when(voiceMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(candidate);
        when(voiceMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(1);

        VoiceTranscriptionLeaseDTO lease = service.claimNext("worker", java.time.Instant.now());

        assertThat(lease).isNotNull();
        ArgumentCaptor<LambdaQueryWrapper<Voice>> queryCaptor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        ArgumentCaptor<LambdaUpdateWrapper<Voice>> updateCaptor = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(voiceMapper).selectOne(queryCaptor.capture());
        verify(voiceMapper).update(isNull(), updateCaptor.capture());
        assertThat(queryCaptor.getValue().getSqlSegment().toLowerCase()).contains("del_flag");
        assertThat(queryCaptor.getValue().getParamNameValuePairs().values()).contains("0");
        assertThat(updateCaptor.getValue().getSqlSegment().toLowerCase()).contains("del_flag");
        assertThat(updateCaptor.getValue().getParamNameValuePairs().values()).contains("0");
    }

    private void assertOwnedDeletableVoiceScope(LambdaQueryWrapper<Voice> wrapper, long voiceId) {
        String sql = wrapper.getSqlSegment().toLowerCase();
        assertThat(sql).contains("voice_id", "tenant_id", "workspace_id", "owner_id", "del_flag", "voice_type", " in ");
        assertThat(wrapper.getParamNameValuePairs().values())
            .contains(voiceId, 1L, "personal:7", 7L, "0", "origin", "clone");
    }

    private Voice ownedVoice(String voiceType, String delFlag, long assetId) {
        Voice voice = voice("pending", 3L);
        voice.setVoiceId(42L);
        voice.setAssetId(assetId);
        voice.setVoiceType(voiceType);
        voice.setDelFlag(delFlag);
        return voice;
    }

    private Voice voice(String status, long revision) {
        Voice voice = new Voice();
        voice.setVoiceId(11L);
        voice.setAssetId(12L);
        voice.setTenantId(1L);
        voice.setWorkspaceId("personal:7");
        voice.setOwnerId(7L);
        voice.setRecordRevision(revision);
        voice.setTagsJson("[]");
        voice.setTranscriptionStatus(status);
        return voice;
    }
}
