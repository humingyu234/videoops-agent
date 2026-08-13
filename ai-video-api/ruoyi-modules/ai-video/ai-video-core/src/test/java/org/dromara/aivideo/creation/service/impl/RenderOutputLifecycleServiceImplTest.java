package org.dromara.aivideo.creation.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.dromara.aivideo.creation.domain.CreationAsset;
import org.dromara.aivideo.creation.domain.CreationProject;
import org.dromara.aivideo.creation.dto.PendingRenderOutputDTO;
import org.dromara.aivideo.creation.enums.CreationAssetStatus;
import org.dromara.aivideo.creation.mapper.CreationAssetMapper;
import org.dromara.aivideo.creation.mapper.CreationProjectMapper;
import org.dromara.aivideo.creation.service.ICreationAssetService;
import org.dromara.aivideo.creation.service.IRenderOutputLifecycleService;
import org.dromara.aivideo.task.domain.AiTask;
import org.dromara.aivideo.task.domain.AiTaskAttempt;
import org.dromara.aivideo.task.domain.AiTaskExecution;
import org.dromara.aivideo.task.dto.AiTaskLeaseDTO;
import org.dromara.aivideo.task.enums.AiTaskExecutionStatus;
import org.dromara.aivideo.task.enums.AiTaskStatus;
import org.dromara.aivideo.task.enums.AiTaskType;
import org.dromara.aivideo.task.mapper.AiTaskAttemptMapper;
import org.dromara.aivideo.task.mapper.AiTaskExecutionMapper;
import org.dromara.aivideo.task.mapper.AiTaskMapper;
import org.dromara.aivideo.timeline.dto.TimelineRenderResultDTO;
import org.dromara.aivideo.timeline.service.TimelineRenderOutputHandle;
import org.dromara.common.oss.client.OssClient;
import org.dromara.common.oss.exception.S3StorageException;
import org.dromara.common.oss.factory.OssFactory;
import org.dromara.common.oss.model.GetObjectResult;
import org.dromara.common.oss.model.Options;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Map;
import java.util.function.BiFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("dev")
class RenderOutputLifecycleServiceImplTest {

    @Mock
    private ICreationAssetService assetService;
    @Mock
    private CreationAssetMapper assetMapper;
    @Mock
    private CreationProjectMapper projectMapper;
    @Mock
    private AiTaskMapper taskMapper;
    @Mock
    private AiTaskExecutionMapper executionMapper;
    @Mock
    private AiTaskAttemptMapper attemptMapper;
    @Mock
    private OssClient ossClient;
    @Mock
    private TimelineRenderOutputHandle output;

    @BeforeAll
    static void initializeLambdaMetadata() {
        initialize(CreationAsset.class);
        initialize(CreationProject.class);
        initialize(AiTask.class);
        initialize(AiTaskExecution.class);
        initialize(AiTaskAttempt.class);
    }

    @Test
    void uploadsOutsideTheFinalCasThenMakesAssetExecutionTaskAndProjectReadyTogether() throws Exception {
        byte[] bytes = {1, 2, 3};
        String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        AiTask task = task();
        AiTaskExecution execution = execution();
        CreationAsset asset = pendingAsset();
        when(taskMapper.selectOne(any(Wrapper.class))).thenReturn(task);
        when(executionMapper.selectOne(any(Wrapper.class))).thenReturn(execution);
        when(assetMapper.selectOne(any(Wrapper.class))).thenReturn(asset);
        when(attemptMapper.selectOne(any(Wrapper.class))).thenReturn(runningAttempt());
        when(assetMapper.update(any(CreationAsset.class), any(LambdaUpdateWrapper.class))).thenReturn(1);
        when(executionMapper.update(any(AiTaskExecution.class), any(LambdaUpdateWrapper.class))).thenReturn(1);
        when(attemptMapper.update(any(AiTaskAttempt.class), any(LambdaUpdateWrapper.class))).thenReturn(1);
        when(taskMapper.update(any(AiTask.class), any(LambdaUpdateWrapper.class))).thenReturn(1);
        when(projectMapper.update(any(CreationProject.class), any(LambdaUpdateWrapper.class))).thenReturn(1);
        when(output.metadata()).thenReturn(new TimelineRenderResultDTO("result.mp4", "video/mp4", digest,
            bytes.length, 1_000L, 1080, 1920, 30));
        when(output.stream()).thenReturn(new ByteArrayInputStream(bytes));
        when(ossClient.upload(any(), any(InputStream.class), anyLong(), any(Options.class))).thenAnswer(invocation -> {
            invocation.getArgument(1, InputStream.class).transferTo(OutputStream.nullOutputStream());
            return null;
        });

        try (MockedStatic<OssFactory> factory = mockStatic(OssFactory.class)) {
            factory.when(OssFactory::instance).thenReturn(ossClient);

            boolean completed = service().storeAndComplete(lease(), pending(), output,
                Instant.parse("2026-08-08T00:00:00Z"));

            assertThat(completed).isTrue();
        }

        verify(ossClient).upload(any(), any(InputStream.class), anyLong(), any(Options.class));
        verify(output).close();
        verify(assetMapper).update(any(CreationAsset.class), any(LambdaUpdateWrapper.class));
        ArgumentCaptor<AiTaskExecution> executionUpdate = ArgumentCaptor.forClass(AiTaskExecution.class);
        verify(executionMapper).update(executionUpdate.capture(), any(LambdaUpdateWrapper.class));
        assertThat(executionUpdate.getValue().getRowVersion()).isNull();
        ArgumentCaptor<AiTaskAttempt> attemptUpdate = ArgumentCaptor.forClass(AiTaskAttempt.class);
        verify(attemptMapper).update(attemptUpdate.capture(), any(LambdaUpdateWrapper.class));
        assertThat(attemptUpdate.getValue().getRowVersion()).isNull();
        ArgumentCaptor<AiTask> taskUpdate = ArgumentCaptor.forClass(AiTask.class);
        verify(taskMapper).update(taskUpdate.capture(), any(LambdaUpdateWrapper.class));
        assertThat(taskUpdate.getValue().getRowVersion()).isNull();
        verify(projectMapper).update(any(CreationProject.class), any(LambdaUpdateWrapper.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void reusesTheExistingImmutableObjectWhenRecoveryProducesTheSameDigest() throws Exception {
        byte[] bytes = {1, 2, 3};
        String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        when(taskMapper.selectOne(any(Wrapper.class))).thenReturn(task());
        when(executionMapper.selectOne(any(Wrapper.class))).thenReturn(execution());
        when(assetMapper.selectOne(any(Wrapper.class))).thenReturn(pendingAsset());
        when(attemptMapper.selectOne(any(Wrapper.class))).thenReturn(runningAttempt());
        when(assetMapper.update(any(CreationAsset.class), any(LambdaUpdateWrapper.class))).thenReturn(1);
        when(executionMapper.update(any(AiTaskExecution.class), any(LambdaUpdateWrapper.class))).thenReturn(1);
        when(attemptMapper.update(any(AiTaskAttempt.class), any(LambdaUpdateWrapper.class))).thenReturn(1);
        when(taskMapper.update(any(AiTask.class), any(LambdaUpdateWrapper.class))).thenReturn(1);
        when(projectMapper.update(any(CreationProject.class), any(LambdaUpdateWrapper.class))).thenReturn(1);
        when(output.metadata()).thenReturn(new TimelineRenderResultDTO("result.mp4", "video/mp4", digest,
            bytes.length, 1_000L, 1080, 1920, 30));
        when(output.stream()).thenReturn(new ByteArrayInputStream(bytes));
        when(ossClient.upload(any(), any(InputStream.class), anyLong(), any(Options.class))).thenAnswer(invocation -> {
            invocation.getArgument(1, InputStream.class).transferTo(OutputStream.nullOutputStream());
            throw immutableObjectAlreadyExists();
        });
        when(ossClient.download(any(),
            org.mockito.ArgumentMatchers.<BiFunction<GetObjectResult, InputStream, Boolean>>any()))
            .thenAnswer(invocation -> {
                BiFunction<GetObjectResult, InputStream, Boolean> transformer = invocation.getArgument(1);
                GetObjectResult result = GetObjectResult.form(pendingAsset().getStorageKey(), "etag",
                    LocalDateTime.of(2026, 8, 8, 0, 0), bytes.length, "video/mp4", null, null,
                    null, null, Map.of());
                return transformer.apply(result, new ByteArrayInputStream(bytes));
            });

        try (MockedStatic<OssFactory> factory = mockStatic(OssFactory.class)) {
            factory.when(OssFactory::instance).thenReturn(ossClient);

            assertThat(service().storeAndComplete(lease(), pending(), output,
                Instant.parse("2026-08-08T00:00:00Z"))).isTrue();
        }

        verify(ossClient).upload(any(), any(InputStream.class), anyLong(),
            org.mockito.ArgumentMatchers.argThat(options -> "*".equals(options.getIfNoneMatch())));
        verify(ossClient).download(any(),
            org.mockito.ArgumentMatchers.<BiFunction<GetObjectResult, InputStream, Boolean>>any());
    }

    private S3StorageException immutableObjectAlreadyExists() {
        return new S3StorageException(software.amazon.awssdk.services.s3.model.S3Exception.builder()
            .statusCode(412).message("precondition failed").build());
    }

    @Test
    void expiredLeaseDoesNotUploadOrAdvanceRenderFacts() throws Exception {
        AiTask task = task();
        AiTaskExecution execution = execution();
        execution.setLeaseExpiresAt(LocalDateTime.of(2026, 8, 7, 23, 59));
        when(taskMapper.selectOne(any(Wrapper.class))).thenReturn(task);
        when(executionMapper.selectOne(any(Wrapper.class))).thenReturn(execution);

        boolean completed = service().storeAndComplete(lease(), pending(), output,
            Instant.parse("2026-08-08T00:00:00Z"));

        assertThat(completed).isFalse();

        verify(ossClient, never()).upload(any(), any(InputStream.class), anyLong());
        verify(assetMapper, never()).update(any(CreationAsset.class), any(LambdaUpdateWrapper.class));
        verify(executionMapper, never()).update(any(AiTaskExecution.class), any(LambdaUpdateWrapper.class));
        verify(taskMapper, never()).update(any(AiTask.class), any(LambdaUpdateWrapper.class));
        verify(projectMapper, never()).update(any(CreationProject.class), any(LambdaUpdateWrapper.class));
        verify(output).close();
    }

    @Test
    void finalizationRechecksTheLeaseAfterTheObjectUploadCompletes() throws Exception {
        byte[] bytes = {1, 2, 3};
        String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        AiTask task = task();
        AiTaskExecution execution = execution();
        execution.setLeaseExpiresAt(LocalDateTime.of(2026, 8, 8, 0, 0, 30));
        Instant beforeUpload = Instant.parse("2026-08-08T00:00:00Z");
        Instant afterUpload = Instant.parse("2026-08-08T00:01:00Z");
        when(taskMapper.selectOne(any(Wrapper.class))).thenReturn(task);
        when(executionMapper.selectOne(any(Wrapper.class))).thenReturn(execution);
        when(assetMapper.selectOne(any(Wrapper.class))).thenReturn(pendingAsset());
        when(output.metadata()).thenReturn(new TimelineRenderResultDTO("result.mp4", "video/mp4", digest,
            bytes.length, 1_000L, 1080, 1920, 30));
        when(output.stream()).thenReturn(new ByteArrayInputStream(bytes));
        when(ossClient.upload(any(), any(InputStream.class), anyLong(), any(Options.class))).thenAnswer(invocation -> {
            invocation.getArgument(1, InputStream.class).transferTo(OutputStream.nullOutputStream());
            return null;
        });

        try (MockedStatic<OssFactory> factory = mockStatic(OssFactory.class);
             MockedStatic<Instant> clock = mockStatic(Instant.class, org.mockito.Mockito.CALLS_REAL_METHODS)) {
            factory.when(OssFactory::instance).thenReturn(ossClient);
            clock.when(Instant::now).thenReturn(afterUpload);

            boolean completed = service().storeAndComplete(lease(), pending(), output, beforeUpload);

            assertThat(completed).isFalse();
        }

        verify(ossClient).upload(any(), any(InputStream.class), anyLong(), any(Options.class));
        verify(assetMapper, never()).update(any(CreationAsset.class), any(LambdaUpdateWrapper.class));
        verify(executionMapper, never()).update(any(AiTaskExecution.class), any(LambdaUpdateWrapper.class));
        verify(taskMapper, never()).update(any(AiTask.class), any(LambdaUpdateWrapper.class));
        verify(projectMapper, never()).update(any(CreationProject.class), any(LambdaUpdateWrapper.class));
        verify(output).close();
    }

    private IRenderOutputLifecycleService service() {
        return new RenderOutputLifecycleServiceImpl(assetService, assetMapper, projectMapper, taskMapper,
            executionMapper, attemptMapper);
    }

    private AiTaskLeaseDTO lease() {
        return new AiTaskLeaseDTO("701", "801", "901", "lease-token", "timeline-worker-a", "7", "601",
            1, 1, 4);
    }

    private PendingRenderOutputDTO pending() {
        return new PendingRenderOutputDTO("88", "701", "601", "a".repeat(64),
            CreationAssetStatus.PENDING, Instant.parse("2026-08-08T00:00:00Z"));
    }

    private AiTask task() {
        AiTask task = new AiTask();
        task.setTaskId(701L);
        task.setOwnerUserId(7L);
        task.setTaskType(AiTaskType.TIMELINE_RENDER.value());
        task.setResourceId(900L);
        task.setTaskStatus(AiTaskStatus.RUNNING.value());
        task.setStage("encoding");
        task.setProgressPercent(80);
        task.setRowVersion(2L);
        task.setActiveExecutionId(801L);
        task.setCancelRequested(false);
        task.setActorType("app_user");
        task.setActorId(7L);
        return task;
    }

    private AiTaskExecution execution() {
        AiTaskExecution execution = new AiTaskExecution();
        execution.setTaskExecutionId(801L);
        execution.setOwnerUserId(7L);
        execution.setTaskId(701L);
        execution.setResourceId(900L);
        execution.setExecutionNo(1L);
        execution.setExecutionStatus(AiTaskExecutionStatus.RUNNING.value());
        execution.setStage("encoding");
        execution.setProgressPercent(80);
        execution.setRowVersion(4L);
        execution.setLeaseOwner("timeline-worker-a");
        execution.setLeaseToken("lease-token");
        execution.setLeaseExpiresAt(LocalDateTime.of(2099, 1, 1, 0, 0));
        execution.setInputVersionId(601L);
        execution.setOutputConfigDigest("a".repeat(64));
        execution.setCancelRequestedSnapshot(false);
        execution.setActorType("app_user");
        execution.setActorId(7L);
        return execution;
    }

    private CreationAsset pendingAsset() {
        CreationAsset asset = new CreationAsset();
        asset.setAssetId(88L);
        asset.setOwnerUserId(7L);
        asset.setUsageOrigin("timeline_render_output");
        asset.setSourceRefId(701L);
        asset.setAssetStatus("pending");
        asset.setStorageKey("timeline-renders/7/701/601/" + "a".repeat(64) + ".mp4");
        asset.setSha256("0".repeat(64));
        asset.setSizeBytes(0L);
        asset.setActorType("app_user");
        asset.setActorId(7L);
        return asset;
    }

    private AiTaskAttempt runningAttempt() {
        AiTaskAttempt attempt = new AiTaskAttempt();
        attempt.setTaskAttemptId(901L);
        attempt.setOwnerUserId(7L);
        attempt.setTaskId(701L);
        attempt.setTaskExecutionId(801L);
        attempt.setAttemptNo(1L);
        attempt.setAttemptStatus("running");
        attempt.setRowVersion(0L);
        attempt.setWorkerId("timeline-worker-a");
        attempt.setLeaseTokenDigest("a".repeat(64));
        attempt.setActorType("app_user");
        attempt.setActorId(7L);
        return attempt;
    }

    private static void initialize(Class<?> type) {
        if (TableInfoHelper.getTableInfo(type) == null) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), type);
        }
    }
}
