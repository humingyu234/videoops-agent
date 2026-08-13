package org.dromara.aivideo.script.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.aivideo.identity.dto.AppPrincipalSnapshotDTO;
import org.dromara.aivideo.identity.dto.AppWorkspaceSessionSnapshotDTO;
import org.dromara.aivideo.script.domain.AvScriptVersion;
import org.dromara.aivideo.script.domain.AvUserScript;
import org.dromara.aivideo.script.dto.ScriptVersionDTO;
import org.dromara.aivideo.script.dto.ScriptVersionSummaryDTO;
import org.dromara.aivideo.script.dto.UserScriptCreateDTO;
import org.dromara.aivideo.script.dto.UserScriptDetailDTO;
import org.dromara.aivideo.script.dto.UserScriptEditDTO;
import org.dromara.aivideo.script.dto.UserScriptListDTO;
import org.dromara.aivideo.script.dto.UserScriptQueryDTO;
import org.dromara.aivideo.script.dto.UserScriptSaveResultDTO;
import org.dromara.aivideo.script.mapper.AvScriptVersionMapper;
import org.dromara.aivideo.script.mapper.AvUserScriptMapper;
import org.dromara.aivideo.script.service.IUserScriptService;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** 个人文案创建、查询和不可变版本编排。 */
@Service
@RequiredArgsConstructor
public class UserScriptServiceImpl implements IUserScriptService {
    private static final int INVALID = 400;
    private static final int NOT_FOUND = 404;
    private static final int IDEMPOTENCY_CONFLICT = 46116;
    private static final int SCRIPT_HAS_REFERENCES = 46118;
    private static final int REVISION_CONFLICT = 46136;
    private static final String OWNER_PERSONAL = "personal";
    private static final int CHARS_PER_MINUTE = 240;

    private final AvUserScriptMapper userScriptMapper;
    private final AvScriptVersionMapper scriptVersionMapper;

    @Override
    public PageResult<UserScriptListDTO> queryPage(UserScriptQueryDTO query, AppPrincipalSnapshotDTO principal,
                                                    PageQuery pageQuery) {
        requirePermission(principal, "aivideo:script:query");
        AppWorkspaceSessionSnapshotDTO workspace = requireWorkspace(principal);
        UserScriptQueryDTO safe = query == null ? new UserScriptQueryDTO(null, null, null) : query;
        String keyword = optional(safe.keyword());
        String order = optional(safe.orderByColumn());
        order = order == null ? "updatedAt" : order;
        if (!Set.of("updatedAt", "displayTitle").contains(order)) {
            throw invalid("排序字段无效");
        }
        String direction = optional(safe.isAsc());
        direction = direction == null ? "desc" : direction.toLowerCase(Locale.ROOT);
        if (!Set.of("asc", "desc").contains(direction)) {
            throw invalid("排序方向无效");
        }
        int pageNum = pageQuery == null || pageQuery.getPageNum() == null || pageQuery.getPageNum() <= 0
            ? 1 : pageQuery.getPageNum();
        int pageSize = pageQuery == null || pageQuery.getPageSize() == null || pageQuery.getPageSize() <= 0
            ? 20 : Math.min(pageQuery.getPageSize(), 100);
        Page<UserScriptListDTO> page = userScriptMapper.selectOwnedPage(new Page<>(pageNum, pageSize),
            workspace.tenantId(), principal.appUserId(), keyword, order, direction);
        return PageResult.build(page.getRecords(), page.getTotal());
    }

    @Override
    public UserScriptDetailDTO queryById(String scriptId, AppPrincipalSnapshotDTO principal) {
        requirePermission(principal, "aivideo:script:query");
        AppWorkspaceSessionSnapshotDTO workspace = requireWorkspace(principal);
        AvUserScript script = requireOwned(parseId(scriptId, NOT_FOUND), principal, workspace);
        AvScriptVersion current = requireVersion(script.getId(), script.getCurrentVersionId(), principal, workspace);
        List<ScriptVersionSummaryDTO> versions = scriptVersionMapper.selectSummaries(script.getId(),
            workspace.tenantId(), principal.appUserId());
        return new UserScriptDetailDTO(id(script.getId()), script.getDisplayTitle(), id(script.getScriptRevision()),
            id(script.getCurrentVersionId()), script.getCreatedAt(), script.getUpdatedAt(), toDTO(current), versions);
    }

    @Override
    public ScriptVersionDTO queryVersion(String scriptId, String versionId, AppPrincipalSnapshotDTO principal) {
        requirePermission(principal, "aivideo:script:query");
        AppWorkspaceSessionSnapshotDTO workspace = requireWorkspace(principal);
        long parsedScriptId = parseId(scriptId, NOT_FOUND);
        requireOwned(parsedScriptId, principal, workspace);
        return toDTO(requireVersion(parsedScriptId, parseId(versionId, NOT_FOUND), principal, workspace));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserScriptSaveResultDTO create(UserScriptCreateDTO command, AppPrincipalSnapshotDTO principal) {
        requirePermission(principal, "aivideo:script:edit");
        if (command == null) throw invalid("文案参数不能为空");
        AppWorkspaceSessionSnapshotDTO workspace = requireWorkspace(principal);
        String title = normalizeRequired(command.displayTitle(), "标题", 100);
        String text = normalizeRequired(command.scriptText(), "文案正文", 20_000);
        String key = normalizeKey(command.idempotencyKey());
        String hash = sha256(title + "\0" + text);
        AvUserScript existing = userScriptMapper.selectOwnedByIntent(workspace.tenantId(), principal.appUserId(), key);
        if (existing != null) return reuseCreate(existing, hash, principal, workspace);

        LocalDateTime now = LocalDateTime.now();
        AvUserScript script = new AvUserScript();
        script.setTenantId(workspace.tenantId());
        script.setOwnerType(OWNER_PERSONAL);
        script.setOwnerId(principal.appUserId());
        script.setCreatedByUserId(principal.appUserId());
        script.setDisplayTitle(title);
        script.setCreateIdempotencyKey(key);
        script.setCreateRequestHash(hash);
        script.setScriptRevision(1L);
        script.setCreatedAt(now);
        script.setUpdatedAt(now);
        script.setDeleted("0");
        try {
            if (userScriptMapper.insert(script) != 1 || script.getId() == null) throw invalid("文案创建失败");
        } catch (DuplicateKeyException exception) {
            existing = userScriptMapper.selectOwnedByIntent(workspace.tenantId(), principal.appUserId(), key);
            if (existing != null) return reuseCreate(existing, hash, principal, workspace);
            throw new ServiceException("幂等键已用于不同的创建请求", IDEMPOTENCY_CONFLICT);
        }
        AvScriptVersion version = newVersion(script.getId(), null, 1, "manual_input", text, key, hash,
            title, 1L, workspace, principal, now);
        if (scriptVersionMapper.insert(version) != 1 || version.getId() == null
            || userScriptMapper.updateCurrentVersion(script.getId(), version.getId(), workspace.tenantId(),
            principal.appUserId(), principal.appUserId()) != 1) {
            throw invalid("文案版本创建失败");
        }
        script.setCurrentVersionId(version.getId());
        return saveResult(script, version, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserScriptSaveResultDTO createVersion(UserScriptEditDTO command, AppPrincipalSnapshotDTO principal) {
        requirePermission(principal, "aivideo:script:edit");
        if (command == null) throw revisionConflict();
        AppWorkspaceSessionSnapshotDTO workspace = requireWorkspace(principal);
        long scriptId = parseId(command.scriptId(), NOT_FOUND);
        long parentId = parseId(command.parentVersionId(), REVISION_CONFLICT);
        long revision = parseId(command.expectedScriptRevision(), REVISION_CONFLICT);
        String title = normalizeRequired(command.displayTitle(), "标题", 100);
        String text = normalizeRequired(command.scriptText(), "文案正文", 20_000);
        String key = normalizeKey(command.idempotencyKey());
        String requestHash = sha256(title + "\0" + text + "\0" + parentId + "\0" + revision);
        AvUserScript script = requireOwned(scriptId, principal, workspace);
        AvScriptVersion reused = scriptVersionMapper.selectByManualIntent(scriptId, workspace.tenantId(),
            principal.appUserId(), key);
        if (reused != null) {
            if (!Objects.equals(reused.getManualRequestHash(), requestHash)) {
                throw new ServiceException("幂等键已用于不同的编辑请求", IDEMPOTENCY_CONFLICT);
            }
            return saveResult(script, reused, true);
        }
        if (!Objects.equals(script.getCurrentVersionId(), parentId)
            || !Objects.equals(script.getScriptRevision(), revision)) throw revisionConflict();
        AvScriptVersion parent = requireVersion(scriptId, parentId, principal, workspace);
        AvScriptVersion version = newVersion(scriptId, parentId, parent.getVersionNo() + 1, "manual_edit", text,
            key, requestHash, title, revision + 1, workspace, principal, LocalDateTime.now());
        try {
            if (scriptVersionMapper.insert(version) != 1 || version.getId() == null) throw invalid("文案版本创建失败");
        } catch (DuplicateKeyException exception) {
            AvScriptVersion concurrent = scriptVersionMapper.selectByManualIntent(scriptId, workspace.tenantId(),
                principal.appUserId(), key);
            if (concurrent != null) {
                if (!Objects.equals(concurrent.getManualRequestHash(), requestHash)) {
                    throw new ServiceException("幂等键已用于不同的编辑请求", IDEMPOTENCY_CONFLICT);
                }
                return saveResult(script, concurrent, true);
            }
            throw revisionConflict();
        }
        int affected = userScriptMapper.updateForNewVersion(scriptId, workspace.tenantId(), principal.appUserId(),
            parentId, revision, title, version.getId(), principal.appUserId());
        if (affected != 1) throw revisionConflict();
        script.setDisplayTitle(title);
        script.setCurrentVersionId(version.getId());
        script.setScriptRevision(revision + 1);
        script.setUpdatedAt(LocalDateTime.now());
        return saveResult(script, version, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String scriptId, AppPrincipalSnapshotDTO principal) {
        requirePermission(principal, "aivideo:script:remove");
        AppWorkspaceSessionSnapshotDTO workspace = requireWorkspace(principal);
        AvUserScript script = requireOwned(parseId(scriptId, NOT_FOUND), principal, workspace);
        if (script.getCurrentConfirmedVersionId() != null) {
            throw new ServiceException("文案已被引用，无法删除", SCRIPT_HAS_REFERENCES);
        }
        if (userScriptMapper.softDeleteOwned(script.getId(), workspace.tenantId(), principal.appUserId(),
            principal.appUserId()) != 1) throw new ServiceException("文案不存在", NOT_FOUND);
    }

    private UserScriptSaveResultDTO reuseCreate(AvUserScript script, String hash,
                                                  AppPrincipalSnapshotDTO principal,
                                                  AppWorkspaceSessionSnapshotDTO workspace) {
        if (!Objects.equals(script.getCreateRequestHash(), hash)) {
            throw new ServiceException("幂等键已用于不同的创建请求", IDEMPOTENCY_CONFLICT);
        }
        AvScriptVersion version = requireVersion(script.getId(), script.getCurrentVersionId(), principal, workspace);
        return saveResult(script, version, true);
    }

    private AvScriptVersion newVersion(Long scriptId, Long parentId, int versionNo, String sourceType,
                                       String text, String key, String requestHash, String resultTitle,
                                       long resultRevision, AppWorkspaceSessionSnapshotDTO workspace,
                                       AppPrincipalSnapshotDTO principal, LocalDateTime now) {
        int count = countEffectiveCharacters(text);
        AvScriptVersion version = new AvScriptVersion();
        version.setTenantId(workspace.tenantId());
        version.setOwnerType(OWNER_PERSONAL);
        version.setOwnerId(principal.appUserId());
        version.setCreatedByUserId(principal.appUserId());
        version.setScriptId(scriptId);
        version.setParentVersionId(parentId);
        version.setVersionNo(versionNo);
        version.setSourceType(sourceType);
        version.setScriptText(text);
        version.setEffectiveCharacterCount(count);
        version.setEstimatedDurationSeconds((count + 3) / 4);
        version.setEffectiveCharsPerMinute(CHARS_PER_MINUTE);
        version.setRuleConfigVersionsJson("{\"duration\":\"manual-v1\",\"character\":\"p3-v1\"}");
        version.setManualIdempotencyKey(key);
        version.setManualRequestHash(requestHash);
        version.setResultDisplayTitle(resultTitle);
        version.setResultScriptRevision(resultRevision);
        version.setCreatedAt(now);
        return version;
    }

    private int countEffectiveCharacters(String value) {
        int count = 0;
        boolean inLatinNumber = false;
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            offset += Character.charCount(codePoint);
            Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
            if (Set.of(Character.UnicodeScript.HAN, Character.UnicodeScript.HIRAGANA,
                Character.UnicodeScript.KATAKANA, Character.UnicodeScript.HANGUL,
                Character.UnicodeScript.BOPOMOFO).contains(script)) {
                count++;
                inLatinNumber = false;
            } else if (Character.isDigit(codePoint)
                || script == Character.UnicodeScript.LATIN) {
                if (!inLatinNumber) count++;
                inLatinNumber = true;
            } else {
                inLatinNumber = false;
            }
        }
        return count;
    }

    private UserScriptSaveResultDTO saveResult(AvUserScript script, AvScriptVersion version, boolean reused) {
        return new UserScriptSaveResultDTO(id(script.getId()), id(version.getId()), id(version.getResultScriptRevision()),
            version.getVersionNo(), version.getResultDisplayTitle(), version.getEffectiveCharacterCount(),
            version.getEstimatedDurationSeconds(), version.getCreatedAt(), reused);
    }

    private ScriptVersionDTO toDTO(AvScriptVersion version) {
        return new ScriptVersionDTO(id(version.getScriptId()), id(version.getId()), id(version.getParentVersionId()),
            version.getVersionNo(), version.getSourceType(), version.getScriptText(),
            version.getEffectiveCharacterCount(), version.getEstimatedDurationSeconds(), version.getCreatedAt());
    }

    private AvUserScript requireOwned(long scriptId, AppPrincipalSnapshotDTO principal,
                                      AppWorkspaceSessionSnapshotDTO workspace) {
        AvUserScript script = userScriptMapper.selectOwned(scriptId, workspace.tenantId(), principal.appUserId());
        if (script == null) throw new ServiceException("文案不存在", NOT_FOUND);
        return script;
    }

    private AvScriptVersion requireVersion(long scriptId, Long versionId, AppPrincipalSnapshotDTO principal,
                                           AppWorkspaceSessionSnapshotDTO workspace) {
        if (versionId == null) throw new ServiceException("文案版本不存在", NOT_FOUND);
        AvScriptVersion version = scriptVersionMapper.selectOwned(scriptId, versionId, workspace.tenantId(),
            principal.appUserId());
        if (version == null) throw new ServiceException("文案版本不存在", NOT_FOUND);
        return version;
    }

    private AppWorkspaceSessionSnapshotDTO requireWorkspace(AppPrincipalSnapshotDTO principal) {
        if (principal == null || principal.appUserId() == null || principal.appUserId() <= 0
            || principal.workspace() == null || principal.workspace().tenantId() == null) {
            throw new ServiceException("当前创作身份不可用", 403);
        }
        return principal.workspace();
    }

    private void requirePermission(AppPrincipalSnapshotDTO principal, String permission) {
        if (!requireWorkspace(principal).permissions().contains(permission)) {
            throw new ServiceException("无文案操作权限", 403);
        }
    }

    private String normalizeRequired(String value, String label, int maxCodePoints) {
        if (value == null) throw invalid(label + "不能为空");
        String normalized = value.replace("\r\n", "\n").replace('\r', '\n').strip();
        int length = normalized.codePointCount(0, normalized.length());
        if (length == 0) throw invalid(label + "不能为空");
        if (length > maxCodePoints) throw invalid(label + "长度不能超过 " + maxCodePoints);
        return normalized;
    }

    private String normalizeKey(String key) {
        String normalized = normalizeRequired(key, "幂等键", 64);
        if (!normalized.matches("[A-Za-z0-9._:-]+")) throw invalid("幂等键格式无效");
        return normalized;
    }

    private String optional(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private long parseId(String value, int code) {
        try {
            long parsed = Long.parseLong(value);
            if (parsed <= 0) throw new NumberFormatException();
            return parsed;
        } catch (RuntimeException exception) {
            throw new ServiceException("编号或修订号无效", code);
        }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM 不支持 SHA-256", exception);
        }
    }

    private String id(Long value) {
        return value == null ? null : Long.toString(value);
    }

    private ServiceException invalid(String message) {
        return new ServiceException(message, INVALID);
    }

    private ServiceException revisionConflict() {
        return new ServiceException("文案已被修改，请刷新后重试", REVISION_CONFLICT);
    }
}
