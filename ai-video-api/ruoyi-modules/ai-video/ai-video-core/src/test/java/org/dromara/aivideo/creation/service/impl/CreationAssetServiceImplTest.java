package org.dromara.aivideo.creation.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.apache.ibatis.annotations.Select;
import org.dromara.aivideo.creation.domain.CreationAsset;
import org.dromara.aivideo.creation.dto.CreationAssetDTO;
import org.dromara.aivideo.creation.dto.CreationAssetUploadDTO;
import org.dromara.aivideo.digitalhuman.domain.DigitalHumanGenerationJob;
import org.dromara.aivideo.digitalhuman.domain.DigitalHumanJobStatus;
import org.dromara.aivideo.digitalhuman.domain.DigitalHumanJobType;
import org.dromara.aivideo.digitalhuman.dto.DigitalHumanJobDTO;
import org.dromara.aivideo.digitalhuman.dto.DigitalHumanMediaContentDTO;
import org.dromara.aivideo.digitalhuman.mapper.DigitalHumanGenerationJobMapper;
import org.dromara.aivideo.digitalhuman.service.IDigitalHumanGenerationService;
import org.dromara.aivideo.identity.domain.AppUser;
import org.dromara.aivideo.identity.mapper.AppUserMapper;
import org.dromara.aivideo.creation.dto.PendingRenderOutputDTO;
import org.dromara.aivideo.creation.dto.RegisterPendingRenderOutputDTO;
import org.dromara.aivideo.creation.dto.RenderOutputFailureDTO;
import org.dromara.aivideo.creation.enums.CreationAssetStatus;
import org.dromara.aivideo.creation.enums.CreationAssetType;
import org.dromara.aivideo.creation.enums.CreationAssetUsageOrigin;
import org.dromara.aivideo.creation.mapper.CreationAssetMapper;
import org.dromara.aivideo.creation.mapper.CreationProjectMapper;
import org.dromara.aivideo.creation.service.CreationMediaHandle;
import org.dromara.aivideo.timeline.dto.TimelineRenderResultDTO;
import org.dromara.aivideo.timeline.dto.TimelineMediaProbeDTO;
import org.dromara.aivideo.timeline.constant.TimelineErrorCodes;
import org.dromara.aivideo.timeline.service.ITimelineMediaRenderService;
import org.dromara.aivideo.timeline.service.TimelineRenderOutputHandle;
import org.dromara.aivideo.timeline.mapper.TimelineAssetRefMapper;
import org.dromara.aivideo.task.mapper.AiTaskExecutionMapper;
import org.dromara.aivideo.task.mapper.AiTaskMapper;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.oss.client.OssClient;
import org.dromara.common.oss.config.OssClientConfig;
import org.dromara.common.oss.exception.S3StorageException;
import org.dromara.common.oss.model.GetObjectResult;
import org.dromara.common.oss.model.Options;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.dao.DuplicateKeyException;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("dev")
class CreationAssetServiceImplTest {

    @Mock
    private CreationAssetMapper assetMapper;
    @Mock
    private OssClient ossClient;
    @Mock
    private ObjectProvider<OssClient> ossClientProvider;
    @Mock
    private ResponseInputStream<GetObjectResponse> responseStream;

    @Test
    void springConstructorAllowsMissingDigitalHumanGenerationService() {
        ObjectProvider<IDigitalHumanGenerationService> digitalHumanServiceProvider = mock();
        ObjectProvider<ITimelineMediaRenderService> mediaRenderServiceProvider = mock();
        ObjectProvider<OssClient> projectOssClientProvider = mock();
        when(digitalHumanServiceProvider.getIfAvailable()).thenReturn(null);
        when(mediaRenderServiceProvider.getIfAvailable()).thenReturn(null);

        CreationAssetServiceImpl service = new CreationAssetServiceImpl(assetMapper, null, null, null, null,
            null, null, digitalHumanServiceProvider, mediaRenderServiceProvider, projectOssClientProvider);

        assertThat(service).isNotNull();
        verify(digitalHumanServiceProvider).getIfAvailable();
        verify(mediaRenderServiceProvider).getIfAvailable();
    }

    @Test
    void rangeHandleKeepsTheOssResponseStreamOpenUntilTheCallerClosesIt() throws IOException {
        when(ossClientProvider.getIfAvailable()).thenReturn(ossClient);
        when(assetMapper.selectOne(any(Wrapper.class))).thenReturn(readyAsset(88L, 10L));
        when(ossClient.config()).thenReturn(OssClientConfig.builder().bucket("creation-bucket").build());
        when(ossClient.doCustomBufferedDownload(any(),
            org.mockito.ArgumentMatchers.eq(Duration.ofSeconds(30)))).thenReturn(responseStream);
        when(responseStream.read()).thenReturn(7);

        CreationMediaHandle handle = new CreationAssetServiceImpl(assetMapper, ossClientProvider)
            .openOwnedMediaRange(7L, "88", "bytes=2-4");

        assertThat(handle.stream().read()).isEqualTo(7);
        verifyRangeRequest("creation-bucket", "creation-assets/7/88.mp4", "bytes=2-4");
        handle.close();
        verify(responseStream).close();
    }

    @Test
    void invalidRangeIsRejectedBeforeOpeningOss() {
        when(assetMapper.selectOne(any(Wrapper.class))).thenReturn(readyAsset(88L, 10L));

        assertThatThrownBy(() -> new CreationAssetServiceImpl(assetMapper, ossClientProvider)
            .openOwnedMediaRange(7L, "88", "bytes=10-10"))
            .isInstanceOf(ServiceException.class);
        verifyNoInteractions(ossClientProvider, ossClient);
    }

    @Test
    void returnsOnlyTheOwnedReadyTimelineRenderOutputBoundToThePersistedTask() {
        CreationAsset output = timelineRenderAsset(88L, 99L);
        when(assetMapper.selectOwnedTimelineRenderOutput(7L, 99L, 88L)).thenReturn(output);

        CreationAssetDTO result = new CreationAssetServiceImpl(assetMapper)
            .getOwnedTimelineRenderOutput(7L, "99", "88");

        assertThat(result.assetId()).isEqualTo("88");
        assertThat(result.status()).isEqualTo(CreationAssetStatus.READY);
        assertThat(result.assetType()).isEqualTo(CreationAssetType.VIDEO);
        assertThat(result.usageOrigin()).isEqualTo(CreationAssetUsageOrigin.TIMELINE_RENDER_OUTPUT);
        verify(assetMapper).selectOwnedTimelineRenderOutput(7L, 99L, 88L);
    }

    @Test
    void opensOnlyTheOwnedReadyTimelineRenderOutputWithExplicitOrigin() throws IOException {
        CreationAsset output = timelineRenderAsset(88L, 99L);
        when(assetMapper.selectOwnedTimelineRenderOutput(7L, 99L, 88L)).thenReturn(output);
        when(ossClientProvider.getIfAvailable()).thenReturn(ossClient);
        when(ossClient.config()).thenReturn(OssClientConfig.builder().bucket("creation-bucket").build());
        when(ossClient.doCustomBufferedDownload(any(),
            org.mockito.ArgumentMatchers.eq(Duration.ofSeconds(30)))).thenReturn(responseStream);

        CreationMediaHandle result = new CreationAssetServiceImpl(assetMapper, ossClientProvider)
            .openOwnedTimelineRenderOutput(7L, "99", "88");

        assertThat(result.metadata().usageType()).isNull();
        assertThat(result.metadata().usageOrigin()).isEqualTo(CreationAssetUsageOrigin.TIMELINE_RENDER_OUTPUT);
        assertThat(result.metadata().assetType()).isEqualTo(CreationAssetType.VIDEO);
        verifyRangeRequest("creation-bucket", "creation-assets/7/88.mp4", null);
        result.close();
        verify(responseStream).close();
    }

    @Test
    void rejectsMissingMismatchedCrossOwnerNonReadyAndWrongTypeRenderOutputs() {
        CreationAsset crossOwner = timelineRenderAsset(88L, 99L);
        crossOwner.setOwnerUserId(8L);
        CreationAsset mismatchedTask = timelineRenderAsset(88L, 100L);
        CreationAsset nonReady = timelineRenderAsset(88L, 99L);
        nonReady.setAssetStatus(CreationAssetStatus.PENDING.value());
        CreationAsset wrongType = timelineRenderAsset(88L, 99L);
        wrongType.setAssetType(CreationAssetType.AUDIO.value());
        when(assetMapper.selectOwnedTimelineRenderOutput(7L, 99L, 88L))
            .thenReturn(null, crossOwner, mismatchedTask, nonReady, wrongType);
        CreationAssetServiceImpl service = new CreationAssetServiceImpl(assetMapper);

        for (int index = 0; index < 5; index++) {
            assertThatThrownBy(() -> service.getOwnedTimelineRenderOutput(7L, "99", "88"))
                .isInstanceOfSatisfying(ServiceException.class,
                    exception -> assertThat(exception.getCode())
                        .isEqualTo(TimelineErrorCodes.TIMELINE_ASSET_INVALID));
        }

        verify(assetMapper, times(5)).selectOwnedTimelineRenderOutput(7L, 99L, 88L);
    }

    @Test
    void taskOutputMapperAtomicallyFreezesOwnershipAndAssociationWithoutProjectLatest() throws Exception {
        Method method = CreationAssetMapper.class.getMethod("selectOwnedTimelineRenderOutput",
            long.class, long.class, long.class);
        String sql = String.join(" ", method.getAnnotation(Select.class).value())
            .replaceAll("\\s+", " ");

        assertThat(sql).contains(
            "FROM av_creation_asset asset", "INNER JOIN av_ai_task task",
            "task.task_id = #{taskId}", "task.owner_user_id = #{ownerUserId}",
            "task.task_type = 'timeline_render'", "task.resource_type = 'creation_project'",
            "task.task_status = 'success'", "task.result_asset_id = asset.asset_id",
            "asset.asset_id = #{resultAssetId}", "asset.owner_user_id = #{ownerUserId}",
            "asset.source_ref_id = #{taskId}", "asset.asset_status = 'ready'",
            "asset.asset_type = 'video'", "asset.usage_origin = 'timeline_render_output'",
            "asset.del_flag = '0'");
        assertThat(sql).doesNotContain("av_creation_project", "current_output_asset_id");
    }

    @Test
    void ownedMediaReadFailsClosedWhenProjectOssIsUnavailable() {
        when(assetMapper.selectOne(any(Wrapper.class))).thenReturn(readyAsset(88L, 10L));

        assertThatThrownBy(() -> new CreationAssetServiceImpl(assetMapper, ossClientProvider)
            .openOwnedMediaRange(7L, "88", "bytes=0-2"))
            .isInstanceOf(ServiceException.class)
            .hasMessage("VideoOps 对象存储未启用")
            .extracting("code").isEqualTo(TimelineErrorCodes.TIMELINE_ASSET_INVALID);
        verify(ossClientProvider).getIfAvailable();
        verifyNoInteractions(ossClient);
    }

    @Test
    void uploadRejectsUnsupportedMimeTypeBeforePersisting() {
        assertThatThrownBy(() -> new CreationAssetServiceImpl(assetMapper).uploadOwned(7L,
            new org.dromara.aivideo.creation.dto.CreationAssetUploadDTO("payload.pdf", "application/pdf",
                "image", "upload-key", "request-digest", 3L),
            new ByteArrayInputStream(new byte[] {1, 2, 3})))
            .isInstanceOf(ServiceException.class);
        verify(assetMapper, never()).insert(any(CreationAsset.class));
    }

    @Test
    void uploadReplaysTheSameIdempotencyRequestAndRejectsAChangedDigest() throws Exception {
        when(ossClientProvider.getIfAvailable()).thenReturn(ossClient);
        byte[] firstContent = new byte[] {1, 2, 3};
        CreationAsset replay = readyAsset(88L, firstContent.length);
        replay.setAssetType(CreationAssetType.IMAGE.value());
        replay.setMimeType("image/png");
        replay.setIdempotencyKey("upload-key");
        replay.setRequestDigest(uploadRequestDigest(firstContent, "image"));
        when(assetMapper.insert(any(CreationAsset.class))).thenThrow(new DuplicateKeyException("duplicate"));
        when(assetMapper.selectOne(any(Wrapper.class))).thenReturn(replay);
        when(ossClient.buildPathKey(any(), any())).thenAnswer(invocation ->
            invocation.getArgument(0, String.class) + "/" + invocation.getArgument(1, String.class));
        when(ossClient.upload(any(), any(InputStream.class), org.mockito.ArgumentMatchers.eq(3L)))
            .thenAnswer(invocation -> {
                invocation.getArgument(1, InputStream.class).transferTo(OutputStream.nullOutputStream());
                return null;
            });

        CreationAssetServiceImpl service = new CreationAssetServiceImpl(assetMapper, ossClientProvider);

        assertThat(service.uploadOwned(7L, uploadCommand(), new ByteArrayInputStream(firstContent)).assetId())
            .isEqualTo("88");

        assertThatThrownBy(() -> service.uploadOwned(7L, uploadCommand(),
            new ByteArrayInputStream(new byte[] {9, 9, 9})))
            .isInstanceOfSatisfying(ServiceException.class,
                exception -> assertThat(exception.getCode())
                    .isEqualTo(TimelineErrorCodes.TIMELINE_IDEMPOTENCY_CONFLICT));
    }

    @Test
    void resolvesAReadableOwnedDigitalHumanSourceIntoStableCreationAssets() throws Exception {
        when(ossClientProvider.getIfAvailable()).thenReturn(ossClient);
        AppUserMapper userMapper = mock(AppUserMapper.class);
        DigitalHumanGenerationJobMapper jobMapper = mock(DigitalHumanGenerationJobMapper.class);
        IDigitalHumanGenerationService digitalHumanService = mock(IDigitalHumanGenerationService.class);
        ITimelineMediaRenderService mediaRenderService = mock(ITimelineMediaRenderService.class);
        AppUser user = new AppUser();
        user.setUserId(7L);
        user.setPersonalTenantId(2001L);
        DigitalHumanGenerationJob video = digitalHumanJob(99L, 44L, DigitalHumanJobType.VIDEO_GENERATE,
            "video-private-key", "video/mp4", new byte[] {1, 2, 3});
        DigitalHumanGenerationJob voice = digitalHumanJob(44L, null, DigitalHumanJobType.VOICE_GENERATE,
            "voice-private-key", "audio/wav", new byte[] {4, 5, 6});
        voice.setScriptText("server-owned script");
        byte[] videoContent = new byte[] {1, 2, 3};
        byte[] voiceContent = new byte[] {4, 5, 6};
        when(userMapper.selectById(7L)).thenReturn(user);
        when(digitalHumanService.getJob(org.mockito.ArgumentMatchers.eq(99L), any()))
            .thenReturn(new DigitalHumanJobDTO(99L, 44L, DigitalHumanJobType.VIDEO_GENERATE,
                DigitalHumanJobStatus.SUCCEEDED, null, 100, true, true, null));
        when(jobMapper.selectOwnedById(99L, 2001L, 7L)).thenReturn(video);
        when(jobMapper.selectOwnedById(44L, 2001L, 7L)).thenReturn(voice);
        when(digitalHumanService.getOutputMedia(org.mockito.ArgumentMatchers.eq(99L), any()))
            .thenReturn(new DigitalHumanMediaContentDTO("result.mp4", "video/mp4", videoContent));
        when(digitalHumanService.getOutputMedia(org.mockito.ArgumentMatchers.eq(44L), any()))
            .thenReturn(new DigitalHumanMediaContentDTO("voice.wav", "audio/wav", voiceContent));
        when(mediaRenderService.probe(any())).thenReturn(new TimelineMediaProbeDTO(null,
            CreationAssetType.VIDEO.value(), "mp4",
            1_000L, videoContent.length, 1280, 720, 30, null, null, true, false));
        when(assetMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(assetMapper.insert(any(CreationAsset.class))).thenReturn(1);
        when(ossClient.buildPathKey(any(), any())).thenAnswer(invocation ->
            "videoops-agent/dev/" + invocation.getArgument(0, String.class) + "/"
                + invocation.getArgument(1, String.class));
        when(ossClient.upload(any(), any(InputStream.class), org.mockito.ArgumentMatchers.anyLong()))
            .thenAnswer(invocation -> {
                invocation.getArgument(1, InputStream.class).transferTo(OutputStream.nullOutputStream());
                return null;
            });

        var result = new CreationAssetServiceImpl(assetMapper, null, null, null, null, userMapper,
            jobMapper, digitalHumanService, mediaRenderService, ossClientProvider)
            .resolveDigitalHumanSource(7L, "99");

        assertThat(result.sourceId()).isEqualTo("99");
        assertThat(result.scriptTextSnapshot()).isEqualTo("server-owned script");
        assertThat(result.durationMs()).isEqualTo(1_000L);
        assertThat(result.width()).isEqualTo(1280);
        assertThat(result.height()).isEqualTo(720);
        assertThat(result.frameRate()).isEqualTo(30);
        assertThat(result.primaryAudioAssetId()).isNotBlank();
        ArgumentCaptor<CreationAsset> assets = ArgumentCaptor.forClass(CreationAsset.class);
        verify(assetMapper, times(2)).insert(assets.capture());
        assertThat(assets.getAllValues()).extracting(CreationAsset::getSourceRefId)
            .containsExactly(99L, 44L);
        assertThat(assets.getAllValues()).extracting(CreationAsset::getUsageOrigin)
            .containsOnly(CreationAssetUsageOrigin.DIGITAL_HUMAN_OUTPUT.value());
        assertThat(assets.getAllValues()).extracting(CreationAsset::getStorageKey)
            .allSatisfy(key -> assertThat(key)
                .startsWith("videoops-agent/dev/creation-digital-human/")
                .doesNotContain("private-key"));
    }

    @Test
    void deletionProtectionCoversDraftAndImmutableVersionReferences() {
        assertDeletionBlockedBy(DeletionReference.DRAFT);
        assertDeletionBlockedBy(DeletionReference.IMMUTABLE_VERSION);
    }

    @Test
    void deletionProtectionCoversAllProjectDirectAssetFields() {
        assertDeletionBlockedBy(DeletionReference.PROJECT_BASE_VIDEO);
        assertDeletionBlockedBy(DeletionReference.PROJECT_PRIMARY_AUDIO);
        assertDeletionBlockedBy(DeletionReference.PROJECT_CURRENT_OUTPUT);
    }

    @Test
    void deletionProtectionCoversRootAndExecutionResultAssets() {
        assertDeletionBlockedBy(DeletionReference.ROOT_TASK_RESULT);
        assertDeletionBlockedBy(DeletionReference.EXECUTION_RESULT);
    }

    @Test
    void registersPendingRenderOutputWithOnlyTheServerOwnedDeterministicKey() {
        when(assetMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(assetMapper.insert(any(CreationAsset.class))).thenReturn(1);

        PendingRenderOutputDTO result = new CreationAssetServiceImpl(assetMapper).registerPendingRenderOutput(7L,
            new RegisterPendingRenderOutputDTO("99", "44", "a".repeat(64), "render-key"));

        ArgumentCaptor<CreationAsset> asset = ArgumentCaptor.forClass(CreationAsset.class);
        verify(assetMapper).insert(asset.capture());
        assertThat(result.status()).isEqualTo(CreationAssetStatus.PENDING);
        assertThat(asset.getValue().getStorageKey())
            .isEqualTo("videoops-agent/dev/timeline-renders/7/99/44/" + "a".repeat(64) + ".mp4");
        assertThat(asset.getValue().getOwnerUserId()).isEqualTo(7L);
        assertThat(asset.getValue().getSourceRefId()).isEqualTo(99L);
        assertThat(asset.getValue().getUsageOrigin())
            .isEqualTo(CreationAssetUsageOrigin.TIMELINE_RENDER_OUTPUT.value());
        assertThat(asset.getValue().getAssetStatus()).isEqualTo(CreationAssetStatus.PENDING.value());
    }

    @Test
    void storesPendingRenderOutputAsReadyOnlyAfterTheStreamWasConsumed() throws Exception {
        when(ossClientProvider.getIfAvailable()).thenReturn(ossClient);
        byte[] content = new byte[] {1, 2, 3};
        CreationAsset pending = pendingRenderAsset(88L, 99L);
        TimelineRenderOutputHandle output = org.mockito.Mockito.mock(TimelineRenderOutputHandle.class);
        when(assetMapper.selectOne(any(Wrapper.class))).thenReturn(pending);
        when(assetMapper.updateById(any(CreationAsset.class))).thenReturn(1);
        when(output.metadata()).thenReturn(new TimelineRenderResultDTO("render.mp4", "video/mp4",
            HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content)), content.length,
            1_000L, 1080, 1920, 30));
        when(output.stream()).thenReturn(new ByteArrayInputStream(content));
        when(ossClient.upload(any(), any(InputStream.class), org.mockito.ArgumentMatchers.eq(3L),
            any(Options.class)))
            .thenAnswer(invocation -> {
                invocation.getArgument(1, InputStream.class).transferTo(OutputStream.nullOutputStream());
                return null;
            });

        var result = new CreationAssetServiceImpl(assetMapper, ossClientProvider)
            .storePendingRenderContent(7L, "88", output);

        assertThat(result.assetId()).isEqualTo("88");
        assertThat(result.sha256()).isEqualTo(HexFormat.of().formatHex(
            MessageDigest.getInstance("SHA-256").digest(content)));
        ArgumentCaptor<CreationAsset> saved = ArgumentCaptor.forClass(CreationAsset.class);
        verify(assetMapper).updateById(saved.capture());
        assertThat(saved.getValue().getAssetStatus()).isEqualTo(CreationAssetStatus.READY.value());
        assertThat(saved.getValue().getSizeBytes()).isEqualTo(3L);
        verify(ossClient).upload(org.mockito.ArgumentMatchers.eq(pending.getStorageKey()),
            any(InputStream.class), org.mockito.ArgumentMatchers.eq(3L), any(Options.class));
        verify(output).close();
    }

    @Test
    @SuppressWarnings("unchecked")
    void rejectsRecoveryOutputWithADifferentDigestWithoutOverwritingTheImmutableObject() throws Exception {
        when(ossClientProvider.getIfAvailable()).thenReturn(ossClient);
        byte[] existingContent = {1, 2, 3};
        byte[] retryContent = {4, 5, 6};
        String retryDigest = HexFormat.of().formatHex(
            MessageDigest.getInstance("SHA-256").digest(retryContent));
        CreationAsset pending = pendingRenderAsset(88L, 99L);
        TimelineRenderOutputHandle output = mock(TimelineRenderOutputHandle.class);
        when(assetMapper.selectOne(any(Wrapper.class))).thenReturn(pending);
        when(output.metadata()).thenReturn(new TimelineRenderResultDTO("render.mp4", "video/mp4",
            retryDigest, retryContent.length, 1_000L, 1080, 1920, 30));
        when(output.stream()).thenReturn(new ByteArrayInputStream(retryContent));
        when(ossClient.upload(any(), any(InputStream.class), org.mockito.ArgumentMatchers.eq(3L),
            any(Options.class))).thenAnswer(invocation -> {
                invocation.getArgument(1, InputStream.class).transferTo(OutputStream.nullOutputStream());
                throw immutableObjectAlreadyExists();
            });
        when(ossClient.download(any(),
            org.mockito.ArgumentMatchers.<BiFunction<GetObjectResult, InputStream, Boolean>>any()))
            .thenAnswer(invocation -> {
                BiFunction<GetObjectResult, InputStream, Boolean> transformer = invocation.getArgument(1);
                GetObjectResult result = GetObjectResult.form(pending.getStorageKey(), "etag",
                    LocalDateTime.of(2026, 8, 8, 0, 0), existingContent.length, "video/mp4", null,
                    null, null, null, Map.of());
                return transformer.apply(result, new ByteArrayInputStream(existingContent));
            });

        assertThatThrownBy(() -> new CreationAssetServiceImpl(assetMapper, ossClientProvider)
            .storePendingRenderContent(7L, "88", output)).isInstanceOf(ServiceException.class);

        verify(ossClient).download(any(),
            org.mockito.ArgumentMatchers.<BiFunction<GetObjectResult, InputStream, Boolean>>any());
        verify(ossClient).upload(org.mockito.ArgumentMatchers.eq(pending.getStorageKey()),
            any(InputStream.class), org.mockito.ArgumentMatchers.eq(3L),
            org.mockito.ArgumentMatchers.argThat(options -> "*".equals(options.getIfNoneMatch())));
        verify(assetMapper, never()).updateById(any(CreationAsset.class));
    }

    @Test
    void marksOnlyTheOwnedPendingRenderAssetAsFailedWithoutDiscardingTheCompensationKey() {
        CreationAsset pending = pendingRenderAsset(88L, 99L);
        when(assetMapper.selectOne(any(Wrapper.class))).thenReturn(pending);
        when(assetMapper.updateById(any(CreationAsset.class))).thenReturn(1);

        new CreationAssetServiceImpl(assetMapper).markPendingRenderFailed(7L,
            new RenderOutputFailureDTO("88", "99", "RENDER_FAILED", "safe failure"));

        ArgumentCaptor<CreationAsset> saved = ArgumentCaptor.forClass(CreationAsset.class);
        verify(assetMapper).updateById(saved.capture());
        assertThat(saved.getValue().getAssetStatus()).isEqualTo(CreationAssetStatus.FAILED.value());
        assertThat(saved.getValue().getStorageKey())
            .isEqualTo("videoops-agent/dev/timeline-renders/7/99/44/" + "a".repeat(64) + ".mp4");
    }

    @Test
    void listsOnlyExpiredPendingRenderOutputsForCompensation() {
        CreationAsset pending = pendingRenderAsset(88L, 99L);
        pending.setCreateTime(LocalDateTime.of(2026, 8, 8, 10, 0));
        when(assetMapper.selectList(any(Wrapper.class))).thenReturn(List.of(pending));

        List<PendingRenderOutputDTO> result = new CreationAssetServiceImpl(assetMapper)
            .findCompensatablePending(Instant.parse("2026-08-08T03:00:00Z"), 10);

        assertThat(result).singleElement().satisfies(item -> {
            assertThat(item.assetId()).isEqualTo("88");
            assertThat(item.taskId()).isEqualTo("99");
            assertThat(item.inputVersionId()).isEqualTo("44");
            assertThat(item.outputConfigDigest()).isEqualTo("a".repeat(64));
            assertThat(item.status()).isEqualTo(CreationAssetStatus.PENDING);
        });
    }

    @SuppressWarnings("unchecked")
    private void verifyRangeRequest(String bucket, String key, String range) {
        ArgumentCaptor<Consumer<GetObjectRequest.Builder>> request = ArgumentCaptor.forClass(Consumer.class);
        verify(ossClient).doCustomBufferedDownload(request.capture(),
            org.mockito.ArgumentMatchers.eq(Duration.ofSeconds(30)));
        GetObjectRequest.Builder builder = GetObjectRequest.builder();
        request.getValue().accept(builder);
        GetObjectRequest built = builder.build();
        assertThat(built.bucket()).isEqualTo(bucket);
        assertThat(built.key()).isEqualTo(key);
        assertThat(built.range()).isEqualTo(range);
    }

    private CreationAsset readyAsset(long assetId, long sizeBytes) {
        CreationAsset asset = new CreationAsset();
        asset.setAssetId(assetId);
        asset.setOwnerUserId(7L);
        asset.setAssetType(CreationAssetType.VIDEO.value());
        asset.setUsageOrigin(CreationAssetUsageOrigin.UPLOAD.value());
        asset.setAssetStatus(CreationAssetStatus.READY.value());
        asset.setStorageKey("creation-assets/7/88.mp4");
        asset.setMimeType("video/mp4");
        asset.setSha256("a".repeat(64));
        asset.setSizeBytes(sizeBytes);
        asset.setHasVideoStream(true);
        asset.setHasAudioStream(true);
        asset.setDelFlag("0");
        return asset;
    }

    private CreationAsset pendingRenderAsset(long assetId, long taskId) {
        CreationAsset asset = readyAsset(assetId, 0L);
        asset.setUsageOrigin(CreationAssetUsageOrigin.TIMELINE_RENDER_OUTPUT.value());
        asset.setSourceRefId(taskId);
        asset.setAssetStatus(CreationAssetStatus.PENDING.value());
        asset.setStorageKey("videoops-agent/dev/timeline-renders/7/99/44/" + "a".repeat(64) + ".mp4");
        asset.setSha256("0".repeat(64));
        return asset;
    }

    private CreationAsset timelineRenderAsset(long assetId, long taskId) {
        CreationAsset asset = readyAsset(assetId, 10L);
        asset.setUsageOrigin(CreationAssetUsageOrigin.TIMELINE_RENDER_OUTPUT.value());
        asset.setSourceRefId(taskId);
        return asset;
    }

    private CreationAssetUploadDTO uploadCommand() {
        return new CreationAssetUploadDTO("cover.png", "image/png", "image", "upload-key", "ignored", 3L);
    }

    private String uploadRequestDigest(byte[] content, String usageIntent) throws Exception {
        String contentDigest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        MessageDigest request = MessageDigest.getInstance("SHA-256");
        request.update(contentDigest.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        request.update((byte) ':');
        request.update(usageIntent.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(request.digest());
    }

    private DigitalHumanGenerationJob digitalHumanJob(long id, Long parentId, DigitalHumanJobType type,
                                                       String key, String mediaType, byte[] content) throws Exception {
        DigitalHumanGenerationJob job = new DigitalHumanGenerationJob();
        job.setId(id);
        job.setParentJobId(parentId);
        job.setOwnerUserId(7L);
        job.setTenantId(2001L);
        job.setJobType(type);
        job.setStatus(DigitalHumanJobStatus.SUCCEEDED);
        job.setOutputMediaKey(key);
        job.setOutputMediaType(mediaType);
        job.setOutputMediaSize((long) content.length);
        job.setOutputMediaSha256(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content)));
        return job;
    }

    private void assertDeletionBlockedBy(DeletionReference reference) {
        TimelineAssetRefMapper assetRefMapper = mock(TimelineAssetRefMapper.class);
        CreationProjectMapper projectMapper = mock(CreationProjectMapper.class);
        AiTaskMapper taskMapper = mock(AiTaskMapper.class);
        AiTaskExecutionMapper executionMapper = mock(AiTaskExecutionMapper.class);
        when(assetMapper.selectOne(any(Wrapper.class))).thenReturn(readyAsset(88L, 10L));
        when(assetRefMapper.selectCount(any())).thenReturn(reference.isTimeline() ? 1L : 0L);
        when(projectMapper.selectCount(any())).thenReturn(reference.isProject() ? 1L : 0L);
        when(taskMapper.selectCount(any())).thenReturn(reference == DeletionReference.ROOT_TASK_RESULT ? 1L : 0L);
        when(executionMapper.selectCount(any())).thenReturn(reference == DeletionReference.EXECUTION_RESULT ? 1L : 0L);

        assertThatThrownBy(() -> new CreationAssetServiceImpl(assetMapper, assetRefMapper, projectMapper,
            taskMapper, executionMapper).assertAssetDeletable(7L, "88"))
            .isInstanceOfSatisfying(ServiceException.class,
                exception -> assertThat(exception.getCode()).isEqualTo(TimelineErrorCodes.TIMELINE_ASSET_INVALID));
    }

    private enum DeletionReference {
        DRAFT,
        IMMUTABLE_VERSION,
        PROJECT_BASE_VIDEO,
        PROJECT_PRIMARY_AUDIO,
        PROJECT_CURRENT_OUTPUT,
        ROOT_TASK_RESULT,
        EXECUTION_RESULT;

        boolean isTimeline() {
            return this == DRAFT || this == IMMUTABLE_VERSION;
        }

        boolean isProject() {
            return this == PROJECT_BASE_VIDEO || this == PROJECT_PRIMARY_AUDIO
                || this == PROJECT_CURRENT_OUTPUT;
        }
    }

    private S3StorageException immutableObjectAlreadyExists() {
        return new S3StorageException(software.amazon.awssdk.services.s3.model.S3Exception.builder()
            .statusCode(412).message("precondition failed").build());
    }
}
