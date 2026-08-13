package org.dromara.aivideo.portrait.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.hutool.json.JSONUtil;
import lombok.RequiredArgsConstructor;
import org.dromara.aivideo.asset.dto.AssetAccessUrlDTO;
import org.dromara.aivideo.asset.dto.AssetDTO;
import org.dromara.aivideo.asset.service.IAssetService;
import org.dromara.aivideo.identity.dto.AppPrincipalSnapshotDTO;
import org.dromara.aivideo.identity.dto.AppWorkspaceSessionSnapshotDTO;
import org.dromara.aivideo.portrait.domain.Portrait;
import org.dromara.aivideo.portrait.dto.CreatePortraitDTO;
import org.dromara.aivideo.portrait.dto.PortraitAccessUrlDTO;
import org.dromara.aivideo.portrait.dto.PortraitDTO;
import org.dromara.aivideo.portrait.dto.PortraitPageRowDTO;
import org.dromara.aivideo.portrait.dto.PortraitQueryDTO;
import org.dromara.aivideo.portrait.dto.UpdatePortraitDTO;
import org.dromara.aivideo.portrait.mapper.PortraitMapper;
import org.dromara.aivideo.portrait.service.IPortraitService;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** 人物形象 CRUD 与归属编排。 */
@Service
@RequiredArgsConstructor
public class PortraitServiceImpl implements IPortraitService {
    private static final int NOT_FOUND = 46301;
    private static final int INVALID = 46302;
    private static final int REVISION_CONFLICT = 46303;
    private static final int IDEMPOTENCY_CONFLICT = 46304;
    private static final int DELETE_FAILED = 46211;
    private final PortraitMapper portraitMapper;
    private final IAssetService assetService;
    private final TransactionTemplate transactionTemplate;

    @Override
    public PageResult<PortraitDTO> queryPage(PortraitQueryDTO query, AppPrincipalSnapshotDTO principal,
                                              PageQuery pageQuery) {
        requirePermission(principal, "aivideo:portrait:query");
        AppWorkspaceSessionSnapshotDTO workspace = requireWorkspace(principal);
        PortraitQueryDTO safeQuery = query == null ? new PortraitQueryDTO(null, null, null) : query;
        String keyword = cleanOptional(safeQuery.keyword());
        String status = cleanOptional(safeQuery.availabilityStatus());
        if (status != null && !Set.of("processing", "ready", "failed").contains(status)) {
            throw new ServiceException("形象状态筛选值无效", INVALID);
        }
        String gender = cleanOptional(safeQuery.gender());
        int pageNum = pageQuery == null || pageQuery.getPageNum() == null || pageQuery.getPageNum() <= 0
            ? 1 : pageQuery.getPageNum();
        int pageSize = pageQuery == null || pageQuery.getPageSize() == null || pageQuery.getPageSize() <= 0
            ? 12 : Math.min(pageQuery.getPageSize(), 48);
        Page<PortraitPageRowDTO> page = portraitMapper.selectOwnedPage(new Page<>(pageNum, pageSize),
            workspace.tenantId(), workspace.workspaceKey(), principal.appUserId(), keyword, status, gender);
        List<PortraitDTO> records = page.getRecords().stream().map(item -> toDTO(item, principal)).toList();
        return PageResult.build(records, page.getTotal());
    }

    @Override
    public PortraitDTO queryById(String portraitId, AppPrincipalSnapshotDTO principal) {
        requirePermission(principal, "aivideo:portrait:query");
        Portrait portrait = requireOwned(portraitId, principal);
        AssetDTO asset = assetService.requireOwnedPortraitAsset(Long.toString(portrait.getAssetId()), principal);
        return toDTO(portrait, asset, principal, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PortraitDTO create(CreatePortraitDTO command, AppPrincipalSnapshotDTO principal) {
        requirePermission(principal, "aivideo:portrait:add");
        if (command == null) throw new ServiceException("人物形象参数不能为空", INVALID);
        AppWorkspaceSessionSnapshotDTO workspace = requireWorkspace(principal);
        String idempotencyKey = normalizeIdempotencyKey(command.idempotencyKey());
        Portrait normalized = new Portrait();
        applyMetadata(normalized, command.name(), command.gender(), command.sceneTags(), command.note());
        long assetId = parseId(command.assetId(), INVALID);
        String digest = requestDigest(assetId, normalized);

        Portrait existing = findByIdempotencyKey(workspace, principal, idempotencyKey);
        if (existing != null) {
            return resolveIdempotent(existing, digest, principal);
        }

        AssetDTO asset = assetService.requireOwnedReadyPortraitAsset(command.assetId(), principal);
        if (portraitMapper.exists(new LambdaQueryWrapper<Portrait>().eq(Portrait::getAssetId, assetId))) {
            throw new ServiceException("图片素材已绑定人物形象", INVALID);
        }
        Portrait portrait = new Portrait();
        portrait.setTenantId(workspace.tenantId());
        portrait.setWorkspaceId(workspace.workspaceKey());
        portrait.setOwnerId(principal.appUserId());
        portrait.setAssetId(assetId);
        portrait.setName(normalized.getName());
        portrait.setGender(normalized.getGender());
        portrait.setSceneTagsJson(normalized.getSceneTagsJson());
        portrait.setNote(normalized.getNote());
        portrait.setIdempotencyKey(idempotencyKey);
        portrait.setRequestDigest(digest);
        portrait.setRecordRevision(1L);
        portrait.setCreateBy(principal.appUserId());
        portrait.setUpdateBy(principal.appUserId());
        try {
            if (portraitMapper.insert(portrait) != 1 || portrait.getPortraitId() == null) {
                throw new ServiceException("人物形象创建失败", INVALID);
            }
        } catch (DuplicateKeyException exception) {
            existing = findByIdempotencyKey(workspace, principal, idempotencyKey);
            if (existing != null) return resolveIdempotent(existing, digest, principal);
            throw new ServiceException("图片素材已绑定人物形象", INVALID);
        }
        return toDTO(portrait, asset, principal, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PortraitDTO update(UpdatePortraitDTO command, AppPrincipalSnapshotDTO principal) {
        requirePermission(principal, "aivideo:portrait:edit");
        if (command == null) throw revisionConflict();
        Portrait current = requireOwned(command.portraitId(), principal);
        assetService.requireOwnedReadyPortraitAsset(Long.toString(current.getAssetId()), principal);
        Portrait changed = new Portrait();
        applyMetadata(changed, command.name(), command.gender(), command.sceneTags(), command.note());
        long expected = parseRevision(command.expectedRevision());
        int affected = portraitMapper.update(null, new LambdaUpdateWrapper<Portrait>()
            .eq(Portrait::getPortraitId, current.getPortraitId())
            .eq(Portrait::getOwnerId, principal.appUserId())
            .eq(Portrait::getRecordRevision, expected)
            .eq(Portrait::getDelFlag, "0")
            .set(Portrait::getName, changed.getName())
            .set(Portrait::getGender, changed.getGender())
            .set(Portrait::getSceneTagsJson, changed.getSceneTagsJson())
            .set(Portrait::getNote, changed.getNote())
            .set(Portrait::getUpdateBy, principal.appUserId())
            .setSql("record_revision = record_revision + 1"));
        if (affected != 1) throw revisionConflict();
        return queryById(command.portraitId(), principal);
    }

    @Override
    public void delete(String portraitId, String expectedRevision, AppPrincipalSnapshotDTO principal) {
        requirePermission(principal, "aivideo:portrait:remove");
        DeleteContext context = transactionTemplate.execute(status -> prepareDelete(
            portraitId, expectedRevision, principal));
        if (context == null) return;
        try {
            assetService.deleteObject(context.assetId(), principal);
        } catch (RuntimeException exception) {
            transactionTemplate.executeWithoutResult(status ->
                assetService.markDeleteFailed(context.assetId(), exception.getMessage(), principal));
            if (exception instanceof ServiceException serviceException) throw serviceException;
            throw new ServiceException("人物照片文件删除失败", DELETE_FAILED);
        }
        transactionTemplate.executeWithoutResult(status -> {
            int affected = portraitMapper.delete(new LambdaQueryWrapper<Portrait>()
                .eq(Portrait::getPortraitId, context.portraitId())
                .eq(Portrait::getOwnerId, principal.appUserId())
                .eq(Portrait::getDelFlag, "0"));
            if (affected != 1) throw new ServiceException("人物形象删除收尾失败，请重试", DELETE_FAILED);
            assetService.deleteAssetRecord(context.assetId(), principal);
        });
    }

    @Override
    public PortraitAccessUrlDTO createAccessUrl(String portraitId, AppPrincipalSnapshotDTO principal) {
        requirePermission(principal, "aivideo:portrait:query");
        Portrait current = requireOwned(portraitId, principal);
        AssetAccessUrlDTO access = assetService.createPortraitAccessUrl(Long.toString(current.getAssetId()), principal);
        return new PortraitAccessUrlDTO(access.url(), access.expiresAt(), access.contentType());
    }

    private DeleteContext prepareDelete(String portraitId, String expectedRevision,
                                        AppPrincipalSnapshotDTO principal) {
        AppWorkspaceSessionSnapshotDTO workspace = requireWorkspace(principal);
        long parsedId = parseId(portraitId, NOT_FOUND);
        Portrait current = portraitMapper.selectOne(ownedWrapper(principal, workspace)
            .eq(Portrait::getPortraitId, parsedId));
        if (current == null) {
            Portrait historical = portraitMapper.selectOwnedIncludingDeleted(parsedId, workspace.tenantId(),
                workspace.workspaceKey(), principal.appUserId());
            if (historical != null && "1".equals(historical.getDelFlag())) return null;
            throw new ServiceException("人物形象不存在", NOT_FOUND);
        }
        if (current.getRecordRevision() == null || current.getRecordRevision() != parseRevision(expectedRevision)) {
            throw revisionConflict();
        }
        String assetId = Long.toString(current.getAssetId());
        assetService.requireOwnedPortraitAsset(assetId, principal);
        assetService.markDeletePending(assetId, principal);
        return new DeleteContext(current.getPortraitId(), assetId);
    }

    private PortraitDTO resolveIdempotent(Portrait existing, String digest, AppPrincipalSnapshotDTO principal) {
        if (!digest.equals(existing.getRequestDigest())) {
            throw new ServiceException("幂等键已用于不同的创建请求", IDEMPOTENCY_CONFLICT);
        }
        AssetDTO asset = assetService.requireOwnedPortraitAsset(Long.toString(existing.getAssetId()), principal);
        return toDTO(existing, asset, principal, true);
    }

    private Portrait findByIdempotencyKey(AppWorkspaceSessionSnapshotDTO workspace,
                                          AppPrincipalSnapshotDTO principal, String idempotencyKey) {
        return portraitMapper.selectOne(ownedWrapper(principal, workspace)
            .eq(Portrait::getIdempotencyKey, idempotencyKey));
    }

    private Portrait requireOwned(String portraitId, AppPrincipalSnapshotDTO principal) {
        AppWorkspaceSessionSnapshotDTO workspace = requireWorkspace(principal);
        Portrait result = portraitMapper.selectOne(ownedWrapper(principal, workspace)
            .eq(Portrait::getPortraitId, parseId(portraitId, NOT_FOUND)));
        if (result == null) throw new ServiceException("人物形象不存在", NOT_FOUND);
        return result;
    }

    private LambdaQueryWrapper<Portrait> ownedWrapper(AppPrincipalSnapshotDTO principal,
                                                       AppWorkspaceSessionSnapshotDTO workspace) {
        return new LambdaQueryWrapper<Portrait>()
            .eq(Portrait::getTenantId, workspace.tenantId())
            .eq(Portrait::getWorkspaceId, workspace.workspaceKey())
            .eq(Portrait::getOwnerId, principal.appUserId())
            .eq(Portrait::getDelFlag, "0");
    }

    private PortraitDTO toDTO(PortraitPageRowDTO row, AppPrincipalSnapshotDTO principal) {
        String preview = null;
        java.time.LocalDateTime expiresAt = null;
        if ("ready".equals(row.getAvailabilityStatus())) {
            AssetAccessUrlDTO access = assetService.createPortraitAccessUrl(Long.toString(row.getAssetId()), principal);
            preview = access.url();
            expiresAt = access.expiresAt();
        }
        return new PortraitDTO(Long.toString(row.getPortraitId()), Long.toString(row.getAssetId()), row.getName(),
            row.getGender(), parseTags(row.getSceneTagsJson()), row.getNote(),
            row.getAvailabilityStatus(), row.getFailureReason(), preview, expiresAt, row.getOriginalFileName(),
            row.getContentType(), row.getFileFormat(), row.getWidth(), row.getHeight(), row.getFileSize(),
            Long.toString(row.getRecordRevision()), row.getCreateTime(), row.getUpdateTime());
    }

    private PortraitDTO toDTO(Portrait portrait, AssetDTO asset, AppPrincipalSnapshotDTO principal,
                              boolean includePreview) {
        String status = mapStatus(asset.availabilityStatus());
        String preview = null;
        java.time.LocalDateTime expiresAt = null;
        if (includePreview && "ready".equals(status)) {
            AssetAccessUrlDTO access = assetService.createPortraitAccessUrl(asset.assetId(), principal);
            preview = access.url();
            expiresAt = access.expiresAt();
        }
        return new PortraitDTO(Long.toString(portrait.getPortraitId()), asset.assetId(), portrait.getName(),
            portrait.getGender(), parseTags(portrait.getSceneTagsJson()), portrait.getNote(),
            status, asset.failureReason(), preview, expiresAt, asset.originalName(), asset.contentType(),
            asset.fileFormat(), asset.width(), asset.height(), asset.fileSize(),
            Long.toString(portrait.getRecordRevision()), portrait.getCreateTime(), portrait.getUpdateTime());
    }

    private String mapStatus(String status) {
        if ("ready".equals(status)) return "ready";
        if (Set.of("verifying", "delete_pending").contains(status)) return "processing";
        return "failed";
    }

    private void applyMetadata(Portrait portrait, String name, String gender, List<String> sceneTags, String note) {
        String cleanName = requiredText(name, "形象名称", 80);
        String cleanGender = gender == null ? "unspecified" : gender.trim().toLowerCase(Locale.ROOT);
        if (!Set.of("female", "male", "unspecified").contains(cleanGender)) {
            throw new ServiceException("性别取值无效", INVALID);
        }
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        if (sceneTags != null) {
            for (String tag : sceneTags) {
                if (tag == null || tag.isBlank()) continue;
                tags.add(requiredText(tag, "场景标签", 20));
                if (tags.size() > 8) throw new ServiceException("场景标签最多 8 个", INVALID);
            }
        }
        portrait.setName(cleanName);
        portrait.setGender(cleanGender);
        portrait.setSceneTagsJson(JSONUtil.toJsonStr(new ArrayList<>(tags)));
        portrait.setNote(note == null || note.isBlank() ? null : requiredText(note, "备注", 500));
    }

    private String requestDigest(long assetId, Portrait portrait) {
        String canonical = assetId + "\n" + portrait.getName() + "\n" + portrait.getGender() + "\n"
            + portrait.getSceneTagsJson() + "\n" + (portrait.getNote() == null ? "" : portrait.getNote());
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM 不支持 SHA-256", exception);
        }
    }

    private List<String> parseTags(String json) {
        return json == null || json.isBlank() ? List.of() : JSONUtil.toList(json, String.class);
    }

    private String normalizeIdempotencyKey(String key) {
        String result = requiredText(key, "幂等键", 64);
        if (!result.matches("[A-Za-z0-9._:-]+")) {
            throw new ServiceException("幂等键格式无效", INVALID);
        }
        return result;
    }

    private String cleanOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    private String requiredText(String value, String label, int max) {
        if (value == null || value.isBlank()) throw new ServiceException(label + "不能为空", INVALID);
        String result = value.trim();
        if (result.length() > max) throw new ServiceException(label + "长度不能超过 " + max, INVALID);
        return result;
    }

    private AppWorkspaceSessionSnapshotDTO requireWorkspace(AppPrincipalSnapshotDTO principal) {
        if (principal == null || principal.appUserId() == null || principal.appUserId() <= 0
            || principal.workspace() == null || principal.workspace().tenantId() == null
            || principal.workspace().workspaceKey() == null || principal.workspace().workspaceKey().isBlank()) {
            throw new ServiceException("当前创作工作区不可用", 403);
        }
        return principal.workspace();
    }

    private void requirePermission(AppPrincipalSnapshotDTO principal, String permission) {
        if (!requireWorkspace(principal).permissions().contains(permission)) {
            throw new ServiceException("无人物形象操作权限", 403);
        }
    }

    private long parseId(String id, int code) {
        try {
            long parsed = Long.parseLong(id);
            if (parsed <= 0) throw new NumberFormatException();
            return parsed;
        } catch (RuntimeException exception) {
            throw new ServiceException("资源编号无效", code);
        }
    }

    private long parseRevision(String revision) {
        return parseId(revision, REVISION_CONFLICT);
    }

    private ServiceException revisionConflict() {
        return new ServiceException("人物形象已被修改，请刷新后重试", REVISION_CONFLICT);
    }

    private record DeleteContext(Long portraitId, String assetId) {
    }
}
