package org.dromara.aivideo.platform.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.dromara.aivideo.knowledge.KnowledgeDomainCode;
import org.dromara.aivideo.knowledge.KnowledgeTypeCode;
import org.dromara.aivideo.knowledge.KnowledgeVersionStatus;
import org.dromara.aivideo.knowledge.domain.KnowledgeBinding;
import org.dromara.aivideo.knowledge.domain.KnowledgeItem;
import org.dromara.aivideo.knowledge.domain.KnowledgeVersion;
import org.dromara.aivideo.knowledge.mapper.KnowledgeBindingMapper;
import org.dromara.aivideo.knowledge.mapper.KnowledgeItemMapper;
import org.dromara.aivideo.knowledge.mapper.KnowledgeVersionMapper;
import org.dromara.aivideo.platform.knowledge.domain.bo.KnowledgeItemAdminQueryBo;
import org.dromara.aivideo.platform.knowledge.domain.bo.KnowledgeItemSaveBo;
import org.dromara.aivideo.platform.knowledge.domain.vo.KnowledgeImportSummaryVo;
import org.dromara.aivideo.platform.knowledge.domain.vo.KnowledgeImportSummaryVo.KnowledgeImportFileVo;
import org.dromara.aivideo.platform.knowledge.domain.vo.KnowledgeItemAdminVo;
import org.dromara.aivideo.platform.knowledge.domain.vo.KnowledgeItemDetailVo;
import org.dromara.aivideo.platform.knowledge.service.IKnowledgeAdminService;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** 运营端知识库管理服务实现。 */
@Service
@RequiredArgsConstructor
public class KnowledgeAdminServiceImpl implements IKnowledgeAdminService {

    private static final String EMPTY_ARRAY_JSON = "[]";
    private static final String EMPTY_OBJECT_JSON = "{}";
    private static final String IMPORT_SOURCE_TYPE = "knowledge_upload";
    private static final String MANUAL_SOURCE_TYPE = "manual";
    private static final int MAX_IMPORT_FILES = 20;
    private static final int MAX_FILE_BYTES = 10 * 1024 * 1024;
    private static final long MAX_TOTAL_BYTES = 20L * 1024 * 1024;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
        ".md", ".markdown", ".txt", ".text", ".json", ".csv", ".yaml", ".yml"
    );
    private static final Set<String> SUPPORTED_MIME_TYPES = Set.of(
        "text/plain", "text/markdown", "text/csv", "text/yaml", "text/x-yaml",
        "application/json", "application/yaml", "application/x-yaml", "application/octet-stream", ""
    );
    private static final Set<String> KNOWLEDGE_TYPES = Set.of(
        KnowledgeTypeCode.PRIMARY_TEMPLATE.getCode(), KnowledgeTypeCode.WRITING_TECHNIQUE.getCode(),
        KnowledgeTypeCode.PSYCHOLOGY.getCode(), KnowledgeTypeCode.CASE.getCode(),
        KnowledgeTypeCode.MANDATORY_RULE.getCode()
    );
    private static final Set<String> VERSION_STATUSES = Set.of(
        KnowledgeVersionStatus.DRAFT.getCode(), KnowledgeVersionStatus.REVIEWING.getCode(),
        KnowledgeVersionStatus.PUBLISHED.getCode(), KnowledgeVersionStatus.RETIRED.getCode()
    );

    private final KnowledgeItemMapper itemMapper;
    private final KnowledgeVersionMapper versionMapper;
    private final KnowledgeBindingMapper bindingMapper;

    @Override
    public PageResult<KnowledgeItemAdminVo> page(KnowledgeItemAdminQueryBo query, PageQuery pageQuery) {
        KnowledgeItemAdminQueryBo filters = query == null ? new KnowledgeItemAdminQueryBo() : query;
        LambdaQueryWrapper<KnowledgeItem> wrapper = new LambdaQueryWrapper<>();
        if (filters.getName() != null && !filters.getName().isBlank()) {
            wrapper.like(KnowledgeItem::getName, filters.getName().trim());
        }
        if (filters.getKnowledgeType() != null && !filters.getKnowledgeType().isBlank()) {
            wrapper.eq(KnowledgeItem::getKnowledgeTypeCode,
                filters.getKnowledgeType().trim().toLowerCase(Locale.ROOT));
        }
        if (filters.getStatus() != null && !filters.getStatus().isBlank()) {
            wrapper.apply("EXISTS (SELECT 1 FROM av_knowledge_version kv "
                    + "WHERE kv.knowledge_item_id = av_knowledge_item.knowledge_item_id "
                    + "AND kv.version_no = (SELECT MAX(kv2.version_no) FROM av_knowledge_version kv2 "
                    + "WHERE kv2.knowledge_item_id = av_knowledge_item.knowledge_item_id) "
                    + "AND kv.status = {0})",
                filters.getStatus().trim().toLowerCase(Locale.ROOT));
        }
        wrapper.orderByDesc(KnowledgeItem::getUpdateTime)
            .orderByDesc(KnowledgeItem::getKnowledgeItemId);

        PageQuery effectivePage = safePageQuery(pageQuery);
        IPage<KnowledgeItem> page = itemMapper.selectPage(effectivePage.build(), wrapper);
        if (page.getRecords().isEmpty()) {
            return PageResult.build(List.of(), page.getTotal());
        }

        List<Long> itemIds = page.getRecords().stream().map(KnowledgeItem::getKnowledgeItemId).toList();
        List<KnowledgeVersion> versions = versionMapper.selectList(new LambdaQueryWrapper<KnowledgeVersion>()
            .select(KnowledgeVersion::getKnowledgeItemId, KnowledgeVersion::getKnowledgeVersionId,
                KnowledgeVersion::getVersionNo, KnowledgeVersion::getStatus)
            .in(KnowledgeVersion::getKnowledgeItemId, itemIds)
            .orderByDesc(KnowledgeVersion::getVersionNo)
            .orderByDesc(KnowledgeVersion::getKnowledgeVersionId));
        Map<Long, KnowledgeVersion> latestByItem = new HashMap<>();
        for (KnowledgeVersion version : versions) {
            latestByItem.putIfAbsent(version.getKnowledgeItemId(), version);
        }
        List<KnowledgeItemAdminVo> rows = page.getRecords().stream()
            .map(item -> toVo(item, latestByItem.get(item.getKnowledgeItemId())))
            .toList();
        return PageResult.build(rows, page.getTotal());
    }

    private PageQuery safePageQuery(PageQuery requestedPage) {
        int pageNum = requestedPage == null || requestedPage.getPageNum() == null
            || requestedPage.getPageNum() <= 0 ? 1 : requestedPage.getPageNum();
        int requestedSize = requestedPage == null || requestedPage.getPageSize() == null
            || requestedPage.getPageSize() <= 0 ? DEFAULT_PAGE_SIZE : requestedPage.getPageSize();
        return new PageQuery(Math.min(requestedSize, MAX_PAGE_SIZE), pageNum);
    }

    @Override
    public KnowledgeItemDetailVo detail(Long knowledgeItemId) {
        KnowledgeItem item = requireItem(knowledgeItemId);
        KnowledgeVersion version = requireLatestVersion(knowledgeItemId);
        return new KnowledgeItemDetailVo(
            item.getKnowledgeItemId(), item.getName(), item.getKnowledgeTypeCode(), version.getStatus(),
            version.getVersionNo(), item.getSummary(), version.getContent(), item.getUpdateTime()
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(KnowledgeItemSaveBo bo, Long operatorId) {
        SaveValues values = saveValues(bo);
        Long itemId = IdWorker.getId();
        createItem(itemId, "manual_" + itemId, values.name(), values.knowledgeType(), values.status(),
            values.content(), values.summary(), MANUAL_SOURCE_TYPE, MANUAL_SOURCE_TYPE, actorId(operatorId));
        return itemId;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long knowledgeItemId, KnowledgeItemSaveBo bo, Long operatorId) {
        KnowledgeItem item = requireItem(knowledgeItemId);
        KnowledgeVersion latestVersion = requireLatestVersion(knowledgeItemId);
        SaveValues values = saveValues(bo);

        item.setName(values.name());
        item.setKnowledgeTypeCode(values.knowledgeType());
        item.setSummary(values.summary());
        KnowledgeVersion appendedVersion = new KnowledgeVersion();
        appendedVersion.setKnowledgeVersionId(IdWorker.getId());
        appendedVersion.setKnowledgeItemId(knowledgeItemId);
        appendedVersion.setVersionNo(latestVersion.getVersionNo() == null ? 1 : latestVersion.getVersionNo() + 1);
        appendedVersion.setContent(values.content());
        appendedVersion.setStructureJson(EMPTY_OBJECT_JSON);
        appendedVersion.setSourceSummary(values.summary());
        appendedVersion.setStatus(values.status());
        if (KnowledgeVersionStatus.PUBLISHED.getCode().equals(values.status())) {
            applyPublishedState(item, appendedVersion, actorId(operatorId));
        }

        requireWrite(versionMapper.insert(appendedVersion), "创建知识版本失败");
        requireWrite(bindingMapper.insert(newBinding(item, appendedVersion, values.status())), "创建知识绑定失败");
        requireWrite(itemMapper.updateById(item), "更新知识条目失败");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(Long knowledgeItemId, String status, Long operatorId) {
        KnowledgeItem item = requireItem(knowledgeItemId);
        KnowledgeVersion version = requireLatestVersion(knowledgeItemId);
        String normalizedStatus = normalizeStatus(status);
        if (normalizedStatus.equals(version.getStatus())) {
            return;
        }
        if (requiresAppendedVersion(version.getStatus(), normalizedStatus)) {
            appendStatusVersion(item, version, normalizedStatus, actorId(operatorId));
            return;
        }
        applyStatus(item, version, normalizedStatus, actorId(operatorId));
        requireWrite(versionMapper.updateById(version), "更新知识状态失败");
        requireWrite(itemMapper.updateById(item), "更新知识条目状态失败");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long knowledgeItemId) {
        requireItem(knowledgeItemId);
        Long protectedVersionCount = versionMapper.selectCount(new LambdaQueryWrapper<KnowledgeVersion>()
            .eq(KnowledgeVersion::getKnowledgeItemId, knowledgeItemId)
            .in(KnowledgeVersion::getStatus, List.of(
                KnowledgeVersionStatus.PUBLISHED.getCode(), KnowledgeVersionStatus.RETIRED.getCode())));
        if (protectedVersionCount != null && protectedVersionCount > 0) {
            throw new ServiceException("存在发布历史的知识条目不可物理删除，请改为退役");
        }
        bindingMapper.delete(new LambdaQueryWrapper<KnowledgeBinding>()
            .eq(KnowledgeBinding::getKnowledgeItemId, knowledgeItemId));
        versionMapper.delete(new LambdaQueryWrapper<KnowledgeVersion>()
            .eq(KnowledgeVersion::getKnowledgeItemId, knowledgeItemId));
        requireWrite(itemMapper.deleteById(knowledgeItemId), "删除知识条目失败");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeImportSummaryVo importFiles(List<MultipartFile> files, List<String> names,
                                                List<String> knowledgeTypes, List<String> statuses,
                                                Long operatorId) {
        if (files == null || files.isEmpty()) {
            throw new ServiceException("至少上传一个知识文件");
        }
        validateImportLimits(files);
        validateImportMetadata(files, names, knowledgeTypes, statuses);
        Long actorId = actorId(operatorId);
        ImportByteBudget byteBudget = new ImportByteBudget();
        List<KnowledgeImportFileVo> results = new ArrayList<>(files.size());
        Map<String, ImportedReference> importedInRequest = new LinkedHashMap<>();
        for (int index = 0; index < files.size(); index++) {
            MultipartFile file = files.get(index);
            String sourcePath = sourcePath(file);
            String fileName = fileName(file, sourcePath);
            if (isAppleDoublePath(sourcePath) || fileName.startsWith("._")) {
                results.add(fileResult(sourcePath, fileName, "skipped", "已排除 AppleDouble 文件", null));
                continue;
            }
            try {
                ValidKnowledgeFile valid = validate(file, fileName, byteBudget);
                String name = names.get(index);
                String knowledgeType = normalizeKnowledgeType(knowledgeTypes.get(index));
                String status = normalizeStatus(statuses.get(index));
                name = normalizeName(name);
                ImportedReference inRequest = importedInRequest.get(valid.stableCode());
                if (inRequest != null) {
                    results.add(fileResult(sourcePath, fileName, "skipped", "内容重复", inRequest));
                    continue;
                }
                ImportedReference existing = findExisting(valid.stableCode(), valid.content());
                if (existing != null) {
                    importedInRequest.put(valid.stableCode(), existing);
                    results.add(fileResult(sourcePath, fileName, "skipped", "内容已存在", existing));
                    continue;
                }
                Long itemId = IdWorker.getId();
                ImportedReference created = createItem(itemId, valid.stableCode(), name, knowledgeType, status,
                    valid.content(), truncate("知识文件导入：" + sourcePath, 500), IMPORT_SOURCE_TYPE,
                    truncate(sourcePath, 128), actorId);
                importedInRequest.put(valid.stableCode(), created);
                results.add(fileResult(sourcePath, fileName, "success", "导入成功", created));
            } catch (InvalidKnowledgeFileException exception) {
                results.add(fileResult(sourcePath, fileName, "failed", exception.getMessage(), null));
            }
        }
        int successCount = (int) results.stream().filter(result -> "success".equals(result.status())).count();
        int skippedCount = (int) results.stream().filter(result -> "skipped".equals(result.status())).count();
        int failedCount = results.size() - successCount - skippedCount;
        return new KnowledgeImportSummaryVo(results.size(), successCount, skippedCount, failedCount,
            List.copyOf(results));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publish(Long knowledgeItemId, Long operatorId) {
        changeStatus(knowledgeItemId, KnowledgeVersionStatus.PUBLISHED.getCode(), operatorId);
    }

    private KnowledgeItemAdminVo toVo(KnowledgeItem item, KnowledgeVersion latest) {
        return new KnowledgeItemAdminVo(
            item.getKnowledgeItemId(), item.getName(), item.getKnowledgeTypeCode(),
            latest == null ? null : latest.getStatus(), latest == null ? null : latest.getVersionNo(),
            item.getUpdateTime()
        );
    }

    private ImportedReference createItem(Long itemId, String stableCode, String name, String knowledgeType,
                                         String status, String content, String summary, String sourceType,
                                         String sourceRef, Long actorId) {
        LocalDateTime now = LocalDateTime.now();
        Long versionId = IdWorker.getId();

        KnowledgeItem item = new KnowledgeItem();
        item.setKnowledgeItemId(itemId);
        item.setDomainCode(KnowledgeDomainCode.COPYWRITING.getCode());
        item.setKnowledgeTypeCode(knowledgeType);
        item.setStableCode(stableCode);
        item.setName(name);
        item.setSummary(summary);
        item.setTagsJson(EMPTY_ARRAY_JSON);
        item.setCurrentPublishedVersionId(KnowledgeVersionStatus.PUBLISHED.getCode().equals(status) ? versionId : null);
        item.setSourceType(sourceType);
        item.setSourceRef(sourceRef);

        KnowledgeVersion version = new KnowledgeVersion();
        version.setKnowledgeVersionId(versionId);
        version.setKnowledgeItemId(itemId);
        version.setVersionNo(1);
        version.setStatus(status);
        version.setContent(content);
        version.setStructureJson(EMPTY_OBJECT_JSON);
        version.setSourceSummary(summary);
        if (KnowledgeVersionStatus.PUBLISHED.getCode().equals(status)) {
            version.setReviewedBy(actorId);
            version.setReviewedAt(now);
            version.setPublishedBy(actorId);
            version.setPublishedAt(now);
        }

        KnowledgeBinding binding = newBinding(item, version, status);
        requireWrite(itemMapper.insert(item), "创建知识条目失败");
        requireWrite(versionMapper.insert(version), "创建知识版本失败");
        requireWrite(bindingMapper.insert(binding), "创建知识绑定失败");
        return new ImportedReference(itemId, versionId, stableCode);
    }

    private KnowledgeBinding newBinding(KnowledgeItem item, KnowledgeVersion version, String status) {
        KnowledgeBinding binding = new KnowledgeBinding();
        binding.setKnowledgeBindingId(IdWorker.getId());
        binding.setBindingGroupCode(item.getStableCode());
        binding.setVersionNo(version.getVersionNo());
        binding.setKnowledgeItemId(item.getKnowledgeItemId());
        binding.setKnowledgeVersionId(version.getKnowledgeVersionId());
        binding.setIndustryCode("*");
        binding.setPurposeCode("*");
        binding.setVideoTypeCode("*");
        binding.setAngleCodesJson(EMPTY_ARRAY_JSON);
        binding.setAnglePrioritiesJson(EMPTY_OBJECT_JSON);
        binding.setPriority(0);
        binding.setRequiredFlag(false);
        binding.setRequiredSlotCodesJson(EMPTY_ARRAY_JSON);
        binding.setAudienceTagCodesJson(EMPTY_ARRAY_JSON);
        binding.setExclusionConditionsJson(EMPTY_ARRAY_JSON);
        binding.setStatus(status);
        return binding;
    }

    private ImportedReference findExisting(String stableCode, String content) {
        KnowledgeItem item = itemMapper.selectOne(new LambdaQueryWrapper<KnowledgeItem>()
            .eq(KnowledgeItem::getStableCode, stableCode)
            .last("LIMIT 1"));
        KnowledgeVersion version = null;
        if (item == null) {
            version = versionMapper.selectOne(new LambdaQueryWrapper<KnowledgeVersion>()
                .eq(KnowledgeVersion::getContent, content)
                .orderByDesc(KnowledgeVersion::getVersionNo)
                .last("LIMIT 1"));
            if (version != null) {
                item = itemMapper.selectById(version.getKnowledgeItemId());
            }
        }
        if (item == null) {
            return null;
        }
        Long versionId = item.getCurrentPublishedVersionId();
        if (versionId == null) {
            if (version == null) {
                version = versionMapper.selectOne(new LambdaQueryWrapper<KnowledgeVersion>()
                    .eq(KnowledgeVersion::getKnowledgeItemId, item.getKnowledgeItemId())
                    .orderByDesc(KnowledgeVersion::getVersionNo)
                    .last("LIMIT 1"));
            }
            versionId = version == null ? null : version.getKnowledgeVersionId();
        }
        return new ImportedReference(item.getKnowledgeItemId(), versionId, stableCode);
    }

    private ValidKnowledgeFile validate(MultipartFile file, String fileName, ImportByteBudget byteBudget) {
        if (file == null || file.isEmpty()) {
            throw new InvalidKnowledgeFileException("文件为空");
        }
        String extension = extension(fileName);
        if (!SUPPORTED_EXTENSIONS.contains(extension)) {
            throw new InvalidKnowledgeFileException("仅支持 md、markdown、txt、text、json、csv、yaml、yml 文件");
        }
        String mimeType = normalizeMimeType(file.getContentType());
        if (!SUPPORTED_MIME_TYPES.contains(mimeType)) {
            throw new InvalidKnowledgeFileException("文件 MIME 类型不受支持");
        }
        byte[] bytes;
        try (InputStream inputStream = file.getInputStream()) {
            bytes = inputStream.readNBytes(MAX_FILE_BYTES + 1);
        } catch (IOException exception) {
            throw new InvalidKnowledgeFileException("读取文件失败");
        }
        if (bytes.length > MAX_FILE_BYTES) {
            throw new InvalidKnowledgeFileException("单个文件不能超过10MB");
        }
        byteBudget.consume(bytes.length);
        rejectObviousBinaryContent(bytes);
        String content;
        try {
            content = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException exception) {
            throw new InvalidKnowledgeFileException("文件不是有效 UTF-8");
        }
        if (!content.isEmpty() && content.charAt(0) == '\uFEFF') {
            content = content.substring(1);
        }
        if (content.isBlank()) {
            throw new InvalidKnowledgeFileException("文件内容为空");
        }
        String stableCode = sha256(content.getBytes(StandardCharsets.UTF_8));
        return new ValidKnowledgeFile(content, stableCode);
    }

    private void validateImportLimits(List<MultipartFile> files) {
        if (files.size() > MAX_IMPORT_FILES) {
            throw new ServiceException("单次最多上传20个知识文件");
        }
        long totalBytes = 0L;
        for (MultipartFile file : files) {
            if (file == null) {
                throw new ServiceException("上传文件不能为空");
            }
            long fileSize = Math.max(file.getSize(), 0L);
            if (fileSize > MAX_FILE_BYTES) {
                throw new ServiceException("单个文件不能超过10MB");
            }
            totalBytes += fileSize;
            if (totalBytes > MAX_TOTAL_BYTES) {
                throw new ServiceException("单次上传文件总大小不能超过20MB");
            }
        }
    }

    private void validateImportMetadata(List<MultipartFile> files, List<String> names,
                                        List<String> knowledgeTypes, List<String> statuses) {
        if (names == null || knowledgeTypes == null || statuses == null) {
            throw new ServiceException("每个文件的名称、知识类型和状态都不能为空");
        }
        int fileCount = files.size();
        if (names.size() != fileCount || knowledgeTypes.size() != fileCount || statuses.size() != fileCount) {
            throw new ServiceException("文件与名称、知识类型、状态必须一一对应");
        }
        for (int index = 0; index < fileCount; index++) {
            if (names.get(index) == null || names.get(index).isBlank()
                || knowledgeTypes.get(index) == null || knowledgeTypes.get(index).isBlank()
                || statuses.get(index) == null || statuses.get(index).isBlank()) {
                throw new ServiceException("名称、知识类型和状态不能为空");
            }
        }
    }

    private String normalizeMimeType(String contentType) {
        if (contentType == null) {
            return "";
        }
        int parameterSeparator = contentType.indexOf(';');
        String mimeType = parameterSeparator < 0 ? contentType : contentType.substring(0, parameterSeparator);
        return mimeType.trim().toLowerCase(Locale.ROOT);
    }

    private void rejectObviousBinaryContent(byte[] bytes) {
        if (startsWith(bytes, "%PDF-") || startsWith(bytes, "GIF87a") || startsWith(bytes, "GIF89a")) {
            throw new InvalidKnowledgeFileException("文件包含明显二进制内容");
        }
        for (byte current : bytes) {
            int unsigned = Byte.toUnsignedInt(current);
            if (unsigned == 0 || unsigned < 0x09 || unsigned == 0x0B || unsigned == 0x0C
                || (unsigned > 0x0D && unsigned < 0x20) || unsigned == 0x7F) {
                throw new InvalidKnowledgeFileException("文件包含明显二进制内容");
            }
        }
    }

    private boolean startsWith(byte[] bytes, String signature) {
        byte[] signatureBytes = signature.getBytes(StandardCharsets.US_ASCII);
        if (bytes.length < signatureBytes.length) {
            return false;
        }
        for (int index = 0; index < signatureBytes.length; index++) {
            if (bytes[index] != signatureBytes[index]) {
                return false;
            }
        }
        return true;
    }

    private void applyStatus(KnowledgeItem item, KnowledgeVersion version, String status, Long actorId) {
        String published = KnowledgeVersionStatus.PUBLISHED.getCode();
        if (published.equals(status)) {
            applyPublishedState(item, version, actorId);
        } else if (version.getKnowledgeVersionId().equals(item.getCurrentPublishedVersionId())) {
            item.setCurrentPublishedVersionId(null);
        }
        version.setStatus(status);
        synchronizeBinding(item, version, status);
    }

    private boolean requiresAppendedVersion(String currentStatus, String targetStatus) {
        boolean historical = KnowledgeVersionStatus.PUBLISHED.getCode().equals(currentStatus)
            || KnowledgeVersionStatus.RETIRED.getCode().equals(currentStatus);
        boolean retirePublished = KnowledgeVersionStatus.PUBLISHED.getCode().equals(currentStatus)
            && KnowledgeVersionStatus.RETIRED.getCode().equals(targetStatus);
        return historical && !retirePublished;
    }

    private void appendStatusVersion(KnowledgeItem item, KnowledgeVersion sourceVersion, String status,
                                     Long actorId) {
        KnowledgeVersion appendedVersion = new KnowledgeVersion();
        appendedVersion.setKnowledgeVersionId(IdWorker.getId());
        appendedVersion.setKnowledgeItemId(item.getKnowledgeItemId());
        appendedVersion.setVersionNo(sourceVersion.getVersionNo() == null ? 1 : sourceVersion.getVersionNo() + 1);
        appendedVersion.setStatus(status);
        appendedVersion.setContent(sourceVersion.getContent());
        appendedVersion.setStructureJson(sourceVersion.getStructureJson());
        appendedVersion.setSourceSummary(sourceVersion.getSourceSummary());
        if (KnowledgeVersionStatus.PUBLISHED.getCode().equals(status)) {
            applyPublishedState(item, appendedVersion, actorId);
        }
        requireWrite(versionMapper.insert(appendedVersion), "创建知识版本失败");
        requireWrite(bindingMapper.insert(newBinding(item, appendedVersion, status)), "创建知识绑定失败");
        requireWrite(itemMapper.updateById(item), "更新知识条目状态失败");
    }

    private void applyPublishedState(KnowledgeItem item, KnowledgeVersion version, Long actorId) {
        String published = KnowledgeVersionStatus.PUBLISHED.getCode();
        versionMapper.update(null, new LambdaUpdateWrapper<KnowledgeVersion>()
            .eq(KnowledgeVersion::getKnowledgeItemId, item.getKnowledgeItemId())
            .eq(KnowledgeVersion::getStatus, published)
            .ne(KnowledgeVersion::getKnowledgeVersionId, version.getKnowledgeVersionId())
            .set(KnowledgeVersion::getStatus, KnowledgeVersionStatus.RETIRED.getCode()));
        bindingMapper.update(null, new LambdaUpdateWrapper<KnowledgeBinding>()
            .eq(KnowledgeBinding::getKnowledgeItemId, item.getKnowledgeItemId())
            .eq(KnowledgeBinding::getStatus, published)
            .ne(KnowledgeBinding::getKnowledgeVersionId, version.getKnowledgeVersionId())
            .set(KnowledgeBinding::getStatus, KnowledgeVersionStatus.RETIRED.getCode()));
        LocalDateTime now = LocalDateTime.now();
        version.setReviewedBy(actorId);
        version.setReviewedAt(now);
        version.setPublishedBy(actorId);
        version.setPublishedAt(now);
        item.setCurrentPublishedVersionId(version.getKnowledgeVersionId());
    }

    private void synchronizeBinding(KnowledgeItem item, KnowledgeVersion version, String status) {
        KnowledgeBinding binding = bindingMapper.selectOne(new LambdaQueryWrapper<KnowledgeBinding>()
            .eq(KnowledgeBinding::getKnowledgeItemId, item.getKnowledgeItemId())
            .eq(KnowledgeBinding::getKnowledgeVersionId, version.getKnowledgeVersionId())
            .last("LIMIT 1"));
        if (binding == null) {
            requireWrite(bindingMapper.insert(newBinding(item, version, status)), "创建知识绑定失败");
            return;
        }
        binding.setStatus(status);
        requireWrite(bindingMapper.updateById(binding), "更新知识绑定状态失败");
    }

    private KnowledgeItem requireItem(Long knowledgeItemId) {
        if (knowledgeItemId == null) {
            throw new ServiceException("知识条目编号不能为空");
        }
        KnowledgeItem item = itemMapper.selectById(knowledgeItemId);
        if (item == null) {
            throw new ServiceException("知识条目不存在");
        }
        return item;
    }

    private KnowledgeVersion requireLatestVersion(Long knowledgeItemId) {
        KnowledgeVersion version = versionMapper.selectOne(new LambdaQueryWrapper<KnowledgeVersion>()
            .eq(KnowledgeVersion::getKnowledgeItemId, knowledgeItemId)
            .orderByDesc(KnowledgeVersion::getVersionNo)
            .orderByDesc(KnowledgeVersion::getKnowledgeVersionId)
            .last("LIMIT 1"));
        if (version == null) {
            throw new ServiceException("知识版本不存在");
        }
        return version;
    }

    private SaveValues saveValues(KnowledgeItemSaveBo bo) {
        if (bo == null) {
            throw new ServiceException("知识内容不能为空");
        }
        String name = normalizeName(bo.getName());
        String content = bo.getContent() == null ? null : bo.getContent().trim();
        if (content == null || content.isEmpty()) {
            throw new ServiceException("知识正文不能为空");
        }
        String summary = bo.getSummary() == null || bo.getSummary().isBlank() ? null : bo.getSummary().trim();
        if (summary != null && summary.length() > 500) {
            throw new ServiceException("知识摘要不能超过500个字符");
        }
        return new SaveValues(name, normalizeKnowledgeType(bo.getKnowledgeType()), normalizeStatus(bo.getStatus()),
            content, summary);
    }

    private String normalizeName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new InvalidKnowledgeFileException("知识名称不能为空");
        }
        String normalized = name.trim();
        if (normalized.length() > 255) {
            throw new InvalidKnowledgeFileException("知识名称不能超过255个字符");
        }
        return normalized;
    }

    private String normalizeKnowledgeType(String knowledgeType) {
        String normalized = normalizeCode(knowledgeType);
        if (!KNOWLEDGE_TYPES.contains(normalized)) {
            throw new InvalidKnowledgeFileException("知识类型不合法");
        }
        return normalized;
    }

    private String normalizeStatus(String status) {
        String normalized = normalizeCode(status);
        if (!VERSION_STATUSES.contains(normalized)) {
            throw new InvalidKnowledgeFileException("知识状态不合法");
        }
        return normalized;
    }

    private String normalizeCode(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String extension(String fileName) {
        String normalized = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        int dot = normalized.lastIndexOf('.');
        return dot < 0 ? "" : normalized.substring(dot);
    }

    private Long actorId(Long operatorId) {
        return operatorId == null ? 0L : operatorId;
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM 不支持 SHA-256", exception);
        }
    }

    private KnowledgeImportFileVo fileResult(String sourcePath, String fileName, String status, String message,
                                             ImportedReference reference) {
        return new KnowledgeImportFileVo(sourcePath, fileName, status, message,
            reference == null ? null : reference.itemId(),
            reference == null ? null : reference.versionId(),
            reference == null ? null : reference.stableCode());
    }

    private String sourcePath(MultipartFile file) {
        String original = file == null ? null : file.getOriginalFilename();
        return original == null || original.isBlank() ? "unnamed" : original.trim().replace('\\', '/');
    }

    private String fileName(MultipartFile file, String sourcePath) {
        String original = file == null ? null : file.getOriginalFilename();
        String candidate = original == null || original.isBlank() ? sourcePath : original;
        candidate = candidate.replace('\\', '/');
        int separator = candidate.lastIndexOf('/');
        return separator < 0 ? candidate : candidate.substring(separator + 1);
    }

    private boolean isAppleDoublePath(String sourcePath) {
        for (String segment : sourcePath.replace('\\', '/').split("/")) {
            if (segment.startsWith("._")) {
                return true;
            }
        }
        return false;
    }

    private String truncate(String value, int maximumLength) {
        return value.length() <= maximumLength ? value : value.substring(0, maximumLength);
    }

    private void requireWrite(int affectedRows, String message) {
        if (affectedRows != 1) {
            throw new ServiceException(message);
        }
    }

    private record ValidKnowledgeFile(String content, String stableCode) {
    }

    private record SaveValues(String name, String knowledgeType, String status, String content, String summary) {
    }

    private record ImportedReference(Long itemId, Long versionId, String stableCode) {
    }

    private static final class ImportByteBudget {
        private long consumedBytes;

        private void consume(long bytes) {
            consumedBytes += bytes;
            if (consumedBytes > MAX_TOTAL_BYTES) {
                throw new ServiceException("单次上传文件实际总大小不能超过20MB");
            }
        }
    }

    private static final class InvalidKnowledgeFileException extends RuntimeException {
        private InvalidKnowledgeFileException(String message) {
            super(message);
        }
    }
}
