package org.dromara.aivideo.digitalhuman.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.aivideo.digitalhuman.domain.DigitalHumanGenerationJob;
import org.dromara.aivideo.digitalhuman.domain.DigitalHumanJobStatus;
import org.dromara.aivideo.digitalhuman.domain.DigitalHumanJobType;
import org.dromara.aivideo.digitalhuman.dto.CreateDigitalHumanVideoJobDTO;
import org.dromara.aivideo.digitalhuman.dto.CreateVoiceGenerationJobDTO;
import org.dromara.aivideo.digitalhuman.dto.DigitalHumanJobDTO;
import org.dromara.aivideo.digitalhuman.dto.DigitalHumanMediaContentDTO;
import org.dromara.aivideo.digitalhuman.dto.DigitalHumanOwnerDTO;
import org.dromara.aivideo.digitalhuman.dto.DigitalHumanStoredMediaDTO;
import org.dromara.aivideo.digitalhuman.dto.DigitalHumanVideoPollDTO;
import org.dromara.aivideo.digitalhuman.dto.DigitalHumanVideoProviderStatus;
import org.dromara.aivideo.digitalhuman.dto.DigitalHumanVideoSubmitDTO;
import org.dromara.aivideo.digitalhuman.dto.VoiceSynthesisRequestDTO;
import org.dromara.aivideo.digitalhuman.dto.VoiceSynthesisResultDTO;
import org.dromara.aivideo.digitalhuman.mapper.DigitalHumanGenerationJobMapper;
import org.dromara.aivideo.digitalhuman.service.IDigitalHumanMediaStorageService;
import org.dromara.aivideo.digitalhuman.service.IDigitalHumanVideoService;
import org.dromara.aivideo.digitalhuman.service.IVoiceSynthesisService;
import org.dromara.aivideo.identity.security.ConditionalOnAppSecurityEnabled;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@Tag("dev")
class DigitalHumanGenerationServiceImplTest {

    private static final DigitalHumanOwnerDTO OWNER = new DigitalHumanOwnerDTO(2001L, 1001L);
    private static final Executor SAME_THREAD = Runnable::run;

    @BeforeAll
    static void initializeMybatisMetadata() {
        if (TableInfoHelper.getTableInfo(DigitalHumanGenerationJob.class) == null) {
            TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "digital-human-test"),
                DigitalHumanGenerationJob.class);
        }
    }

    @Test
    void marksExactlyOneConstructorForSpringInjection() {
        long annotatedConstructors = Arrays.stream(DigitalHumanGenerationServiceImpl.class.getDeclaredConstructors())
            .filter(constructor -> constructor.isAnnotationPresent(Autowired.class))
            .count();

        assertThat(annotatedConstructors).isEqualTo(1);
    }

    @Test
    void resourceGenerationServiceRequiresAppSecurity() {
        assertThat(DigitalHumanResourceGenerationServiceImpl.class
            .isAnnotationPresent(ConditionalOnAppSecurityEnabled.class)).isTrue();
    }

    @Test
    void setsCreationTimeBeforePersistingAndDispatchingJob() {
        DigitalHumanGenerationJobMapper mapper = mock(DigitalHumanGenerationJobMapper.class);
        IDigitalHumanMediaStorageService storage = mock(IDigitalHumanMediaStorageService.class);
        Executor executor = mock(Executor.class);
        byte[] reference = wavBytes("reference");
        when(mapper.insert(any(DigitalHumanGenerationJob.class))).thenReturn(1);
        when(storage.storeInput(801L, "reference.wav", "audio/wav", reference))
            .thenReturn(new DigitalHumanStoredMediaDTO(
                "801/input/reference.wav", "audio/wav", reference.length, "sha"));
        DigitalHumanGenerationServiceImpl service = new DigitalHumanGenerationServiceImpl(
            mapper, mock(IVoiceSynthesisService.class), mock(IDigitalHumanVideoService.class),
            storage, executor, () -> 801L);

        service.createVoiceJob(new CreateVoiceGenerationJobDTO(
            OWNER, "creation-time", "script", "reference.wav", "audio/wav", reference));

        ArgumentCaptor<DigitalHumanGenerationJob> inserted = ArgumentCaptor.forClass(DigitalHumanGenerationJob.class);
        verify(mapper).insert(inserted.capture());
        assertThat(inserted.getValue().getCreateTime()).isNotNull();
    }

    @Test
    void doesNotStartQueuedVoiceAfterItsTotalDeadline() {
        DigitalHumanGenerationJobMapper mapper = mock(DigitalHumanGenerationJobMapper.class);
        IVoiceSynthesisService voiceService = mock(IVoiceSynthesisService.class);
        IDigitalHumanMediaStorageService storage = mock(IDigitalHumanMediaStorageService.class);
        AtomicReference<Runnable> deferredTask = new AtomicReference<>();
        byte[] reference = wavBytes("reference");
        when(mapper.insert(any(DigitalHumanGenerationJob.class))).thenReturn(1);
        when(mapper.update(isNull(), any())).thenReturn(1);
        when(storage.storeInput(802L, "reference.wav", "audio/wav", reference))
            .thenReturn(new DigitalHumanStoredMediaDTO(
                "802/input/reference.wav", "audio/wav", reference.length, "sha"));
        DigitalHumanGenerationServiceImpl service = new DigitalHumanGenerationServiceImpl(
            mapper, voiceService, mock(IDigitalHumanVideoService.class), storage,
            deferredTask::set, () -> 802L);

        service.createVoiceJob(new CreateVoiceGenerationJobDTO(
            OWNER, "expired-before-start", "script", "reference.wav", "audio/wav", reference));
        ArgumentCaptor<DigitalHumanGenerationJob> inserted = ArgumentCaptor.forClass(DigitalHumanGenerationJob.class);
        verify(mapper).insert(inserted.capture());
        inserted.getValue().setCreateTime(LocalDateTime.now().minusMinutes(11));

        deferredTask.get().run();

        verifyNoInteractions(voiceService);
        verify(storage, never()).read(anyString());
        ArgumentCaptor<LambdaUpdateWrapper<DigitalHumanGenerationJob>> update = updateCaptor();
        verify(mapper).update(isNull(), update.capture());
        assertWhereStatus(update.getValue(), DigitalHumanJobStatus.QUEUED);
        assertThat(inserted.getValue().getStatus()).isEqualTo(DigitalHumanJobStatus.FAILED);
    }

    @Test
    void lengthFramesHashFieldsSoEmbeddedNullCannotReplayDifferentInput() {
        DigitalHumanGenerationJobMapper mapper = mock(DigitalHumanGenerationJobMapper.class);
        IDigitalHumanMediaStorageService storage = mock(IDigitalHumanMediaStorageService.class);
        Executor executor = mock(Executor.class);
        byte[] secondAudio = asciiWavBytes("SECOND");
        byte[] firstAudioPrefix = "RIFFSIZEWAVEPREFIX".getBytes(StandardCharsets.US_ASCII);
        byte[] firstAudio = new byte[firstAudioPrefix.length + 1 + secondAudio.length];
        System.arraycopy(firstAudioPrefix, 0, firstAudio, 0, firstAudioPrefix.length);
        System.arraycopy(secondAudio, 0, firstAudio, firstAudioPrefix.length + 1, secondAudio.length);
        String firstScript = "script";
        String secondScript = firstScript + '\0' + new String(firstAudioPrefix, StandardCharsets.US_ASCII);
        when(mapper.insert(any(DigitalHumanGenerationJob.class))).thenReturn(1);
        when(storage.storeInput(803L, "first.wav", "audio/wav", firstAudio))
            .thenReturn(new DigitalHumanStoredMediaDTO(
                "803/input/first.wav", "audio/wav", firstAudio.length, "sha"));
        DigitalHumanGenerationServiceImpl service = new DigitalHumanGenerationServiceImpl(
            mapper, mock(IVoiceSynthesisService.class), mock(IDigitalHumanVideoService.class),
            storage, executor, () -> 803L);

        service.createVoiceJob(new CreateVoiceGenerationJobDTO(
            OWNER, "framed-hash", firstScript, "first.wav", "audio/wav", firstAudio));
        ArgumentCaptor<DigitalHumanGenerationJob> inserted = ArgumentCaptor.forClass(DigitalHumanGenerationJob.class);
        verify(mapper).insert(inserted.capture());
        when(mapper.selectByIdempotency(
            2001L, 1001L, DigitalHumanJobType.VOICE_GENERATE, "framed-hash"))
            .thenReturn(inserted.getValue());

        assertThatThrownBy(() -> service.createVoiceJob(new CreateVoiceGenerationJobDTO(
            OWNER, "framed-hash", secondScript, "second.wav", "audio/wav", secondAudio)))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("不同输入");
    }

    @Test
    void normalizesIdempotencyKeyToLowercaseBeforeLookupAndStorage() {
        DigitalHumanGenerationJobMapper mapper = mock(DigitalHumanGenerationJobMapper.class);
        IDigitalHumanMediaStorageService storage = mock(IDigitalHumanMediaStorageService.class);
        Executor executor = mock(Executor.class);
        byte[] reference = wavBytes("reference");
        when(mapper.insert(any(DigitalHumanGenerationJob.class))).thenReturn(1);
        when(storage.storeInput(804L, "reference.wav", "audio/wav", reference))
            .thenReturn(new DigitalHumanStoredMediaDTO(
                "804/input/reference.wav", "audio/wav", reference.length, "sha"));
        DigitalHumanGenerationServiceImpl service = new DigitalHumanGenerationServiceImpl(
            mapper, mock(IVoiceSynthesisService.class), mock(IDigitalHumanVideoService.class),
            storage, executor, () -> 804L);

        service.createVoiceJob(new CreateVoiceGenerationJobDTO(
            OWNER, "VOICE-Key", "script", "reference.wav", "audio/wav", reference));
        ArgumentCaptor<DigitalHumanGenerationJob> inserted = ArgumentCaptor.forClass(DigitalHumanGenerationJob.class);
        verify(mapper).insert(inserted.capture());
        assertThat(inserted.getValue().getIdempotencyKey()).isEqualTo("voice-key");
        when(mapper.selectByIdempotency(
            2001L, 1001L, DigitalHumanJobType.VOICE_GENERATE, "voice-key"))
            .thenReturn(inserted.getValue());

        DigitalHumanJobDTO replay = service.createVoiceJob(new CreateVoiceGenerationJobDTO(
            OWNER, "voice-key", "script", "reference.wav", "audio/wav", reference));

        assertThat(replay.jobId()).isEqualTo(804L);
        verify(mapper).insert(any(DigitalHumanGenerationJob.class));
        verify(storage).storeInput(any(), anyString(), anyString(), any());
    }

    @Test
    void createsAndCompletesVoiceJobWithoutTrustingClientOwnership() {
        DigitalHumanGenerationJobMapper mapper = mock(DigitalHumanGenerationJobMapper.class);
        IVoiceSynthesisService voiceService = mock(IVoiceSynthesisService.class);
        IDigitalHumanVideoService videoService = mock(IDigitalHumanVideoService.class);
        IDigitalHumanMediaStorageService storage = mock(IDigitalHumanMediaStorageService.class);
        byte[] reference = wavBytes("reference-audio");
        byte[] generated = wavBytes("generated-wave");
        when(mapper.selectByIdempotency(2001L, 1001L, DigitalHumanJobType.VOICE_GENERATE, "voice-key"))
            .thenReturn(null);
        when(mapper.insert(any(DigitalHumanGenerationJob.class))).thenReturn(1);
        when(mapper.update(isNull(), any())).thenReturn(1);
        when(storage.storeInput(any(), eq("reference.wav"), eq("audio/wav"), eq(reference)))
            .thenReturn(new DigitalHumanStoredMediaDTO("100/input/reference.wav", "audio/wav", reference.length, "ref-sha"));
        when(storage.read("100/input/reference.wav"))
            .thenReturn(new DigitalHumanMediaContentDTO("reference.wav", "audio/wav", reference));
        when(voiceService.synthesize(any(VoiceSynthesisRequestDTO.class)))
            .thenReturn(new VoiceSynthesisResultDTO(generated, "audio/wav", "wav"));
        when(storage.storeOutput(any(), eq("voice.wav"), eq("audio/wav"), eq(generated)))
            .thenReturn(new DigitalHumanStoredMediaDTO("100/output/voice.wav", "audio/wav", generated.length, "voice-sha"));
        DigitalHumanGenerationServiceImpl service = new DigitalHumanGenerationServiceImpl(
            mapper, voiceService, videoService, storage, SAME_THREAD, () -> 100L);

        DigitalHumanJobDTO result = service.createVoiceJob(new CreateVoiceGenerationJobDTO(
            OWNER, "voice-key", "这是一段公开测试文案。", "reference.wav", "audio/wav", reference));

        assertThat(result.jobId()).isEqualTo(100L);
        assertThat(result.status()).isEqualTo(DigitalHumanJobStatus.SUCCEEDED);
        assertThat(result.outputAvailable()).isTrue();
        assertThat(result.voiceConfirmed()).isFalse();
        ArgumentCaptor<VoiceSynthesisRequestDTO> request = ArgumentCaptor.forClass(VoiceSynthesisRequestDTO.class);
        verify(voiceService).synthesize(request.capture());
        assertThat(request.getValue().text()).isEqualTo("这是一段公开测试文案。");
        assertThat(request.getValue().referenceAudio()).containsExactly(reference);
    }

    @Test
    void requiresSuccessfulConfirmedOwnedVoiceBeforeSubmittingVideo() {
        DigitalHumanGenerationJobMapper mapper = mock(DigitalHumanGenerationJobMapper.class);
        IVoiceSynthesisService voiceService = mock(IVoiceSynthesisService.class);
        IDigitalHumanVideoService videoService = mock(IDigitalHumanVideoService.class);
        IDigitalHumanMediaStorageService storage = mock(IDigitalHumanMediaStorageService.class);
        DigitalHumanGenerationJob voice = voiceJob(301L, DigitalHumanJobStatus.SUCCEEDED, true);
        byte[] portrait = pngBytes();
        byte[] voiceBytes = wavBytes("voice");
        when(mapper.selectOwnedById(301L, 2001L, 1001L)).thenReturn(voice);
        when(mapper.selectByIdempotency(2001L, 1001L, DigitalHumanJobType.VIDEO_GENERATE, "video-key"))
            .thenReturn(null);
        when(mapper.insert(any(DigitalHumanGenerationJob.class))).thenReturn(1);
        when(mapper.update(isNull(), any())).thenReturn(1);
        when(storage.storeInput(any(), eq("portrait.png"), eq("image/png"), eq(portrait)))
            .thenReturn(new DigitalHumanStoredMediaDTO("302/input/portrait.png", "image/png", portrait.length, "portrait-sha"));
        when(storage.read("301/output/voice.wav"))
            .thenReturn(new DigitalHumanMediaContentDTO("voice.wav", "audio/wav", voiceBytes));
        when(storage.read("302/input/portrait.png"))
            .thenReturn(new DigitalHumanMediaContentDTO("portrait.png", "image/png", portrait));
        when(videoService.submit(any(DigitalHumanVideoSubmitDTO.class))).thenReturn("prompt-302");
        DigitalHumanGenerationServiceImpl service = new DigitalHumanGenerationServiceImpl(
            mapper, voiceService, videoService, storage, SAME_THREAD, () -> 302L);

        DigitalHumanJobDTO result = service.createVideoJob(new CreateDigitalHumanVideoJobDTO(
            OWNER, "video-key", 301L, "portrait.png", "image/png", portrait));

        assertThat(result.jobId()).isEqualTo(302L);
        assertThat(result.parentJobId()).isEqualTo(301L);
        assertThat(result.status()).isEqualTo(DigitalHumanJobStatus.RUNNING);
        ArgumentCaptor<DigitalHumanVideoSubmitDTO> request = ArgumentCaptor.forClass(DigitalHumanVideoSubmitDTO.class);
        verify(videoService).submit(request.capture());
        assertThat(request.getValue().portrait()).containsExactly(portrait);
        assertThat(request.getValue().audio()).containsExactly(voiceBytes);
        ArgumentCaptor<LambdaUpdateWrapper<DigitalHumanGenerationJob>> updates = updateCaptor();
        verify(mapper, times(2)).update(isNull(), updates.capture());
        assertWhereStatus(updates.getAllValues().get(0), DigitalHumanJobStatus.QUEUED);
        assertWhereStatus(updates.getAllValues().get(1), DigitalHumanJobStatus.RUNNING);
    }

    @Test
    void rejectsVideoWhenVoiceHasNotBeenConfirmed() {
        DigitalHumanGenerationJobMapper mapper = mock(DigitalHumanGenerationJobMapper.class);
        when(mapper.selectOwnedById(301L, 2001L, 1001L))
            .thenReturn(voiceJob(301L, DigitalHumanJobStatus.SUCCEEDED, false));
        DigitalHumanGenerationServiceImpl service = new DigitalHumanGenerationServiceImpl(
            mapper, mock(IVoiceSynthesisService.class), mock(IDigitalHumanVideoService.class),
            mock(IDigitalHumanMediaStorageService.class), SAME_THREAD, () -> 302L);

        assertThatThrownBy(() -> service.createVideoJob(new CreateDigitalHumanVideoJobDTO(
            OWNER, "video-key", 301L, "portrait.png", "image/png", pngBytes())))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("确认声音");
    }

    @Test
    void pollsComfyAndStoresValidatedSuccessfulVideo() {
        DigitalHumanGenerationJobMapper mapper = mock(DigitalHumanGenerationJobMapper.class);
        IDigitalHumanVideoService videoService = mock(IDigitalHumanVideoService.class);
        IDigitalHumanMediaStorageService storage = mock(IDigitalHumanMediaStorageService.class);
        DigitalHumanGenerationJob video = videoJob(401L);
        byte[] mp4 = mp4Bytes("validated-mp4");
        when(mapper.selectOwnedById(401L, 2001L, 1001L)).thenReturn(video);
        when(mapper.update(isNull(), any())).thenReturn(1);
        when(videoService.poll("prompt-401"))
            .thenReturn(new DigitalHumanVideoPollDTO(
                DigitalHumanVideoProviderStatus.SUCCEEDED, 100, mp4, "video/mp4", "mp4", null));
        when(storage.storeOutput(eq(401L), anyString(), eq("video/mp4"), eq(mp4)))
            .thenReturn(new DigitalHumanStoredMediaDTO("401/output/video.mp4", "video/mp4", mp4.length, "video-sha"));
        DigitalHumanGenerationServiceImpl service = new DigitalHumanGenerationServiceImpl(
            mapper, mock(IVoiceSynthesisService.class), videoService, storage, SAME_THREAD, () -> 999L);

        DigitalHumanJobDTO result = service.getJob(401L, OWNER);

        assertThat(result.status()).isEqualTo(DigitalHumanJobStatus.SUCCEEDED);
        assertThat(result.outputAvailable()).isTrue();
        assertThat(video.getOutputMediaKey()).isEqualTo("401/output/video.mp4");
    }

    @Test
    void hidesJobsOwnedByAnotherUser() {
        DigitalHumanGenerationJobMapper mapper = mock(DigitalHumanGenerationJobMapper.class);
        when(mapper.selectOwnedById(501L, 2001L, 1001L)).thenReturn(null);
        DigitalHumanGenerationServiceImpl service = new DigitalHumanGenerationServiceImpl(
            mapper, mock(IVoiceSynthesisService.class), mock(IDigitalHumanVideoService.class),
            mock(IDigitalHumanMediaStorageService.class), SAME_THREAD, () -> 999L);

        assertThatThrownBy(() -> service.getJob(501L, OWNER))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("不存在");
    }

    @Test
    void replaysConcurrentVoiceRequestAfterUniqueConstraintWinnerWithoutDispatchingLoser() {
        DigitalHumanGenerationJobMapper mapper = mock(DigitalHumanGenerationJobMapper.class);
        IDigitalHumanMediaStorageService storage = mock(IDigitalHumanMediaStorageService.class);
        Executor executor = mock(Executor.class);
        byte[] reference = wavBytes("same-reference");
        DigitalHumanGenerationJob winner = voiceJob(701L, DigitalHumanJobStatus.RUNNING, false);
        winner.setInputHash(null);
        when(mapper.selectByIdempotency(2001L, 1001L, DigitalHumanJobType.VOICE_GENERATE, "same-key"))
            .thenReturn(null, winner);
        when(storage.storeInput(702L, "reference.wav", "audio/wav", reference))
            .thenReturn(new DigitalHumanStoredMediaDTO("702/input/reference.wav", "audio/wav", reference.length, "sha"));
        when(mapper.insert(any(DigitalHumanGenerationJob.class))).thenAnswer(invocation -> {
            DigitalHumanGenerationJob loser = invocation.getArgument(0);
            winner.setInputHash(loser.getInputHash());
            throw new DuplicateKeyException("uk_av_dh_job_idempotency");
        });
        DigitalHumanGenerationServiceImpl service = new DigitalHumanGenerationServiceImpl(
            mapper, mock(IVoiceSynthesisService.class), mock(IDigitalHumanVideoService.class),
            storage, executor, () -> 702L);

        DigitalHumanJobDTO result = service.createVoiceJob(new CreateVoiceGenerationJobDTO(
            OWNER, "same-key", "same script", "reference.wav", "audio/wav", reference));

        assertThat(result.jobId()).isEqualTo(701L);
        verify(storage).delete("702/input/reference.wav");
        verify(executor, never()).execute(any());
    }

    @Test
    void rejectsConcurrentIdempotencyKeyWhenWinnerHasDifferentInput() {
        DigitalHumanGenerationJobMapper mapper = mock(DigitalHumanGenerationJobMapper.class);
        IDigitalHumanMediaStorageService storage = mock(IDigitalHumanMediaStorageService.class);
        Executor executor = mock(Executor.class);
        byte[] reference = wavBytes("different-reference");
        DigitalHumanGenerationJob winner = voiceJob(711L, DigitalHumanJobStatus.RUNNING, false);
        winner.setInputHash("different-input-hash");
        when(mapper.selectByIdempotency(2001L, 1001L, DigitalHumanJobType.VOICE_GENERATE, "conflict-key"))
            .thenReturn(null, winner);
        when(storage.storeInput(712L, "reference.wav", "audio/wav", reference))
            .thenReturn(new DigitalHumanStoredMediaDTO("712/input/reference.wav", "audio/wav", reference.length, "sha"));
        when(mapper.insert(any(DigitalHumanGenerationJob.class)))
            .thenThrow(new DuplicateKeyException("uk_av_dh_job_idempotency"));
        DigitalHumanGenerationServiceImpl service = new DigitalHumanGenerationServiceImpl(
            mapper, mock(IVoiceSynthesisService.class), mock(IDigitalHumanVideoService.class),
            storage, executor, () -> 712L);

        assertThatThrownBy(() -> service.createVoiceJob(new CreateVoiceGenerationJobDTO(
            OWNER, "conflict-key", "different script", "reference.wav", "audio/wav", reference)))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("不同输入");
        verify(storage).delete("712/input/reference.wav");
        verify(executor, never()).execute(any());
    }

    @Test
    void marksInsertedJobFailedWhenExecutorRejectsDispatch() {
        DigitalHumanGenerationJobMapper mapper = mock(DigitalHumanGenerationJobMapper.class);
        IDigitalHumanMediaStorageService storage = mock(IDigitalHumanMediaStorageService.class);
        Executor executor = command -> {
            throw new RejectedExecutionException("executor saturated");
        };
        byte[] reference = wavBytes("reference");
        when(storage.storeInput(721L, "reference.wav", "audio/wav", reference))
            .thenReturn(new DigitalHumanStoredMediaDTO("721/input/reference.wav", "audio/wav", reference.length, "sha"));
        when(mapper.insert(any(DigitalHumanGenerationJob.class))).thenReturn(1);
        when(mapper.update(isNull(), any())).thenReturn(1);
        DigitalHumanGenerationServiceImpl service = new DigitalHumanGenerationServiceImpl(
            mapper, mock(IVoiceSynthesisService.class), mock(IDigitalHumanVideoService.class),
            storage, executor, () -> 721L);

        DigitalHumanJobDTO result = service.createVoiceJob(new CreateVoiceGenerationJobDTO(
            OWNER, "executor-key", "script", "reference.wav", "audio/wav", reference));

        assertThat(result.status()).isEqualTo(DigitalHumanJobStatus.FAILED);
        assertThat(result.errorMessage()).contains("重试");
        ArgumentCaptor<LambdaUpdateWrapper<DigitalHumanGenerationJob>> update = updateCaptor();
        verify(mapper).update(isNull(), update.capture());
        assertWhereStatus(update.getValue(), DigitalHumanJobStatus.QUEUED);
    }

    @Test
    void rejectsUnsupportedAndSpoofedUploadMediaBeforeStoring() {
        IDigitalHumanMediaStorageService storage = mock(IDigitalHumanMediaStorageService.class);
        DigitalHumanGenerationServiceImpl service = new DigitalHumanGenerationServiceImpl(
            mock(DigitalHumanGenerationJobMapper.class), mock(IVoiceSynthesisService.class),
            mock(IDigitalHumanVideoService.class), storage, SAME_THREAD, () -> 731L);

        assertThatThrownBy(() -> service.createVoiceJob(new CreateVoiceGenerationJobDTO(
            OWNER, "ogg-key", "script", "reference.ogg", "audio/ogg", new byte[]{1, 2, 3})))
            .isInstanceOf(ServiceException.class);
        assertThatThrownBy(() -> service.createVoiceJob(new CreateVoiceGenerationJobDTO(
            OWNER, "fake-wav-key", "script", "reference.wav", "audio/wav", "not-wav".getBytes(StandardCharsets.UTF_8))))
            .isInstanceOf(ServiceException.class);
        verify(storage, never()).storeInput(any(), any(), any(), any());
    }

    @Test
    void rejectsSpoofedProviderWavBeforeStoringOutput() {
        DigitalHumanGenerationJobMapper mapper = mock(DigitalHumanGenerationJobMapper.class);
        IVoiceSynthesisService voiceService = mock(IVoiceSynthesisService.class);
        IDigitalHumanMediaStorageService storage = mock(IDigitalHumanMediaStorageService.class);
        byte[] reference = wavBytes("reference");
        byte[] spoofed = "not-a-wave".getBytes(StandardCharsets.UTF_8);
        when(storage.storeInput(741L, "reference.wav", "audio/wav", reference))
            .thenReturn(new DigitalHumanStoredMediaDTO("741/input/reference.wav", "audio/wav", reference.length, "sha"));
        when(storage.read("741/input/reference.wav"))
            .thenReturn(new DigitalHumanMediaContentDTO("reference.wav", "audio/wav", reference));
        when(voiceService.synthesize(any(VoiceSynthesisRequestDTO.class)))
            .thenReturn(new VoiceSynthesisResultDTO(spoofed, "audio/wav", "wav"));
        when(mapper.insert(any(DigitalHumanGenerationJob.class))).thenReturn(1);
        when(mapper.update(isNull(), any())).thenReturn(1);
        DigitalHumanGenerationServiceImpl service = new DigitalHumanGenerationServiceImpl(
            mapper, voiceService, mock(IDigitalHumanVideoService.class), storage, SAME_THREAD, () -> 741L);

        DigitalHumanJobDTO result = service.createVoiceJob(new CreateVoiceGenerationJobDTO(
            OWNER, "spoofed-provider-key", "script", "reference.wav", "audio/wav", reference));

        assertThat(result.status()).isEqualTo(DigitalHumanJobStatus.FAILED);
        verify(storage, never()).storeOutput(any(), any(), any(), any());
        ArgumentCaptor<LambdaUpdateWrapper<DigitalHumanGenerationJob>> updates = updateCaptor();
        verify(mapper, times(2)).update(isNull(), updates.capture());
        assertWhereStatus(updates.getAllValues().get(0), DigitalHumanJobStatus.QUEUED);
        assertWhereStatus(updates.getAllValues().get(1), DigitalHumanJobStatus.RUNNING);
        verify(mapper, never()).updateById(any(DigitalHumanGenerationJob.class));
    }

    @Test
    void doesNotPollWhenAnotherRequestOwnsTheDatabaseLease() {
        DigitalHumanGenerationJobMapper mapper = mock(DigitalHumanGenerationJobMapper.class);
        IDigitalHumanVideoService videoService = mock(IDigitalHumanVideoService.class);
        DigitalHumanGenerationJob video = videoJob(751L);
        when(mapper.selectOwnedById(751L, 2001L, 1001L)).thenReturn(video);
        when(mapper.update(isNull(), any())).thenReturn(0);
        DigitalHumanGenerationServiceImpl service = new DigitalHumanGenerationServiceImpl(
            mapper, mock(IVoiceSynthesisService.class), videoService,
            mock(IDigitalHumanMediaStorageService.class), SAME_THREAD, () -> 999L);

        DigitalHumanJobDTO result = service.getJob(751L, OWNER);

        assertThat(result.status()).isEqualTo(DigitalHumanJobStatus.RUNNING);
        verifyNoInteractions(videoService);
    }

    @Test
    void marksVideoFailedAfterThreeConsecutiveProviderPollErrors() {
        DigitalHumanGenerationJobMapper mapper = mock(DigitalHumanGenerationJobMapper.class);
        IDigitalHumanVideoService videoService = mock(IDigitalHumanVideoService.class);
        DigitalHumanGenerationJob video = videoJob(761L);
        when(mapper.selectOwnedById(761L, 2001L, 1001L)).thenReturn(video);
        when(mapper.update(isNull(), any())).thenReturn(1);
        when(videoService.poll("prompt-401")).thenThrow(new ServiceException("provider unavailable"));
        DigitalHumanGenerationServiceImpl service = new DigitalHumanGenerationServiceImpl(
            mapper, mock(IVoiceSynthesisService.class), videoService,
            mock(IDigitalHumanMediaStorageService.class), SAME_THREAD, () -> 999L);

        assertThat(service.getJob(761L, OWNER).status()).isEqualTo(DigitalHumanJobStatus.RUNNING);
        assertThat(service.getJob(761L, OWNER).status()).isEqualTo(DigitalHumanJobStatus.RUNNING);
        DigitalHumanJobDTO third = service.getJob(761L, OWNER);

        assertThat(third.status()).isEqualTo(DigitalHumanJobStatus.FAILED);
        assertThat(third.errorMessage()).contains("重试");
    }

    @Test
    void stalePollCannotOverwriteWinnerAndCleansItsPrivateOutput() {
        DigitalHumanGenerationJobMapper mapper = mock(DigitalHumanGenerationJobMapper.class);
        IDigitalHumanVideoService videoService = mock(IDigitalHumanVideoService.class);
        IDigitalHumanMediaStorageService storage = mock(IDigitalHumanMediaStorageService.class);
        DigitalHumanGenerationJob stale = videoJob(771L);
        DigitalHumanGenerationJob winner = videoJob(771L);
        winner.setStatus(DigitalHumanJobStatus.SUCCEEDED);
        winner.setOutputMediaKey("771/output/winner.mp4");
        winner.setProgress(100);
        byte[] mp4 = mp4Bytes("stale-output");
        when(mapper.selectOwnedById(771L, 2001L, 1001L)).thenReturn(stale, winner);
        when(mapper.update(isNull(), any())).thenReturn(1, 0);
        when(videoService.poll("prompt-401")).thenReturn(new DigitalHumanVideoPollDTO(
            DigitalHumanVideoProviderStatus.SUCCEEDED, 100, mp4, "video/mp4", "mp4", null));
        when(storage.storeOutput(eq(771L), anyString(), eq("video/mp4"), eq(mp4)))
            .thenReturn(new DigitalHumanStoredMediaDTO(
                "771/output/stale-token.mp4", "video/mp4", mp4.length, "stale-sha"));
        DigitalHumanGenerationServiceImpl service = new DigitalHumanGenerationServiceImpl(
            mapper, mock(IVoiceSynthesisService.class), videoService, storage, SAME_THREAD, () -> 999L);

        DigitalHumanJobDTO result = service.getJob(771L, OWNER);

        assertThat(result.status()).isEqualTo(DigitalHumanJobStatus.SUCCEEDED);
        verify(storage).delete("771/output/stale-token.mp4");
        verify(mapper, never()).updateById(stale);
    }

    @Test
    void expiresQueuedVoiceAfterTenMinutesUsingStatusCas() {
        DigitalHumanGenerationJobMapper mapper = mock(DigitalHumanGenerationJobMapper.class);
        IDigitalHumanVideoService videoService = mock(IDigitalHumanVideoService.class);
        DigitalHumanGenerationJob voice = voiceJob(781L, DigitalHumanJobStatus.QUEUED, false);
        voice.setCreateTime(LocalDateTime.now().minusMinutes(11));
        voice.setProgress(0);
        when(mapper.selectOwnedById(781L, 2001L, 1001L)).thenReturn(voice);
        when(mapper.update(isNull(), any())).thenReturn(1);
        DigitalHumanGenerationServiceImpl service = new DigitalHumanGenerationServiceImpl(
            mapper, mock(IVoiceSynthesisService.class), videoService,
            mock(IDigitalHumanMediaStorageService.class), SAME_THREAD, () -> 999L);

        DigitalHumanJobDTO result = service.getJob(781L, OWNER);

        assertThat(result.status()).isEqualTo(DigitalHumanJobStatus.FAILED);
        assertThat(result.errorMessage()).contains("超时");
        ArgumentCaptor<LambdaUpdateWrapper<DigitalHumanGenerationJob>> update = updateCaptor();
        verify(mapper).update(isNull(), update.capture());
        assertWhereStatus(update.getValue(), DigitalHumanJobStatus.QUEUED);
        verifyNoInteractions(videoService);
    }

    @Test
    void expiresProviderRunningVideoAfterSixtyMinutesWithoutPolling() {
        DigitalHumanGenerationJobMapper mapper = mock(DigitalHumanGenerationJobMapper.class);
        IDigitalHumanVideoService videoService = mock(IDigitalHumanVideoService.class);
        DigitalHumanGenerationJob video = videoJob(782L);
        video.setCreateTime(LocalDateTime.now().minusMinutes(61));
        when(mapper.selectOwnedById(782L, 2001L, 1001L)).thenReturn(video);
        when(mapper.update(isNull(), any())).thenReturn(1);
        DigitalHumanGenerationServiceImpl service = new DigitalHumanGenerationServiceImpl(
            mapper, mock(IVoiceSynthesisService.class), videoService,
            mock(IDigitalHumanMediaStorageService.class), SAME_THREAD, () -> 999L);

        DigitalHumanJobDTO result = service.getJob(782L, OWNER);

        assertThat(result.status()).isEqualTo(DigitalHumanJobStatus.FAILED);
        assertThat(result.errorMessage()).contains("超时");
        ArgumentCaptor<LambdaUpdateWrapper<DigitalHumanGenerationJob>> update = updateCaptor();
        verify(mapper).update(isNull(), update.capture());
        assertWhereStatus(update.getValue(), DigitalHumanJobStatus.RUNNING);
        verifyNoInteractions(videoService);
    }

    @Test
    void lateVoiceSuccessCannotOverwriteTerminalStateAndDeletesPrivateOutput() {
        DigitalHumanGenerationJobMapper mapper = mock(DigitalHumanGenerationJobMapper.class);
        IVoiceSynthesisService voiceService = mock(IVoiceSynthesisService.class);
        IDigitalHumanMediaStorageService storage = mock(IDigitalHumanMediaStorageService.class);
        byte[] reference = wavBytes("reference");
        byte[] generated = wavBytes("late-success");
        when(storage.storeInput(783L, "reference.wav", "audio/wav", reference))
            .thenReturn(new DigitalHumanStoredMediaDTO("783/input/reference.wav", "audio/wav", reference.length, "sha"));
        when(storage.read("783/input/reference.wav"))
            .thenReturn(new DigitalHumanMediaContentDTO("reference.wav", "audio/wav", reference));
        when(voiceService.synthesize(any(VoiceSynthesisRequestDTO.class)))
            .thenReturn(new VoiceSynthesisResultDTO(generated, "audio/wav", "wav"));
        when(storage.storeOutput(783L, "voice.wav", "audio/wav", generated))
            .thenReturn(new DigitalHumanStoredMediaDTO("783/output/voice.wav", "audio/wav", generated.length, "voice-sha"));
        when(mapper.insert(any(DigitalHumanGenerationJob.class))).thenReturn(1);
        when(mapper.update(isNull(), any())).thenReturn(1, 0);
        DigitalHumanGenerationServiceImpl service = new DigitalHumanGenerationServiceImpl(
            mapper, voiceService, mock(IDigitalHumanVideoService.class), storage, SAME_THREAD, () -> 783L);

        DigitalHumanJobDTO result = service.createVoiceJob(new CreateVoiceGenerationJobDTO(
            OWNER, "late-voice", "script", "reference.wav", "audio/wav", reference));

        assertThat(result.status()).isEqualTo(DigitalHumanJobStatus.RUNNING);
        verify(storage).delete("783/output/voice.wav");
        ArgumentCaptor<LambdaUpdateWrapper<DigitalHumanGenerationJob>> updates = updateCaptor();
        verify(mapper, times(2)).update(isNull(), updates.capture());
        assertWhereStatus(updates.getAllValues().get(0), DigitalHumanJobStatus.QUEUED);
        assertWhereStatus(updates.getAllValues().get(1), DigitalHumanJobStatus.RUNNING);
        verify(mapper, never()).updateById(any(DigitalHumanGenerationJob.class));
    }

    @Test
    void videoSubmitFailureCannotOverwriteTerminalStateWhenStatusCasLoses() {
        DigitalHumanGenerationJobMapper mapper = mock(DigitalHumanGenerationJobMapper.class);
        IDigitalHumanVideoService videoService = mock(IDigitalHumanVideoService.class);
        IDigitalHumanMediaStorageService storage = mock(IDigitalHumanMediaStorageService.class);
        DigitalHumanGenerationJob voice = voiceJob(790L, DigitalHumanJobStatus.SUCCEEDED, true);
        byte[] portrait = pngBytes();
        byte[] voiceBytes = wavBytes("voice");
        when(mapper.selectOwnedById(790L, 2001L, 1001L)).thenReturn(voice);
        when(mapper.insert(any(DigitalHumanGenerationJob.class))).thenReturn(1);
        when(mapper.update(isNull(), any())).thenReturn(1, 0);
        when(storage.storeInput(791L, "portrait.png", "image/png", portrait))
            .thenReturn(new DigitalHumanStoredMediaDTO("791/input/portrait.png", "image/png", portrait.length, "sha"));
        when(storage.read("791/input/portrait.png"))
            .thenReturn(new DigitalHumanMediaContentDTO("portrait.png", "image/png", portrait));
        when(storage.read("790/output/voice.wav"))
            .thenReturn(new DigitalHumanMediaContentDTO("voice.wav", "audio/wav", voiceBytes));
        when(videoService.submit(any(DigitalHumanVideoSubmitDTO.class)))
            .thenThrow(new ServiceException("provider unavailable"));
        DigitalHumanGenerationServiceImpl service = new DigitalHumanGenerationServiceImpl(
            mapper, mock(IVoiceSynthesisService.class), videoService, storage, SAME_THREAD, () -> 791L);

        DigitalHumanJobDTO result = service.createVideoJob(new CreateDigitalHumanVideoJobDTO(
            OWNER, "late-video-failure", 790L, "portrait.png", "image/png", portrait));

        assertThat(result.status()).isEqualTo(DigitalHumanJobStatus.RUNNING);
        ArgumentCaptor<LambdaUpdateWrapper<DigitalHumanGenerationJob>> updates = updateCaptor();
        verify(mapper, times(2)).update(isNull(), updates.capture());
        assertWhereStatus(updates.getAllValues().get(0), DigitalHumanJobStatus.QUEUED);
        assertWhereStatus(updates.getAllValues().get(1), DigitalHumanJobStatus.RUNNING);
        verify(mapper, never()).updateById(any(DigitalHumanGenerationJob.class));
    }

    @Test
    void confirmVoiceUsesSucceededStatusCasAndRereadsAfterConcurrentConfirmation() {
        DigitalHumanGenerationJobMapper mapper = mock(DigitalHumanGenerationJobMapper.class);
        DigitalHumanGenerationJob stale = voiceJob(784L, DigitalHumanJobStatus.SUCCEEDED, false);
        DigitalHumanGenerationJob winner = voiceJob(784L, DigitalHumanJobStatus.SUCCEEDED, true);
        when(mapper.selectOwnedById(784L, 2001L, 1001L)).thenReturn(stale, winner);
        when(mapper.update(isNull(), any())).thenReturn(0);
        DigitalHumanGenerationServiceImpl service = new DigitalHumanGenerationServiceImpl(
            mapper, mock(IVoiceSynthesisService.class), mock(IDigitalHumanVideoService.class),
            mock(IDigitalHumanMediaStorageService.class), SAME_THREAD, () -> 999L);

        DigitalHumanJobDTO result = service.confirmVoiceJob(784L, OWNER);

        assertThat(result.voiceConfirmed()).isTrue();
        ArgumentCaptor<LambdaUpdateWrapper<DigitalHumanGenerationJob>> update = updateCaptor();
        verify(mapper).update(isNull(), update.capture());
        assertWhereStatus(update.getValue(), DigitalHumanJobStatus.SUCCEEDED);
        verify(mapper, never()).updateById(any(DigitalHumanGenerationJob.class));
    }

    @Test
    void acceptsBrowserMp3MimeAndNormalizesStoredMediaType() {
        DigitalHumanGenerationJobMapper mapper = mock(DigitalHumanGenerationJobMapper.class);
        IDigitalHumanMediaStorageService storage = mock(IDigitalHumanMediaStorageService.class);
        Executor executor = mock(Executor.class);
        byte[] mp3 = new byte[]{'I', 'D', '3', 1};
        when(mapper.insert(any(DigitalHumanGenerationJob.class))).thenReturn(1);
        when(storage.storeInput(785L, "reference.mp3", "audio/mpeg", mp3))
            .thenReturn(new DigitalHumanStoredMediaDTO("785/input/reference.mp3", "audio/mpeg", mp3.length, "sha"));
        DigitalHumanGenerationServiceImpl service = new DigitalHumanGenerationServiceImpl(
            mapper, mock(IVoiceSynthesisService.class), mock(IDigitalHumanVideoService.class),
            storage, executor, () -> 785L);

        service.createVoiceJob(new CreateVoiceGenerationJobDTO(
            OWNER, "browser-mp3", "script", "reference.mp3", "audio/mp3", mp3));

        verify(storage).storeInput(785L, "reference.mp3", "audio/mpeg", mp3);
    }

    @Test
    void acceptsBrowserM4aMimeAndNormalizesStoredMediaType() {
        DigitalHumanGenerationJobMapper mapper = mock(DigitalHumanGenerationJobMapper.class);
        IDigitalHumanMediaStorageService storage = mock(IDigitalHumanMediaStorageService.class);
        Executor executor = mock(Executor.class);
        byte[] m4a = mp4Bytes("m4a");
        when(mapper.insert(any(DigitalHumanGenerationJob.class))).thenReturn(1);
        when(storage.storeInput(786L, "reference.m4a", "audio/mp4", m4a))
            .thenReturn(new DigitalHumanStoredMediaDTO("786/input/reference.m4a", "audio/mp4", m4a.length, "sha"));
        DigitalHumanGenerationServiceImpl service = new DigitalHumanGenerationServiceImpl(
            mapper, mock(IVoiceSynthesisService.class), mock(IDigitalHumanVideoService.class),
            storage, executor, () -> 786L);

        service.createVoiceJob(new CreateVoiceGenerationJobDTO(
            OWNER, "browser-m4a", "script", "reference.m4a", "audio/m4a", m4a));

        verify(storage).storeInput(786L, "reference.m4a", "audio/mp4", m4a);
    }

    private static DigitalHumanGenerationJob voiceJob(long id, DigitalHumanJobStatus status, boolean confirmed) {
        DigitalHumanGenerationJob job = new DigitalHumanGenerationJob();
        job.setId(id);
        job.setTenantId(2001L);
        job.setOwnerUserId(1001L);
        job.setJobType(DigitalHumanJobType.VOICE_GENERATE);
        job.setStatus(status);
        job.setOutputMediaKey(id + "/output/voice.wav");
        job.setVoiceConfirmed(confirmed);
        job.setProgress(100);
        return job;
    }

    private static DigitalHumanGenerationJob videoJob(long id) {
        DigitalHumanGenerationJob job = new DigitalHumanGenerationJob();
        job.setId(id);
        job.setTenantId(2001L);
        job.setOwnerUserId(1001L);
        job.setJobType(DigitalHumanJobType.VIDEO_GENERATE);
        job.setStatus(DigitalHumanJobStatus.RUNNING);
        job.setProviderJobId("prompt-401");
        job.setProgress(30);
        return job;
    }

    private static byte[] wavBytes(String payload) {
        byte[] data = payload.getBytes(StandardCharsets.UTF_8);
        byte[] wav = new byte[12 + data.length];
        System.arraycopy(new byte[]{'R', 'I', 'F', 'F'}, 0, wav, 0, 4);
        System.arraycopy(new byte[]{'W', 'A', 'V', 'E'}, 0, wav, 8, 4);
        System.arraycopy(data, 0, wav, 12, data.length);
        return wav;
    }

    private static byte[] asciiWavBytes(String payload) {
        return ("RIFFSIZEWAVE" + payload).getBytes(StandardCharsets.US_ASCII);
    }

    private static byte[] pngBytes() {
        return new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1};
    }

    private static byte[] mp4Bytes(String payload) {
        byte[] data = payload.getBytes(StandardCharsets.UTF_8);
        byte[] mp4 = new byte[12 + data.length];
        mp4[3] = 12;
        System.arraycopy(new byte[]{'f', 't', 'y', 'p'}, 0, mp4, 4, 4);
        System.arraycopy(data, 0, mp4, 12, data.length);
        return mp4;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ArgumentCaptor<LambdaUpdateWrapper<DigitalHumanGenerationJob>> updateCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
    }

    private static void assertWhereStatus(LambdaUpdateWrapper<DigitalHumanGenerationJob> update,
                                          DigitalHumanJobStatus expected) {
        String sqlSegment = update.getSqlSegment();
        Map<String, Object> parameters = update.getParamNameValuePairs();
        assertThat(parameters.entrySet()).anySatisfy(entry -> {
            assertThat(entry.getValue()).isEqualTo(expected);
            assertThat(sqlSegment).contains(entry.getKey());
        });
    }
}
