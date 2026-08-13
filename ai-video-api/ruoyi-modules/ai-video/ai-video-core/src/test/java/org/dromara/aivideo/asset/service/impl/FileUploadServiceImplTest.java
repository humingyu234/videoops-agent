package org.dromara.aivideo.asset.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.aivideo.asset.domain.AssetFile;
import org.dromara.aivideo.asset.domain.UploadSession;
import org.dromara.aivideo.asset.dto.CreateUploadSessionDTO;
import org.dromara.aivideo.asset.dto.RunningHubUploadedFileDTO;
import org.dromara.aivideo.asset.dto.UploadSessionDTO;
import org.dromara.aivideo.asset.mapper.AssetFileMapper;
import org.dromara.aivideo.asset.mapper.UploadSessionMapper;
import org.dromara.aivideo.asset.service.IRunningHubFileTransferService;
import org.dromara.aivideo.identity.dto.AppPrincipalSnapshotDTO;
import org.dromara.aivideo.identity.dto.AppWorkspaceSessionSnapshotDTO;
import org.dromara.aivideo.workflow.dto.WorkflowTemplateDTOs;
import org.dromara.aivideo.workflow.service.IWorkflowTemplateService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("dev")
class FileUploadServiceImplTest {

    @BeforeAll
    static void initializeEntityMetadata() {
        initializeEntityMetadata(UploadSession.class);
        initializeEntityMetadata(AssetFile.class);
    }

    @Test
    void createsAnOwnerScopedUploadSessionWithoutCreatingALocalFileObject() {
        UploadSessionMapper uploadSessionMapper = mock(UploadSessionMapper.class);
        AssetFileMapper assetFileMapper = mock(AssetFileMapper.class);
        IRunningHubFileTransferService runningHubFileTransferService = mock(IRunningHubFileTransferService.class);
        IWorkflowTemplateService workflowTemplateService = mock(IWorkflowTemplateService.class);
        FileUploadServiceImpl service = new FileUploadServiceImpl(uploadSessionMapper, assetFileMapper,
            runningHubFileTransferService, workflowTemplateService);
        when(workflowTemplateService.queryCreationConfig("101")).thenReturn(workflowConfig());
        doAnswer(invocation -> {
            invocation.<UploadSession>getArgument(0).setUploadSessionId(2001L);
            return 1;
        }).when(uploadSessionMapper).insert(any(UploadSession.class));

        UploadSessionDTO result = service.createWorkflowInputSession(new CreateUploadSessionDTO(
            "101", "schema-1", "promptImage", "hero.png", "image/png", 1024L, "idem-1"), principal());

        assertThat(result.uploadId()).isEqualTo("2001");
        assertThat(result.fileId()).isNull();
        assertThat(result.status()).isEqualTo("initialized");
        assertThat(result.singlePutUrl()).isNull();
        assertThat(result.requiredHeaders()).isEmpty();
        verify(uploadSessionMapper).insert(any(UploadSession.class));
        verifyNoLocalObjectStorageDependencies(runningHubFileTransferService);
    }

    @Test
    void reusesTheSameOwnerScopedUploadSessionForTheSameIdempotencyKey() {
        UploadSessionMapper uploadSessionMapper = mock(UploadSessionMapper.class);
        AssetFileMapper assetFileMapper = mock(AssetFileMapper.class);
        IRunningHubFileTransferService runningHubFileTransferService = mock(IRunningHubFileTransferService.class);
        IWorkflowTemplateService workflowTemplateService = mock(IWorkflowTemplateService.class);
        FileUploadServiceImpl service = new FileUploadServiceImpl(uploadSessionMapper, assetFileMapper,
            runningHubFileTransferService, workflowTemplateService);
        when(workflowTemplateService.queryCreationConfig("101")).thenReturn(workflowConfig());
        UploadSession session = uploadSession("created");
        when(uploadSessionMapper.selectOne(any())).thenReturn(session);

        UploadSessionDTO result = service.createWorkflowInputSession(new CreateUploadSessionDTO(
            "101", "schema-1", "promptImage", "hero.png", "image/png", 1024L, "idem-1"), principal());

        assertThat(result.uploadId()).isEqualTo("2001");
        assertThat(result.fileId()).isNull();
        assertThat(result.status()).isEqualTo("initialized");
        assertThat(result.singlePutUrl()).isNull();
        verify(uploadSessionMapper, never()).insert(any(UploadSession.class));
    }

    @Test
    void streamsContentToRunningHubAndStoresOnlyTheRemoteFileReference() throws Exception {
        UploadSessionMapper uploadSessionMapper = mock(UploadSessionMapper.class);
        AssetFileMapper assetFileMapper = mock(AssetFileMapper.class);
        IRunningHubFileTransferService runningHubFileTransferService = mock(IRunningHubFileTransferService.class);
        IWorkflowTemplateService workflowTemplateService = mock(IWorkflowTemplateService.class);
        FileUploadServiceImpl service = new FileUploadServiceImpl(uploadSessionMapper, assetFileMapper,
            runningHubFileTransferService, workflowTemplateService);
        UploadSession session = uploadSession("created");
        when(uploadSessionMapper.selectOne(any())).thenReturn(session);
        when(uploadSessionMapper.update(any(), any())).thenReturn(1);
        doAnswer(invocation -> {
            invocation.<AssetFile>getArgument(0).setAssetId(3001L);
            return 1;
        }).when(assetFileMapper).insert(any(AssetFile.class));
        AssetFile persistedAsset = new AssetFile();
        persistedAsset.setAssetId(3001L);
        persistedAsset.setTenantId(1L);
        persistedAsset.setWorkspaceId("personal-9");
        persistedAsset.setOwnerId(9L);
        persistedAsset.setCategory("workflow_input");
        persistedAsset.setObjectKey("runninghub-file-name.png");
        persistedAsset.setStatus("ready");
        when(assetFileMapper.selectOne(any())).thenReturn(persistedAsset);
        when(runningHubFileTransferService.uploadWorkflowInput(eq("101"), eq("hero.png"), eq("image/png"),
            eq(1024L), any(InputStream.class))).thenAnswer(invocation -> {
                invocation.<InputStream>getArgument(4).readAllBytes();
                return new RunningHubUploadedFileDTO("runninghub-file-name.png");
            });

        UploadSessionDTO result = service.transferWorkflowInputContent("2001", "image/png", 1024L,
            new ByteArrayInputStream(new byte[1024]), principal());

        assertThat(result.status()).isEqualTo("completed");
        assertThat(result.assetId()).isEqualTo("3001");
        verify(runningHubFileTransferService).uploadWorkflowInput(eq("101"), eq("hero.png"), eq("image/png"), eq(1024L),
            org.mockito.ArgumentMatchers.any(InputStream.class));
        verify(assetFileMapper).insert(org.mockito.ArgumentMatchers.<AssetFile>argThat(asset ->
            asset.getFileId() == null && "runninghub-file-name.png".equals(asset.getObjectKey())));
    }

    @Test
    void createsANewAssetForEachUploadEvenWhenRunningHubReturnsTheSameRemoteFileReference() throws Exception {
        UploadSessionMapper uploadSessionMapper = mock(UploadSessionMapper.class);
        AssetFileMapper assetFileMapper = mock(AssetFileMapper.class);
        IRunningHubFileTransferService runningHubFileTransferService = mock(IRunningHubFileTransferService.class);
        IWorkflowTemplateService workflowTemplateService = mock(IWorkflowTemplateService.class);
        FileUploadServiceImpl service = new FileUploadServiceImpl(uploadSessionMapper, assetFileMapper,
            runningHubFileTransferService, workflowTemplateService);
        UploadSession session = uploadSession("created");
        when(uploadSessionMapper.selectOne(any())).thenReturn(session);
        when(uploadSessionMapper.update(any(), any())).thenReturn(1);
        doAnswer(invocation -> {
            invocation.<AssetFile>getArgument(0).setAssetId(3002L);
            return 1;
        }).when(assetFileMapper).insert(any(AssetFile.class));
        AssetFile persistedAsset = new AssetFile();
        persistedAsset.setAssetId(3002L);
        persistedAsset.setTenantId(1L);
        persistedAsset.setWorkspaceId("personal-9");
        persistedAsset.setOwnerId(9L);
        persistedAsset.setObjectKey("runninghub-file-name.png");
        persistedAsset.setStatus("ready");
        when(assetFileMapper.selectOne(any())).thenReturn(persistedAsset);
        when(runningHubFileTransferService.uploadWorkflowInput(eq("101"), eq("hero.png"), eq("image/png"),
            eq(1024L), any(InputStream.class))).thenAnswer(invocation -> {
                invocation.<InputStream>getArgument(4).readAllBytes();
                return new RunningHubUploadedFileDTO("runninghub-file-name.png");
            });

        UploadSessionDTO result = service.transferWorkflowInputContent("2001", "image/png", 1024L,
            new ByteArrayInputStream(new byte[1024]), principal());

        assertThat(result.status()).isEqualTo("completed");
        assertThat(result.assetId()).isEqualTo("3002");
        verify(assetFileMapper).insert(any(AssetFile.class));
    }

    private static void verifyNoLocalObjectStorageDependencies(IRunningHubFileTransferService transferService) {
        verify(transferService, never()).uploadWorkflowInput(any(), any(), any(), any(Long.class), any(InputStream.class));
    }

    private static UploadSession uploadSession(String status) {
        UploadSession session = new UploadSession();
        session.setUploadSessionId(2001L);
        session.setTenantId(1L);
        session.setWorkspaceId("personal-9");
        session.setOwnerUserId(9L);
        session.setTemplateId(101L);
        session.setSchemaHash("schema-1");
        session.setInputKey("promptImage");
        session.setOriginalFileName("hero.png");
        session.setDeclaredContentType("image/png");
        session.setDeclaredSizeBytes(1024L);
        session.setStatus(status);
        session.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        return session;
    }

    private static AppPrincipalSnapshotDTO principal() {
        return new AppPrincipalSnapshotDTO(9L, "creator", "web", 1L, 1L, 1L, 1L,
            new AppWorkspaceSessionSnapshotDTO("personal-9", "personal", 1L, "app_user", 9L,
                "app_user", 9L, "personal_creator", Set.of("aivideo:asset:upload"), 1L, null));
    }

    private static WorkflowTemplateDTOs.CreationConfig workflowConfig() {
        return new WorkflowTemplateDTOs.CreationConfig("101", "form-1", "schema-1", List.of(
            new WorkflowTemplateDTOs.InputField("promptImage", "promptImage", "参考图", null,
                "upload", "asset", true, null, null, List.of(),
                new WorkflowTemplateDTOs.InputConstraints(null, null, null, null, null, null, "image",
                    List.of("png"), List.of("image/png"), "2048"))
        ), 30, new WorkflowTemplateDTOs.BillingPolicy("free"));
    }

    private static void initializeEntityMetadata(Class<?> entityType) {
        if (TableInfoHelper.getTableInfo(entityType) == null) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), entityType);
        }
    }
}
