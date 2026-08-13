package org.dromara.aivideo.portrait.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.dromara.aivideo.asset.service.IAssetService;
import org.dromara.aivideo.asset.dto.AssetAccessUrlDTO;
import org.dromara.aivideo.asset.dto.AssetDTO;
import org.dromara.aivideo.identity.dto.AppPrincipalSnapshotDTO;
import org.dromara.aivideo.identity.dto.AppWorkspaceSessionSnapshotDTO;
import org.dromara.aivideo.portrait.dto.PortraitPageRowDTO;
import org.dromara.aivideo.portrait.dto.PortraitQueryDTO;
import org.dromara.aivideo.portrait.dto.CreatePortraitDTO;
import org.dromara.aivideo.portrait.domain.Portrait;
import org.dromara.aivideo.portrait.mapper.PortraitMapper;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("dev")
class PortraitServiceImplTest {

    @Test
    void filtersStatusBeforePaginationAndKeepsDatabaseTotal() {
        PortraitMapper mapper = mock(PortraitMapper.class);
        IAssetService assetService = mock(IAssetService.class);
        PortraitPageRowDTO failed = row("failed");
        Page<PortraitPageRowDTO> page = new Page<>(2, 12);
        page.setRecords(List.of(failed));
        page.setTotal(37);
        when(mapper.selectOwnedPage(any(), eq(2001L), eq("personal-1001"), eq(1001L),
            eq(null), eq("failed"), eq(null))).thenReturn(page);
        PortraitServiceImpl service = new PortraitServiceImpl(mapper, assetService, mock(TransactionTemplate.class));

        var result = service.queryPage(new PortraitQueryDTO(null, "failed", null),
            principal(), new PageQuery(12, 2));

        assertThat(result.getTotal()).isEqualTo(37);
        assertThat(result.getRows()).singleElement()
            .satisfies(item -> assertThat(item.availabilityStatus()).isEqualTo("failed"));
        verify(assetService, never()).createPortraitAccessUrl(any(), any());
    }

    @Test
    void returnsExistingPortraitForSameIdempotentCreate() throws Exception {
        PortraitMapper mapper = mock(PortraitMapper.class);
        IAssetService assetService = mock(IAssetService.class);
        Portrait existing = portrait();
        existing.setIdempotencyKey("request-1");
        existing.setRequestDigest(digest("4001\n主播\nfemale\n[]\n"));
        when(mapper.selectOne(any())).thenReturn(existing);
        when(assetService.requireOwnedPortraitAsset("4001", principal())).thenReturn(asset());
        when(assetService.createPortraitAccessUrl("4001", principal())).thenReturn(
            new AssetAccessUrlDTO("/portrait.webp", LocalDateTime.now().plusMinutes(2), "image/webp"));
        PortraitServiceImpl service = new PortraitServiceImpl(mapper, assetService, mock(TransactionTemplate.class));

        var result = service.create(new CreatePortraitDTO("4001", "主播", "female", List.of(), null, "request-1"),
            principal());

        assertThat(result.portraitId()).isEqualTo("3001");
        verify(mapper, never()).insert(any(Portrait.class));
    }

    @Test
    void rejectsReusedIdempotencyKeyWithDifferentRequest() throws Exception {
        PortraitMapper mapper = mock(PortraitMapper.class);
        IAssetService assetService = mock(IAssetService.class);
        Portrait existing = portrait();
        existing.setIdempotencyKey("request-1");
        existing.setRequestDigest(digest("4001\n主播\nfemale\n[]\n"));
        when(mapper.selectOne(any())).thenReturn(existing);
        PortraitServiceImpl service = new PortraitServiceImpl(mapper, assetService, mock(TransactionTemplate.class));

        assertThatThrownBy(() -> service.create(
            new CreatePortraitDTO("4001", "另一名称", "female", List.of(), null, "request-1"), principal()))
            .isInstanceOf(ServiceException.class)
            .hasFieldOrPropertyWithValue("code", 46304);
    }

    @Test
    void deletesObjectBetweenTwoDatabaseTransactions() {
        PortraitMapper mapper = mock(PortraitMapper.class);
        IAssetService assetService = mock(IAssetService.class);
        TransactionTemplate transactionTemplate = immediateTransactions();
        when(mapper.selectOne(any())).thenReturn(portrait());
        when(assetService.requireOwnedPortraitAsset("4001", principal())).thenReturn(asset());
        when(mapper.delete(any())).thenReturn(1);
        PortraitServiceImpl service = new PortraitServiceImpl(mapper, assetService, transactionTemplate);

        service.delete("3001", "1", principal());

        var order = inOrder(assetService);
        order.verify(assetService).markDeletePending("4001", principal());
        order.verify(assetService).deleteObject("4001", principal());
        order.verify(assetService).deleteAssetRecord("4001", principal());
    }

    @Test
    void persistsDeleteFailureWithoutDeletingDatabaseRecords() {
        PortraitMapper mapper = mock(PortraitMapper.class);
        IAssetService assetService = mock(IAssetService.class);
        when(mapper.selectOne(any())).thenReturn(portrait());
        when(assetService.requireOwnedPortraitAsset("4001", principal())).thenReturn(asset());
        doThrow(new ServiceException("OSS failed", 46211)).when(assetService).deleteObject("4001", principal());
        PortraitServiceImpl service = new PortraitServiceImpl(mapper, assetService, immediateTransactions());

        assertThatThrownBy(() -> service.delete("3001", "1", principal()))
            .isInstanceOf(ServiceException.class)
            .hasFieldOrPropertyWithValue("code", 46211);

        verify(assetService).markDeleteFailed("4001", "OSS failed", principal());
        verify(mapper, never()).delete(any());
        verify(assetService, never()).deleteAssetRecord(any(), any());
    }

    @SuppressWarnings("unchecked")
    private TransactionTemplate immediateTransactions() {
        TransactionTemplate template = mock(TransactionTemplate.class);
        TransactionStatus status = mock(TransactionStatus.class);
        when(template.execute(any(TransactionCallback.class))).thenAnswer(invocation ->
            ((TransactionCallback<Object>) invocation.getArgument(0)).doInTransaction(status));
        doAnswer(invocation -> {
            ((java.util.function.Consumer<TransactionStatus>) invocation.getArgument(0)).accept(status);
            return null;
        }).when(template).executeWithoutResult(any());
        return template;
    }

    private Portrait portrait() {
        Portrait portrait = new Portrait();
        portrait.setPortraitId(3001L);
        portrait.setTenantId(2001L);
        portrait.setWorkspaceId("personal-1001");
        portrait.setOwnerId(1001L);
        portrait.setAssetId(4001L);
        portrait.setName("主播");
        portrait.setGender("female");
        portrait.setSceneTagsJson("[]");
        portrait.setRecordRevision(1L);
        portrait.setDelFlag("0");
        return portrait;
    }

    private AssetDTO asset() {
        return new AssetDTO("4001", "ready", null, "portrait.webp", "image/webp", "webp",
            1080, 1440, 1024L, LocalDateTime.now());
    }

    private String digest(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
            .digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private PortraitPageRowDTO row(String status) {
        PortraitPageRowDTO row = new PortraitPageRowDTO();
        row.setPortraitId(3001L);
        row.setAssetId(4001L);
        row.setName("主播");
        row.setGender("female");
        row.setSceneTagsJson("[]");
        row.setAvailabilityStatus(status);
        row.setFailureReason("文件处理失败");
        row.setOriginalFileName("portrait.webp");
        row.setContentType("image/webp");
        row.setFileFormat("webp");
        row.setWidth(1080);
        row.setHeight(1440);
        row.setFileSize(1024L);
        row.setRecordRevision(1L);
        row.setCreateTime(LocalDateTime.now());
        row.setUpdateTime(LocalDateTime.now());
        return row;
    }

    private AppPrincipalSnapshotDTO principal() {
        AppWorkspaceSessionSnapshotDTO workspace = new AppWorkspaceSessionSnapshotDTO(
            "personal-1001", "personal", 2001L, "app_user", 1001L,
            "app_user", 1001L, "personal_creator", Set.of("aivideo:portrait:query", "aivideo:portrait:add",
                "aivideo:portrait:remove"), 1L, null);
        return new AppPrincipalSnapshotDTO(1001L, "creator", "desktop-web", 1L, 1L, 1L, 1L, workspace);
    }
}
