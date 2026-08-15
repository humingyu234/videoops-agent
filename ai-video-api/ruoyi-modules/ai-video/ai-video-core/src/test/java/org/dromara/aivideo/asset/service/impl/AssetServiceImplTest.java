package org.dromara.aivideo.asset.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.aivideo.asset.PortraitImageMetadata;
import org.dromara.aivideo.asset.PortraitImageValidator;
import org.dromara.aivideo.asset.VoiceSampleValidator;
import org.dromara.aivideo.asset.domain.AssetFile;
import org.dromara.aivideo.asset.dto.AssetDTO;
import org.dromara.aivideo.asset.dto.UploadPortraitImageDTO;
import org.dromara.aivideo.asset.dto.UploadVoiceSampleDTO;
import org.dromara.aivideo.asset.mapper.AssetFileMapper;
import org.dromara.aivideo.identity.dto.AppPrincipalSnapshotDTO;
import org.dromara.aivideo.identity.dto.AppWorkspaceSessionSnapshotDTO;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.oss.client.OssClient;
import org.dromara.common.oss.model.Options;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("dev")
class AssetServiceImplTest {
    @Mock private AssetFileMapper assetMapper;
    @Mock private PortraitImageValidator imageValidator;
    @Mock private OssClient ossClient;
    @Mock private ObjectProvider<OssClient> ossClientProvider;
    private AssetServiceImpl service;
    private AppPrincipalSnapshotDTO principal;

    @BeforeAll
    static void setUpMybatisMetadata() {
        if (TableInfoHelper.getTableInfo(AssetFile.class) == null) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), AssetFile.class);
        }
    }

    @BeforeEach
    void setUp() {
        service = new AssetServiceImpl(assetMapper, imageValidator, new VoiceSampleValidator(), ossClientProvider);
        AppWorkspaceSessionSnapshotDTO workspace = new AppWorkspaceSessionSnapshotDTO(
            "personal:7", "personal", 1L, "app_user", 7L, "app_user", 7L,
            "owner", Set.of("aivideo:voice:upload"), 1L, null);
        principal = new AppPrincipalSnapshotDTO(7L, "tester", "web", 1L, 1L, 1L, 1L, workspace);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void uploadVoiceSampleStoresCanonicalMimeWhenClientMimeMissing(String contentType) {
        useOssClient();
        byte[] mp3 = mp3Frames();
        when(ossClient.buildPathKey(anyString(), anyString()))
            .thenReturn("videoops-agent/dev/voices/7/sample.mp3");
        when(assetMapper.insert(any(AssetFile.class))).thenAnswer(invocation -> {
            AssetFile asset = invocation.getArgument(0);
            asset.setAssetId(9L);
            return 1;
        });

        AssetDTO result = service.uploadVoiceSample(new UploadVoiceSampleDTO(
            "sample.mp3", contentType, mp3.length, new ByteArrayInputStream(mp3)), principal);

        assertThat(result.contentType()).isEqualTo("audio/mpeg");
        verify(ossClientProvider).getIfAvailable();
        verify(ossClient).upload(eq("videoops-agent/dev/voices/7/sample.mp3"),
            any(BufferedInputStream.class), eq((long) mp3.length));
    }

    @Test
    void uploadPortraitImageUsesVerifiedContentTypeAsObjectMetadata() {
        useOssClient();
        byte[] content = {1, 2, 3};
        AppPrincipalSnapshotDTO portraitPrincipal = new AppPrincipalSnapshotDTO(
            7L, "tester", "web", 1L, 1L, 1L, 1L, new AppWorkspaceSessionSnapshotDTO(
                "personal:7", "personal", 1L, "app_user", 7L, "app_user", 7L,
                "owner", Set.of("aivideo:portrait:add"), 1L, null));
        when(imageValidator.validate(any(), any(), any()))
            .thenReturn(new PortraitImageMetadata("webp", 2, 3, content.length));
        when(ossClient.buildPathKey(anyString(), anyString()))
            .thenReturn("videoops-agent/dev/portraits/7/new.webp");
        when(assetMapper.insert(any(AssetFile.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, AssetFile.class).setAssetId(10L);
            return 1;
        });

        service.uploadPortraitImage(new UploadPortraitImageDTO("portrait.webp", "image/webp", content), portraitPrincipal);

        ArgumentCaptor<Options> options = ArgumentCaptor.forClass(Options.class);
        verify(ossClient).upload(eq("videoops-agent/dev/portraits/7/new.webp"), eq(content), options.capture());
        assertThat(options.getValue().getContentType()).isEqualTo("image/webp");
    }

    @Test
    void rejectsLegacyPortraitKeysBeforeUploadOrPersistence() {
        useOssClient();
        byte[] content = {1, 2, 3};
        AppPrincipalSnapshotDTO portraitPrincipal = new AppPrincipalSnapshotDTO(
            7L, "tester", "web", 1L, 1L, 1L, 1L, new AppWorkspaceSessionSnapshotDTO(
                "personal:7", "personal", 1L, "app_user", 7L, "app_user", 7L,
                "owner", Set.of("aivideo:portrait:add"), 1L, null));
        when(imageValidator.validate(any(), any(), any()))
            .thenReturn(new PortraitImageMetadata("webp", 2, 3, content.length));
        when(ossClient.buildPathKey(anyString(), anyString())).thenReturn("ai-video/portraits/7/new.webp");

        assertThatThrownBy(() -> service.uploadPortraitImage(
            new UploadPortraitImageDTO("portrait.webp", "image/webp", content), portraitPrincipal))
            .isInstanceOf(IllegalArgumentException.class);

        verify(ossClient, never()).upload(anyString(), any(byte[].class), any(Options.class));
        verify(assetMapper, never()).insert(any(AssetFile.class));
    }

    @Test
    void failsClosedBeforeStorageOrPersistenceWhenProjectOssIsUnavailable() {
        byte[] content = {1, 2, 3};
        AppPrincipalSnapshotDTO portraitPrincipal = new AppPrincipalSnapshotDTO(
            7L, "tester", "web", 1L, 1L, 1L, 1L, new AppWorkspaceSessionSnapshotDTO(
                "personal:7", "personal", 1L, "app_user", 7L, "app_user", 7L,
                "owner", Set.of("aivideo:portrait:add"), 1L, null));
        when(imageValidator.validate(any(), any(), any()))
            .thenReturn(new PortraitImageMetadata("webp", 2, 3, content.length));
        assertThatThrownBy(() -> service.uploadPortraitImage(
            new UploadPortraitImageDTO("portrait.webp", "image/webp", content), portraitPrincipal))
            .isInstanceOf(ServiceException.class)
            .hasMessage("VideoOps 对象存储未启用")
            .extracting("code").isEqualTo(46213);
        verifyNoInteractions(ossClient);
        verify(assetMapper, never()).insert(any(AssetFile.class));
    }

    @Test
    void tombstoneVoiceAssetDeletesDatabaseFirstAndObjectOnlyAfterCommit() {
        useOssClient();
        AssetFile asset = ownedVoiceAsset(91L, "voices/7/sample.mp3");
        when(assetMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(asset);
        when(assetMapper.delete(any(LambdaQueryWrapper.class))).thenReturn(1);
        when(ossClient.delete("voices/7/sample.mp3")).thenReturn(true);
        beginTransactionSynchronization();
        try {
            service.tombstoneOwnedVoiceAssetAndPurgeAfterCommit("42", "91", principal);

            var queryCaptor = org.mockito.ArgumentCaptor.<LambdaQueryWrapper<AssetFile>>captor();
            var deleteCaptor = org.mockito.ArgumentCaptor.<LambdaQueryWrapper<AssetFile>>captor();
            verify(assetMapper).selectOne(queryCaptor.capture());
            verify(assetMapper).delete(deleteCaptor.capture());
            assertOwnedVoiceAssetScope(queryCaptor.getValue(), 91L);
            assertOwnedVoiceAssetScope(deleteCaptor.getValue(), 91L);
            verifyNoInteractions(ossClient);
            TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);
            verify(ossClient).delete("voices/7/sample.mp3");
        } finally {
            endTransactionSynchronization();
        }
    }

    @Test
    void tombstoneVoiceAssetRequiresActualTransactionBeforeDatabaseAccess() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            assertThatThrownBy(() -> service.tombstoneOwnedVoiceAssetAndPurgeAfterCommit("42", "91", principal))
                .isInstanceOf(IllegalStateException.class);
            verifyNoInteractions(assetMapper, ossClient);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void tombstoneVoiceAssetRequiresActiveSynchronizationBeforeDatabaseAccess() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertThatThrownBy(() -> service.tombstoneOwnedVoiceAssetAndPurgeAfterCommit("42", "91", principal))
                .isInstanceOf(IllegalStateException.class);
            verifyNoInteractions(assetMapper, ossClient);
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }

    @Test
    void tombstoneVoiceAssetMasksWrongCategoryAsInvisible() {
        useOssClient();
        when(assetMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        beginTransactionSynchronization();
        try {
            assertThatThrownBy(() -> service.tombstoneOwnedVoiceAssetAndPurgeAfterCommit("42", "91", principal))
                .isInstanceOf(ServiceException.class)
                .hasMessage("声音资产不可删除")
                .extracting("code").isEqualTo(46302);
            verify(assetMapper, never()).delete(any(LambdaQueryWrapper.class));
            assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
        } finally {
            endTransactionSynchronization();
        }
    }

    @Test
    void tombstoneVoiceAssetDoesNotRegisterCallbackWhenConditionalDeleteLosesRace() {
        useOssClient();
        when(assetMapper.selectOne(any(LambdaQueryWrapper.class)))
            .thenReturn(ownedVoiceAsset(91L, "voices/7/sample.mp3"));
        when(assetMapper.delete(any(LambdaQueryWrapper.class))).thenReturn(0);
        beginTransactionSynchronization();
        try {
            assertThatThrownBy(() -> service.tombstoneOwnedVoiceAssetAndPurgeAfterCommit("42", "91", principal))
                .isInstanceOf(ServiceException.class)
                .extracting("code").isEqualTo(46302);
            assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
            verifyNoInteractions(ossClient);
        } finally {
            endTransactionSynchronization();
        }
    }

    @Test
    void tombstoneVoiceAssetSwallowsObjectPurgeFailureAfterCommit() {
        useOssClient();
        when(assetMapper.selectOne(any(LambdaQueryWrapper.class)))
            .thenReturn(ownedVoiceAsset(91L, "voices/7/sample.mp3"));
        when(assetMapper.delete(any(LambdaQueryWrapper.class))).thenReturn(1);
        when(ossClient.delete(anyString())).thenThrow(new IllegalStateException("secret path"));
        Logger logger = (Logger) LoggerFactory.getLogger(AssetServiceImpl.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        beginTransactionSynchronization();
        try {
            service.tombstoneOwnedVoiceAssetAndPurgeAfterCommit("42", "91", principal);

            assertThatCode(() -> TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit)).doesNotThrowAnyException();
            assertThat(appender.list).extracting(ILoggingEvent::getFormattedMessage)
                .anySatisfy(message -> assertThat(message)
                    .contains("voiceId=42", "assetId=91")
                    .doesNotContain("secret path", "voices/7/sample.mp3"));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
            endTransactionSynchronization();
        }
    }

    @Test
    void createsShortLivedUrlOnlyForOwnedReadyWorkflowOutput() {
        useOssClient();
        AssetFile output = new AssetFile();
        output.setAssetId(92L);
        output.setTenantId(1L);
        output.setWorkspaceId("personal:7");
        output.setOwnerId(7L);
        output.setCategory("workflow_output");
        output.setObjectKey("workflow-results/701/output.png");
        output.setContentType("image/png");
        output.setStatus("ready");
        output.setDelFlag("0");
        when(assetMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(output);
        when(ossClient.presignGetUrl(eq(output.getObjectKey()), any(Duration.class)))
            .thenReturn("https://private.example.test/output.png");

        var access = service.createWorkflowAccessUrl("92", principal);

        assertThat(access.url()).isEqualTo("https://private.example.test/output.png");
        assertThat(access.contentType()).isEqualTo("image/png");
        verify(ossClient).presignGetUrl(eq(output.getObjectKey()), any(Duration.class));
    }

    private void assertOwnedVoiceAssetScope(LambdaQueryWrapper<AssetFile> wrapper, long assetId) {
        String sql = wrapper.getSqlSegment().toLowerCase();
        assertThat(sql).contains("asset_id", "tenant_id", "workspace_id", "owner_id", "category", "del_flag");
        assertThat(wrapper.getParamNameValuePairs().values())
            .contains(assetId, 1L, "personal:7", 7L, "voice_sample", "0");
    }

    private void useOssClient() {
        when(ossClientProvider.getIfAvailable()).thenReturn(ossClient);
    }

    private AssetFile ownedVoiceAsset(long assetId, String objectKey) {
        AssetFile asset = new AssetFile();
        asset.setAssetId(assetId);
        asset.setTenantId(1L);
        asset.setWorkspaceId("personal:7");
        asset.setOwnerId(7L);
        asset.setCategory("voice_sample");
        asset.setObjectKey(objectKey);
        asset.setDelFlag("0");
        return asset;
    }

    private void beginTransactionSynchronization() {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
    }

    private void endTransactionSynchronization() {
        TransactionSynchronizationManager.setActualTransactionActive(false);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private byte[] mp3Frames() {
        int frameLength = 417;
        byte[] bytes = new byte[frameLength * 2];
        writeMp3FrameHeader(bytes, 0);
        writeMp3FrameHeader(bytes, frameLength);
        return bytes;
    }

    private void writeMp3FrameHeader(byte[] bytes, int offset) {
        bytes[offset] = (byte) 0xff;
        bytes[offset + 1] = (byte) 0xfb;
        bytes[offset + 2] = (byte) 0x90;
        bytes[offset + 3] = 0x64;
    }
}
