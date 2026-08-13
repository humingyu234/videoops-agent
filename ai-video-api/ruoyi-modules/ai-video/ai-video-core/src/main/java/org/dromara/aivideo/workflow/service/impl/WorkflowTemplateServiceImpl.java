package org.dromara.aivideo.workflow.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.aivideo.workflow.constant.WorkflowErrorCodes;
import org.dromara.aivideo.workflow.domain.DiscoveryTag;
import org.dromara.aivideo.workflow.domain.RunningHubAccount;
import org.dromara.aivideo.workflow.domain.WorkflowExecutionConfig;
import org.dromara.aivideo.workflow.domain.WorkflowTemplate;
import org.dromara.aivideo.workflow.dto.WorkflowTemplateDTOs;
import org.dromara.aivideo.workflow.enums.WorkflowChannel;
import org.dromara.aivideo.workflow.enums.WorkflowCredentialPurpose;
import org.dromara.aivideo.workflow.enums.WorkflowExecutionMode;
import org.dromara.aivideo.workflow.enums.WorkflowTemplateStatus;
import org.dromara.aivideo.workflow.mapper.DiscoveryCategoryMapper;
import org.dromara.aivideo.workflow.mapper.DiscoveryTagMapper;
import org.dromara.aivideo.workflow.mapper.RunningHubAccountMapper;
import org.dromara.aivideo.workflow.mapper.WorkflowExecutionConfigMapper;
import org.dromara.aivideo.workflow.mapper.WorkflowTemplateMapper;
import org.dromara.aivideo.workflow.service.IWorkflowCredentialWriteService;
import org.dromara.aivideo.workflow.service.IWorkflowTemplateService;
import org.dromara.aivideo.workflow.validation.WorkflowSchemaCanonicalizer;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.system.domain.vo.SysOssVo;
import org.dromara.system.service.ISysOssService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class WorkflowTemplateServiceImpl implements IWorkflowTemplateService {

    private static final long CATALOG_TENANT_ID = 0L;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final Pattern SLUG_PATTERN = Pattern.compile("[a-z0-9][a-z0-9-]{0,127}");
    private static final Set<String> ADMIN_SORTS = Set.of("latest", "name", "sort_no");
    private static final Set<String> PUBLIC_SORTS = Set.of("latest", "recommended");

    private final WorkflowTemplateMapper templateMapper;
    private final WorkflowExecutionConfigMapper executionConfigMapper;
    private final RunningHubAccountMapper accountMapper;
    private final DiscoveryCategoryMapper categoryMapper;
    private final DiscoveryTagMapper tagMapper;
    private final IWorkflowCredentialWriteService credentialWriteService;
    private final ISysOssService ossService;
    private final WorkflowSchemaCanonicalizer schemaCanonicalizer;
    private final JsonMapper jsonMapper;

    @Override
    public PageResult<WorkflowTemplateDTOs.AdminSummary> queryAdminPage(
        WorkflowTemplateDTOs.AdminQuery query, PageQuery pageQuery) {
        WorkflowTemplateDTOs.AdminQuery safeQuery = normalizeAdminQuery(query);
        Page<WorkflowTemplateDTOs.TemplateRow> page = templateMapper.selectAdminPage(
            buildPage(pageQuery), CATALOG_TENANT_ID, safeQuery);
        return PageResult.build(page.getRecords().stream().map(this::toAdminSummary).toList(), page.getTotal());
    }

    @Override
    public WorkflowTemplateDTOs.AdminDetail queryAdminDetail(String templateId) {
        long id = parseId(templateId, "模板编号");
        WorkflowTemplateDTOs.TemplateRow row = templateMapper.selectAdminDetail(CATALOG_TENANT_ID, id);
        if (row == null) {
            throw invalid("工作流模板不存在");
        }
        return toAdminDetail(row);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String create(Long actorId, WorkflowTemplateDTOs.Save command) {
        long actor = requireActorId(actorId);
        WorkflowSchemaCanonicalizer.CanonicalSchema schema = validateSave(command, false);
        long templateId = IdWorker.getId();
        WorkflowTemplate template = buildTemplate(templateId, command, schema, internalSlug(templateId));
        template.setStatus(WorkflowTemplateStatus.DRAFT.getValue());
        template.setBillingMode("free");
        template.setEnabledAt(null);
        template.setRowRevision(0L);
        template.setDelFlag("0");
        template.setCreateBy(actor);
        template.setUpdateBy(actor);
        try {
            assertExactlyOne(templateMapper.insert(template), "工作流模板创建失败");
        } catch (DuplicateKeyException exception) {
            throw new ServiceException("模板名称或 slug 已存在", WorkflowErrorCodes.WORKFLOW_REFERENCE_CONFLICT);
        }
        return Long.toString(templateId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long actorId, String templateId, WorkflowTemplateDTOs.Save command) {
        long actor = requireActorId(actorId);
        long id = parseId(templateId, "模板编号");
        WorkflowTemplate current = requireTemplate(id);
        WorkflowSchemaCanonicalizer.CanonicalSchema schema = validateSave(command, true);
        long expectedRevision = requireExpectedRevision(command.expectedRevision());
        WorkflowTemplate update = buildTemplate(id, command, schema, current.getSlug());
        update.setStatus(current.getStatus());
        update.setUpdateBy(actor);
        try {
            assertTemplateCas(templateMapper.updateContentCas(update, expectedRevision, actor));
        } catch (DuplicateKeyException exception) {
            throw new ServiceException("模板名称或 slug 已存在", WorkflowErrorCodes.WORKFLOW_REFERENCE_CONFLICT);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long actorId, String templateId, long expectedRevision) {
        long actor = requireActorId(actorId);
        long id = parseId(templateId, "模板编号");
        requireExpectedRevision(expectedRevision);
        WorkflowTemplate current = requireTemplateForUpdate(id);
        if (WorkflowTemplateStatus.ENABLED.getValue().equals(current.getStatus())) {
            throw new ServiceException("已启用模板不能删除", WorkflowErrorCodes.WORKFLOW_REFERENCE_CONFLICT);
        }
        executionConfigMapper.logicalDeleteCurrent(CATALOG_TENANT_ID, id, actor);
        assertTemplateCas(templateMapper.logicalDelete(CATALOG_TENANT_ID, id, expectedRevision, actor));
    }

    @Override
    public WorkflowTemplateDTOs.ExecutionConfig queryExecutionConfig(String templateId) {
        long id = parseId(templateId, "模板编号");
        requireTemplate(id);
        WorkflowExecutionConfig config = executionConfigMapper.selectCurrent(CATALOG_TENANT_ID, id);
        if (config == null) {
            throw executionUnavailable();
        }
        return toExecutionConfig(config);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkflowTemplateDTOs.ExecutionConfig saveExecutionConfig(
        Long actorId, String templateId, WorkflowTemplateDTOs.ExecutionConfigSave command) {
        char[] password = command == null ? null : command.accessPassword();
        try {
            long actor = requireActorId(actorId);
            long id = parseId(templateId, "模板编号");
            validateExecutionConfig(command);
            long accountId = parseId(command.runningHubAccountId(), "RunningHub 账号编号");
            requireTemplateForUpdate(id);
            if (accountMapper.selectCatalogAccountForUpdate(CATALOG_TENANT_ID, accountId) == null) {
                throw invalid("RunningHub 账号不存在");
            }
            WorkflowExecutionConfig current = executionConfigMapper.selectCurrent(CATALOG_TENANT_ID, id);
            if (current == null && command.expectedRevision() != 0) {
                throw revisionConflict("执行配置修订冲突");
            }
            if (current != null && command.expectedRevision() == 0) {
                throw revisionConflict("执行配置修订冲突");
            }
            WorkflowExecutionConfig config = buildExecutionConfig(id, accountId, command, current);
            config.setUpdateBy(actor);
            if (command.clearAccessPassword()) {
                config.setAccessPasswordCiphertext(null);
            } else if (hasText(password)) {
                config.setAccessPasswordCiphertext(credentialWriteService.encryptForStorage(
                    WorkflowCredentialPurpose.RUNNINGHUB_ACCESS_PASSWORD, password));
            }
            try {
                if (current == null) {
                    config.setExecutionConfigId(null);
                    config.setLastTestStatus("never");
                    config.setRowRevision(1L);
                    config.setDelFlag("0");
                    config.setCreateBy(actor);
                    assertExactlyOne(executionConfigMapper.insert(config), "执行配置创建失败");
                } else {
                    assertConfigCas(executionConfigMapper.updateCurrentCas(
                        config, command.expectedRevision(), actor));
                    config.setExecutionConfigId(current.getExecutionConfigId());
                    config.setLastTestStatus(current.getLastTestStatus());
                    config.setRowRevision(command.expectedRevision() + 1);
                    config.setUpdateTime(LocalDateTime.now());
                }
            } catch (DuplicateKeyException exception) {
                throw new ServiceException("模板只能有一条当前执行配置",
                    WorkflowErrorCodes.WORKFLOW_REFERENCE_CONFLICT);
            }
            return toExecutionConfig(config);
        } finally {
            clear(password);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void enable(Long actorId, String templateId, long expectedRevision) {
        long actor = requireActorId(actorId);
        long id = parseId(templateId, "模板编号");
        WorkflowTemplate template = requireTemplate(id);
        if (template.getCategoryId() == null
            || categoryMapper.selectActiveById(template.getCategoryId()) == null) {
            throw invalid("模板分类不可用");
        }
        WorkflowExecutionConfig config = executionConfigMapper.selectCurrent(CATALOG_TENANT_ID, id);
        if (config == null || !Boolean.TRUE.equals(config.getEnabled())) {
            throw executionUnavailable();
        }
        RunningHubAccount account = accountMapper.selectCatalogAccount(
            CATALOG_TENANT_ID, config.getRunninghubAccountId());
        if (account == null || !Boolean.TRUE.equals(account.getEnabled())) {
            throw executionUnavailable();
        }
        assertTemplateCas(templateMapper.updateStatusCas(CATALOG_TENANT_ID, id, expectedRevision,
            WorkflowTemplateStatus.ENABLED.getValue(), true, actor));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void disable(Long actorId, String templateId, long expectedRevision) {
        long actor = requireActorId(actorId);
        long id = parseId(templateId, "模板编号");
        requireTemplate(id);
        assertTemplateCas(templateMapper.updateStatusCas(CATALOG_TENANT_ID, id, expectedRevision,
            WorkflowTemplateStatus.DISABLED.getValue(), false, actor));
    }

    @Override
    public List<WorkflowTemplateDTOs.Option> queryOptions() {
        return templateMapper.selectOptions(CATALOG_TENANT_ID).stream()
            .map(row -> new WorkflowTemplateDTOs.Option(
                Long.toString(row.getTemplateId()), row.getName(), row.getStatus()))
            .toList();
    }

    @Override
    public PageResult<WorkflowTemplateDTOs.PublicCard> queryVisiblePage(
        WorkflowTemplateDTOs.PublicQuery query, PageQuery pageQuery) {
        WorkflowTemplateDTOs.PublicQuery safeQuery = normalizePublicQuery(query);
        Page<WorkflowTemplateDTOs.TemplateRow> page = templateMapper.selectVisiblePage(
            buildPage(pageQuery), safeQuery);
        Map<Long, String> tagNames = loadTagNames();
        Map<Long, String> coverUrls = resolveCoverUrls(page.getRecords());
        return PageResult.build(page.getRecords().stream().map(row -> toPublicCard(row, tagNames, coverUrls)).toList(),
            page.getTotal());
    }

    @Override
    public WorkflowTemplateDTOs.PublicDetail queryVisibleDetail(String templateId) {
        long id = parseId(templateId, "模板编号");
        WorkflowTemplateDTOs.TemplateRow row = requireVisibleRow(id);
        WorkflowTemplateDTOs.PublicCard card = toPublicCard(row, loadTagNames(), resolveCoverUrls(List.of(row)));
        WorkflowSchemaCanonicalizer.CanonicalSchema schema = schemaCanonicalizer.canonicalize(row.getFormSchemaJson());
        List<WorkflowTemplateDTOs.RequiredInput> inputs = schema.requiredInputs().stream()
            .map(input -> new WorkflowTemplateDTOs.RequiredInput(input.semanticKey(), input.label(),
                input.valueType(), input.assetType(), input.required()))
            .toList();
        return new WorkflowTemplateDTOs.PublicDetail(
            card.templateId(), card.title(), card.summary(), card.channel(), card.category(), card.tags(),
            card.cover(), card.preview(), card.usageCount(), card.estimatedDurationSeconds(), card.enabledAt(),
            defaultString(row.getDescription()), List.of(), inputs);
    }

    @Override
    public WorkflowTemplateDTOs.CreationConfig queryCreationConfig(String templateId) {
        long id = parseId(templateId, "模板编号");
        WorkflowTemplateDTOs.TemplateRow row = requireVisibleRow(id);
        WorkflowSchemaCanonicalizer.CanonicalSchema schema = schemaCanonicalizer.canonicalize(row.getFormSchemaJson());
        return new WorkflowTemplateDTOs.CreationConfig(
            Long.toString(row.getTemplateId()), WorkflowSchemaCanonicalizer.SCHEMA_VERSION, schema.schemaHash(),
            parseInputFields(schema.canonicalJson()), row.getEstimatedDurationSeconds(),
            new WorkflowTemplateDTOs.BillingPolicy("free"));
    }

    @Override
    public WorkflowTemplateDTOs.DiscoveryHome queryDiscoveryHome() {
        Map<Long, String> tagNames = loadTagNames();
        List<WorkflowTemplateDTOs.TemplateRow> recommendationRows = templateMapper.selectVisibleRecommendations(6);
        Map<Long, String> coverUrls = resolveCoverUrls(recommendationRows);
        List<WorkflowTemplateDTOs.PublicCard> recommendations = recommendationRows.stream()
            .map(row -> toPublicCard(row, tagNames, coverUrls)).toList();
        List<WorkflowTemplateDTOs.ChannelSummary> channels = templateMapper.selectVisibleChannelCounts().stream()
            .map(row -> new WorkflowTemplateDTOs.ChannelSummary(row.getCode(), channelLabel(row.getCode()),
                channelDescription(row.getCode()), Long.toString(valueOrZero(row.getTemplateCount()))))
            .toList();
        List<WorkflowTemplateDTOs.CategorySummary> categories = templateMapper.selectVisibleCategoryCounts().stream()
            .map(row -> new WorkflowTemplateDTOs.CategorySummary(row.getCode(), row.getLabel(),
                Long.toString(valueOrZero(row.getTemplateCount()))))
            .toList();
        List<WorkflowTemplateDTOs.Tag> tags = tagNames.entrySet().stream()
            .map(entry -> new WorkflowTemplateDTOs.Tag(Long.toString(entry.getKey()), entry.getValue()))
            .toList();
        return new WorkflowTemplateDTOs.DiscoveryHome(List.of(), recommendations, channels, categories, tags);
    }

    private WorkflowTemplate buildTemplate(Long templateId, WorkflowTemplateDTOs.Save command,
                                           WorkflowSchemaCanonicalizer.CanonicalSchema schema, String slug) {
        WorkflowTemplate template = new WorkflowTemplate();
        template.setTemplateId(templateId);
        template.setTenantId(CATALOG_TENANT_ID);
        template.setChannel(command.channel());
        template.setName(requiredText(command.name(), "模板名称"));
        template.setSlug(normalizeSlug(slug));
        template.setSummary(optionalText(command.summary()));
        template.setDescription(optionalText(command.description()));
        template.setCoverAssetId(optionalId(command.coverAssetId(), "封面素材编号"));
        template.setCategoryId(parseId(command.categoryId(), "分类编号"));
        template.setTagsJson(normalizeTags(command.tagsJson()));
        template.setFormSchemaJson(schema.canonicalJson());
        template.setSchemaHash(schema.schemaHash());
        template.setRecommended(command.recommended());
        template.setSortNo(command.sortNo());
        template.setEstimatedDurationSeconds(command.estimatedDurationSeconds());
        template.setBillingMode("free");
        template.setExecutionRelevantUpdatedAt(LocalDateTime.now());
        return template;
    }

    private WorkflowExecutionConfig buildExecutionConfig(long templateId, long accountId,
                                                          WorkflowTemplateDTOs.ExecutionConfigSave command,
                                                          WorkflowExecutionConfig current) {
        WorkflowExecutionConfig config = new WorkflowExecutionConfig();
        config.setExecutionConfigId(current == null ? null : current.getExecutionConfigId());
        config.setTenantId(CATALOG_TENANT_ID);
        config.setTemplateId(templateId);
        config.setRunninghubAccountId(accountId);
        config.setAccessPasswordCiphertext(current == null ? null : current.getAccessPasswordCiphertext());
        config.setExecutionMode(command.executionMode());
        config.setWorkflowId(optionalText(command.workflowId()));
        config.setWebappId(optionalText(command.webAppId()));
        config.setInstanceType(optionalText(command.instanceType()));
        config.setInputMappingJson(normalizeJsonObject(command.inputMappingJson(), "输入映射"));
        config.setOutputPolicyJson(normalizeJsonObject(command.outputPolicyJson(), "输出策略"));
        config.setTimeoutSeconds(command.timeoutSeconds());
        config.setEnabled(command.enabled());
        return config;
    }

    private WorkflowSchemaCanonicalizer.CanonicalSchema validateSave(WorkflowTemplateDTOs.Save command,
                                                                      boolean update) {
        if (command == null) {
            throw invalid("模板参数不能为空");
        }
        if (!WorkflowChannel.supports(command.channel())) {
            throw invalid("模板频道无效");
        }
        requiredText(command.name(), "模板名称");
        long categoryId = parseId(command.categoryId(), "分类编号");
        if (categoryMapper.selectActiveById(categoryId) == null) {
            throw invalid("模板分类不可用");
        }
        if (command.estimatedDurationSeconds() != null && command.estimatedDurationSeconds() < 0) {
            throw invalid("预计时长无效");
        }
        if (update) {
            requireExpectedRevision(command.expectedRevision());
        }
        return schemaCanonicalizer.canonicalize(command.formSchema());
    }

    private String internalSlug(long templateId) {
        return "workflow-" + templateId;
    }

    private void validateExecutionConfig(WorkflowTemplateDTOs.ExecutionConfigSave command) {
        if (command == null) {
            throw invalid("执行配置参数不能为空");
        }
        boolean workflow = WorkflowExecutionMode.RUNNINGHUB_WORKFLOW.getValue().equals(command.executionMode());
        boolean aiApp = WorkflowExecutionMode.RUNNINGHUB_AI_APP.getValue().equals(command.executionMode());
        if (!workflow && !aiApp) {
            throw invalid("执行模式无效");
        }
        boolean hasWorkflowId = command.workflowId() != null && !command.workflowId().isBlank();
        boolean hasWebAppId = command.webAppId() != null && !command.webAppId().isBlank();
        if ((workflow && (!hasWorkflowId || hasWebAppId)) || (aiApp && (!hasWebAppId || hasWorkflowId))) {
            throw invalid("Workflow ID 与 Web App ID 必须按模式互斥");
        }
        if (hasStringText(command.instanceType())
            && !"default".equals(command.instanceType())
            && !"plus".equals(command.instanceType())) {
            throw invalid("显存实例类型无效");
        }
        if (command.timeoutSeconds() <= 0) {
            throw invalid("超时时间无效");
        }
        if (command.expectedRevision() < 0) {
            throw invalid("修订号无效");
        }
        if (command.clearAccessPassword() && hasText(command.accessPassword())) {
            throw invalid("清除访问密码时不能同时提供新密码");
        }
    }

    private WorkflowTemplateDTOs.TemplateRow requireVisibleRow(long templateId) {
        WorkflowTemplateDTOs.TemplateRow row = templateMapper.selectVisibleDetail(templateId);
        if (row != null) {
            return row;
        }
        WorkflowTemplate template = templateMapper.selectCatalogTemplate(CATALOG_TENANT_ID, templateId);
        if (template == null || !WorkflowTemplateStatus.ENABLED.getValue().equals(template.getStatus())) {
            throw new ServiceException("工作流模板不可用", WorkflowErrorCodes.WORKFLOW_TEMPLATE_UNAVAILABLE);
        }
        throw executionUnavailable();
    }

    private WorkflowTemplate requireTemplate(long templateId) {
        WorkflowTemplate template = templateMapper.selectCatalogTemplate(CATALOG_TENANT_ID, templateId);
        if (template == null) {
            throw invalid("工作流模板不存在");
        }
        return template;
    }

    private WorkflowTemplate requireTemplateForUpdate(long templateId) {
        WorkflowTemplate template = templateMapper.selectCatalogTemplateForUpdate(CATALOG_TENANT_ID, templateId);
        if (template == null) {
            throw invalid("工作流模板不存在");
        }
        return template;
    }

    private WorkflowTemplateDTOs.AdminSummary toAdminSummary(WorkflowTemplateDTOs.TemplateRow row) {
        return new WorkflowTemplateDTOs.AdminSummary(
            Long.toString(row.getTemplateId()), row.getChannel(), row.getName(), row.getSlug(),
            defaultString(row.getSummary()), row.getStatus(), Boolean.TRUE.equals(row.getRecommended()),
            stringId(row.getCategoryId()), row.getCategoryName(), row.getExecutionConfigId() != null,
            Boolean.TRUE.equals(row.getExecutionEnabled()), row.getAccountName(), valueOrZero(row.getRowRevision()),
            row.getEnabledAt(), row.getUpdateTime());
    }

    private WorkflowTemplateDTOs.AdminDetail toAdminDetail(WorkflowTemplateDTOs.TemplateRow row) {
        return new WorkflowTemplateDTOs.AdminDetail(
            Long.toString(row.getTemplateId()), row.getChannel(), row.getName(), row.getSlug(),
            defaultString(row.getSummary()), defaultString(row.getDescription()), stringId(row.getCoverAssetId()),
            stringId(row.getCategoryId()), parseTagIds(row.getTagsJson()), row.getFormSchemaJson(),
            row.getSchemaHash(), row.getStatus(), Boolean.TRUE.equals(row.getRecommended()),
            row.getSortNo() == null ? 0 : row.getSortNo(), row.getEstimatedDurationSeconds(), row.getBillingMode(),
            valueOrZero(row.getRowRevision()), row.getEnabledAt(), row.getCreateTime(), row.getUpdateTime(),
            row.getExecutionConfigId() == null ? null : toExecutionConfig(row));
    }

    private WorkflowTemplateDTOs.ExecutionConfig toExecutionConfig(WorkflowExecutionConfig config) {
        return new WorkflowTemplateDTOs.ExecutionConfig(
            Long.toString(config.getExecutionConfigId()), Long.toString(config.getTemplateId()),
            Long.toString(config.getRunninghubAccountId()), config.getExecutionMode(), config.getWorkflowId(),
            config.getWebappId(), config.getInstanceType(), config.getInputMappingJson(), config.getOutputPolicyJson(),
            config.getTimeoutSeconds(), Boolean.TRUE.equals(config.getEnabled()),
            config.getAccessPasswordCiphertext() != null, config.getLastTestStatus(),
            valueOrZero(config.getRowRevision()), config.getUpdateTime());
    }

    private WorkflowTemplateDTOs.ExecutionConfig toExecutionConfig(WorkflowTemplateDTOs.TemplateRow row) {
        return new WorkflowTemplateDTOs.ExecutionConfig(
            Long.toString(row.getExecutionConfigId()), Long.toString(row.getTemplateId()),
            Long.toString(row.getRunninghubAccountId()), row.getExecutionMode(), row.getWorkflowId(),
            row.getWebappId(), row.getInstanceType(), row.getInputMappingJson(), row.getOutputPolicyJson(),
            row.getTimeoutSeconds() == null ? 1800 : row.getTimeoutSeconds(),
            Boolean.TRUE.equals(row.getExecutionEnabled()), Boolean.TRUE.equals(row.getHasAccessPassword()),
            row.getLastTestStatus(), valueOrZero(row.getExecutionRevision()), row.getExecutionUpdateTime());
    }

    private WorkflowTemplateDTOs.PublicCard toPublicCard(WorkflowTemplateDTOs.TemplateRow row,
                                                         Map<Long, String> tagNames,
                                                         Map<Long, String> coverUrls) {
        WorkflowTemplateDTOs.Category category = new WorkflowTemplateDTOs.Category(
            stringId(row.getCategoryId()), defaultString(row.getCategoryName()));
        List<WorkflowTemplateDTOs.Tag> tags = parseTagIds(row.getTagsJson()).stream()
            .map(Long::valueOf)
            .filter(tagNames::containsKey)
            .map(id -> new WorkflowTemplateDTOs.Tag(Long.toString(id), tagNames.get(id)))
            .toList();
        String coverUrl = row.getCoverAssetId() == null ? null : coverUrls.get(row.getCoverAssetId());
        WorkflowTemplateDTOs.Media cover = coverUrl == null || coverUrl.isBlank()
            ? null
            : new WorkflowTemplateDTOs.Media(Long.toString(row.getCoverAssetId()), "image", coverUrl,
                null, 0, 0, row.getName() + "封面");
        return new WorkflowTemplateDTOs.PublicCard(
            Long.toString(row.getTemplateId()), row.getName(), defaultString(row.getSummary()), row.getChannel(),
            category, tags, cover, null, null, row.getEstimatedDurationSeconds(), row.getEnabledAt());
    }

    private Map<Long, String> resolveCoverUrls(List<WorkflowTemplateDTOs.TemplateRow> rows) {
        List<Long> coverAssetIds = rows.stream().map(WorkflowTemplateDTOs.TemplateRow::getCoverAssetId)
            .filter(java.util.Objects::nonNull).distinct().toList();
        if (coverAssetIds.isEmpty()) {
            return Map.of();
        }
        return ossService.listByIds(coverAssetIds).stream()
            .filter(oss -> oss.getOssId() != null && oss.getUrl() != null && !oss.getUrl().isBlank())
            .collect(java.util.stream.Collectors.toMap(SysOssVo::getOssId, SysOssVo::getUrl, (left, right) -> left));
    }

    private List<WorkflowTemplateDTOs.InputField> parseInputFields(String canonicalJson) {
        try {
            JsonNode fields = jsonMapper.readTree(canonicalJson).required("fields");
            List<WorkflowTemplateDTOs.InputField> result = new ArrayList<>();
            for (JsonNode field : fields) {
                List<WorkflowTemplateDTOs.InputOption> options = new ArrayList<>();
                if (field.has("options")) {
                    for (JsonNode option : field.get("options")) {
                        options.add(new WorkflowTemplateDTOs.InputOption(
                            option.required("value").textValue(), option.required("label").textValue()));
                    }
                }
                JsonNode constraints = field.get("constraints");
                WorkflowTemplateDTOs.InputConstraints mappedConstraints = constraints == null ? null
                    : new WorkflowTemplateDTOs.InputConstraints(
                    textOrNull(constraints, "min"), textOrNull(constraints, "max"),
                    integerOrNull(constraints, "minLength"), integerOrNull(constraints, "maxLength"),
                    integerOrNull(constraints, "minItems"), integerOrNull(constraints, "maxItems"),
                    textOrNull(constraints, "assetType"), textList(constraints, "allowedExtensions"),
                    textList(constraints, "allowedContentTypes"), textOrNull(constraints, "maxBytesPerAsset"));
                result.add(new WorkflowTemplateDTOs.InputField(
                    field.required("inputKey").textValue(), textOrNull(field, "semanticKey"),
                    field.required("label").textValue(), textOrNull(field, "description"),
                    field.required("control").textValue(), field.required("valueType").textValue(),
                    field.required("required").booleanValue(), field.get("defaultValue"),
                    textOrNull(field, "placeholder"), List.copyOf(options), mappedConstraints));
            }
            return List.copyOf(result);
        } catch (Exception exception) {
            throw invalid("表单 Schema 无法读取");
        }
    }

    private WorkflowTemplateDTOs.AdminQuery normalizeAdminQuery(WorkflowTemplateDTOs.AdminQuery query) {
        if (query == null) {
            return new WorkflowTemplateDTOs.AdminQuery(null, null, null, null, null, "latest");
        }
        if (query.channel() != null && !query.channel().isBlank() && !WorkflowChannel.supports(query.channel())) {
            throw invalid("模板频道无效");
        }
        String sort = query.sort() == null || query.sort().isBlank() ? "latest" : query.sort();
        if (!ADMIN_SORTS.contains(sort)) {
            throw invalid("排序参数无效");
        }
        if (query.categoryId() != null && !query.categoryId().isBlank()) {
            parseId(query.categoryId(), "分类编号");
        }
        return new WorkflowTemplateDTOs.AdminQuery(query.channel(), query.status(), optionalText(query.keyword()),
            query.categoryId(), query.recommended(), sort);
    }

    private WorkflowTemplateDTOs.PublicQuery normalizePublicQuery(WorkflowTemplateDTOs.PublicQuery query) {
        if (query == null) {
            return new WorkflowTemplateDTOs.PublicQuery(null, null, List.of(), null, "latest");
        }
        if (query.channel() != null && !query.channel().isBlank() && !WorkflowChannel.supports(query.channel())) {
            throw invalid("模板频道无效");
        }
        String sort = query.sort() == null || query.sort().isBlank() ? "latest" : query.sort();
        if (!PUBLIC_SORTS.contains(sort)) {
            throw invalid("排序参数无效");
        }
        if (query.categoryCode() != null && !query.categoryCode().isBlank()) {
            parseId(query.categoryCode(), "分类编号");
        }
        List<String> tagCodes = query.tagCodes() == null ? List.of() : query.tagCodes().stream()
            .map(code -> Long.toString(parseId(code, "标签编号"))).distinct().sorted().toList();
        return new WorkflowTemplateDTOs.PublicQuery(query.channel(), query.categoryCode(), tagCodes,
            optionalText(query.keyword()), sort);
    }

    private String normalizeTags(String tagsJson) {
        List<String> ids = parseTagIds(tagsJson == null || tagsJson.isBlank() ? "[]" : tagsJson);
        Set<Long> available = new HashSet<>(tagMapper.selectCatalogTags().stream()
            .map(DiscoveryTag::getTagId).toList());
        for (String id : ids) {
            if (!available.contains(Long.valueOf(id))) {
                throw invalid("标签不存在: " + id);
            }
        }
        return jsonMapper.writeValueAsString(ids);
    }

    private List<String> parseTagIds(String tagsJson) {
        if (tagsJson == null || tagsJson.isBlank()) {
            return List.of();
        }
        try {
            JsonNode node = jsonMapper.readTree(tagsJson);
            if (!node.isArray()) {
                throw invalid("tagsJson 必须是数组");
            }
            LinkedHashSet<String> ids = new LinkedHashSet<>();
            for (JsonNode item : node) {
                if (!item.isTextual()) {
                    throw invalid("标签编号必须是十进制字符串");
                }
                String id = Long.toString(parseId(item.textValue(), "标签编号"));
                if (!ids.add(id)) {
                    throw invalid("标签编号重复: " + id);
                }
            }
            return List.copyOf(ids);
        } catch (ServiceException exception) {
            throw exception;
        } catch (Exception exception) {
            throw invalid("tagsJson 无效");
        }
    }

    private Map<Long, String> loadTagNames() {
        Map<Long, String> names = new HashMap<>();
        for (DiscoveryTag tag : tagMapper.selectCatalogTags()) {
            names.put(tag.getTagId(), tag.getName());
        }
        return names;
    }

    private String normalizeJsonObject(String value, String name) {
        try {
            JsonNode node = jsonMapper.readTree(requiredText(value, name));
            if (!node.isObject()) {
                throw invalid(name + "必须是 JSON 对象");
            }
            return jsonMapper.writeValueAsString(node);
        } catch (ServiceException exception) {
            throw exception;
        } catch (Exception exception) {
            throw invalid(name + "不是合法 JSON");
        }
    }

    private Page<WorkflowTemplateDTOs.TemplateRow> buildPage(PageQuery query) {
        int pageNum = query == null || query.getPageNum() == null ? 1 : Math.max(query.getPageNum(), 1);
        int pageSize = query == null || query.getPageSize() == null ? DEFAULT_PAGE_SIZE : query.getPageSize();
        if (pageSize <= 0 || pageSize > MAX_PAGE_SIZE) {
            throw invalid("分页大小无效");
        }
        return new Page<>(pageNum, pageSize);
    }

    private String normalizeSlug(String value) {
        String slug = requiredText(value, "slug").toLowerCase(Locale.ROOT);
        if (!SLUG_PATTERN.matcher(slug).matches()) {
            throw invalid("slug 格式无效");
        }
        return slug;
    }

    private Long optionalId(String value, String name) {
        return value == null || value.isBlank() ? null : parseId(value, name);
    }

    private long parseId(String value, String name) {
        try {
            long id = Long.parseLong(requiredText(value, name));
            if (id <= 0) {
                throw new NumberFormatException();
            }
            return id;
        } catch (NumberFormatException exception) {
            throw invalid(name + "无效");
        }
    }

    private String requiredText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw invalid(name + "不能为空");
        }
        return value.trim();
    }

    private String optionalText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private boolean isBlank(char[] value) {
        for (char character : value) {
            if (!Character.isWhitespace(character) && character != '\0') {
                return false;
            }
        }
        return true;
    }

    private boolean hasText(char[] value) {
        return value != null && value.length > 0 && !isBlank(value);
    }

    private boolean hasStringText(String value) {
        return value != null && !value.isBlank();
    }

    private void clear(char[] value) {
        if (value != null) {
            Arrays.fill(value, '\0');
        }
    }

    private long requireActorId(Long actorId) {
        if (actorId == null || actorId <= 0) {
            throw invalid("操作人编号无效");
        }
        return actorId;
    }

    private long requireExpectedRevision(Long value) {
        if (value == null || value < 0) {
            throw invalid("修订号无效");
        }
        return value;
    }

    private long valueOrZero(Long value) {
        return value == null ? 0L : value;
    }

    private String stringId(Long value) {
        return value == null ? null : Long.toString(value);
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }

    private String textOrNull(JsonNode node, String name) {
        JsonNode value = node.get(name);
        return value == null ? null : value.textValue();
    }

    private Integer integerOrNull(JsonNode node, String name) {
        JsonNode value = node.get(name);
        return value == null ? null : value.intValue();
    }

    private List<String> textList(JsonNode node, String name) {
        JsonNode value = node.get(name);
        return value == null ? null : value.valueStream().map(JsonNode::textValue).toList();
    }

    private String channelLabel(String channel) {
        return WorkflowChannel.WORKFLOW_INSPIRATION.getValue().equals(channel) ? "工作流灵感" : "视频模板";
    }

    private String channelDescription(String channel) {
        return WorkflowChannel.WORKFLOW_INSPIRATION.getValue().equals(channel)
            ? "发现可复用的工作流创作灵感" : "使用模板快速开始视频创作";
    }

    private void assertTemplateCas(int affected) {
        if (affected != 1) {
            throw revisionConflict("工作流模板修订冲突");
        }
    }

    private void assertConfigCas(int affected) {
        if (affected != 1) {
            throw revisionConflict("执行配置修订冲突");
        }
    }

    private void assertExactlyOne(int affected, String message) {
        if (affected != 1) {
            throw invalid(message);
        }
    }

    private ServiceException revisionConflict(String message) {
        return new ServiceException(message, WorkflowErrorCodes.WORKFLOW_REVISION_CONFLICT);
    }

    private ServiceException executionUnavailable() {
        return new ServiceException("工作流执行配置不可用",
            WorkflowErrorCodes.WORKFLOW_EXECUTION_CONFIG_UNAVAILABLE);
    }

    private ServiceException invalid(String message) {
        return new ServiceException(message, WorkflowErrorCodes.WORKFLOW_CONFIGURATION_INVALID);
    }
}
