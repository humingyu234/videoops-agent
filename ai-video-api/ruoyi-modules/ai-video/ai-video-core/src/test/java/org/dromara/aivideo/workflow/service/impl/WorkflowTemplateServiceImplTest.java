package org.dromara.aivideo.workflow.service.impl;

import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.dromara.aivideo.workflow.constant.WorkflowErrorCodes;
import org.dromara.aivideo.workflow.domain.DiscoveryCategory;
import org.dromara.aivideo.workflow.domain.RunningHubAccount;
import org.dromara.aivideo.workflow.domain.WorkflowExecutionConfig;
import org.dromara.aivideo.workflow.domain.WorkflowTemplate;
import org.dromara.aivideo.workflow.dto.WorkflowTemplateDTOs;
import org.dromara.aivideo.workflow.enums.WorkflowCredentialPurpose;
import org.dromara.aivideo.workflow.mapper.DiscoveryCategoryMapper;
import org.dromara.aivideo.workflow.mapper.DiscoveryTagMapper;
import org.dromara.aivideo.workflow.mapper.RunningHubAccountMapper;
import org.dromara.aivideo.workflow.mapper.WorkflowExecutionConfigMapper;
import org.dromara.aivideo.workflow.mapper.WorkflowTemplateMapper;
import org.dromara.aivideo.workflow.service.IWorkflowCredentialWriteService;
import org.dromara.aivideo.workflow.validation.WorkflowSchemaCanonicalizer;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.system.domain.vo.SysOssVo;
import org.dromara.system.service.ISysOssService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("dev")
class WorkflowTemplateServiceImplTest {

    private static final long ACTOR_ID = 41L;
    private static final String FORM_SCHEMA = """
        {"schemaVersion":"workflow-form-1","fields":[{
          "inputKey":"prompt","label":"Prompt","control":"text","valueType":"string","required":true
        }]}
        """;

    @Test
    void createPersistsCatalogDraftAndActorAudit() {
        Harness harness = harness();
        when(harness.templateMapper.insert(any(WorkflowTemplate.class))).thenReturn(1);

        String templateId = harness.service.create(ACTOR_ID, save(null));

        ArgumentCaptor<WorkflowTemplate> inserted = ArgumentCaptor.forClass(WorkflowTemplate.class);
        verify(harness.templateMapper).insert(inserted.capture());
        assertThat(templateId).isEqualTo(inserted.getValue().getTemplateId().toString());
        assertThat(inserted.getValue().getSlug()).isEqualTo("workflow-" + templateId);
        assertThat(inserted.getValue().getTenantId()).isZero();
        assertThat(inserted.getValue().getStatus()).isEqualTo("draft");
        assertThat(inserted.getValue().getCreateBy()).isEqualTo(ACTOR_ID);
        assertThat(inserted.getValue().getUpdateBy()).isEqualTo(ACTOR_ID);
        assertThat(inserted.getValue().getRowRevision()).isZero();
    }

    @Test
    void createAndUpdateRejectMissingOrInactiveCategory() {
        Harness missing = harness();
        when(missing.templateMapper.insert(any(WorkflowTemplate.class))).thenReturn(1);
        assertThatThrownBy(() -> missing.service.create(ACTOR_ID, save(null, null)))
            .isInstanceOf(ServiceException.class);

        Harness inactive = harness();
        when(inactive.categoryMapper.selectActiveById(999L)).thenReturn(null);
        when(inactive.templateMapper.insert(any(WorkflowTemplate.class))).thenReturn(1);
        assertThatThrownBy(() -> inactive.service.create(ACTOR_ID, save(null, "999")))
            .isInstanceOf(ServiceException.class);

        Harness update = harness();
        when(update.templateMapper.selectCatalogTemplate(0L, 101L))
            .thenReturn(template(101L, "draft", 4L));
        when(update.categoryMapper.selectActiveById(999L)).thenReturn(null);
        assertThatThrownBy(() -> update.service.update(ACTOR_ID, "101", save(4L, "999")))
            .isInstanceOf(ServiceException.class);
        verify(update.templateMapper, never()).updateContentCas(any(), any(Long.class), any(Long.class));
    }

    @Test
    void duplicateTemplateReferenceMapsToStableConflictCode() {
        Harness harness = harness();
        when(harness.templateMapper.insert(any(WorkflowTemplate.class)))
            .thenThrow(new DuplicateKeyException("duplicate"));

        assertThatThrownBy(() -> harness.service.create(ACTOR_ID, save(null)))
            .isInstanceOfSatisfying(ServiceException.class,
                exception -> assertThat(exception.getCode()).isEqualTo(WorkflowErrorCodes.WORKFLOW_REFERENCE_CONFLICT));
    }

    @Test
    void publicCardUsesTheSystemResolvedCoverUrl() {
        Harness harness = harness();
        WorkflowTemplateDTOs.TemplateRow row = new WorkflowTemplateDTOs.TemplateRow();
        row.setTemplateId(101L);
        row.setName("Template");
        row.setChannel("video_template");
        row.setCategoryId(501L);
        row.setCategoryName("Video");
        row.setTagsJson("[]");
        row.setCoverAssetId(7001L);
        row.setCoverUrl("https://cdn.example.test/template-cover.png");
        SysOssVo cover = new SysOssVo();
        cover.setOssId(7001L);
        cover.setUrl("https://cdn.example.test/template-cover.png?signature=resolved");
        Page<WorkflowTemplateDTOs.TemplateRow> page = new Page<>(1, 20);
        page.setRecords(List.of(row));
        page.setTotal(1);
        when(harness.templateMapper.selectVisiblePage(any(), any())).thenReturn(page);
        when(harness.ossService.listByIds(List.of(7001L))).thenReturn(List.of(cover));

        var result = harness.service.queryVisiblePage(
            new WorkflowTemplateDTOs.PublicQuery(null, null, List.of(), null, null), new PageQuery(20, 1));

        assertThat(result.getRows()).singleElement().extracting(card -> card.cover().url())
            .isEqualTo("https://cdn.example.test/template-cover.png?signature=resolved");
    }

    @Test
    void updatingTemplatePreservesEnabledStatusAndUsesActorRevisionCas() {
        Harness harness = harness();
        when(harness.templateMapper.selectCatalogTemplate(0L, 101L))
            .thenReturn(template(101L, "enabled", 4L));
        when(harness.templateMapper.updateContentCas(any(), eq(4L), eq(ACTOR_ID))).thenReturn(1);

        harness.service.update(ACTOR_ID, "101", save(4L));

        ArgumentCaptor<WorkflowTemplate> update = ArgumentCaptor.forClass(WorkflowTemplate.class);
        verify(harness.templateMapper).updateContentCas(update.capture(), eq(4L), eq(ACTOR_ID));
        assertThat(update.getValue().getStatus()).isEqualTo("enabled");
        assertThat(update.getValue().getSlug()).isEqualTo("existing-slug");
        assertThat(update.getValue().getUpdateBy()).isEqualTo(ACTOR_ID);
        assertThat(update.getValue().getSchemaHash()).matches("sha256:[0-9a-f]{64}");
    }

    @Test
    void allTemplateWritesRejectMissingOrNonPositiveActor() {
        Harness harness = harness();
        assertInvalidActor(() -> harness.service.create(null, save(null)));
        assertInvalidActor(() -> harness.service.update(0L, "101", save(1L)));
        assertInvalidActor(() -> harness.service.delete(-1L, "101", 1L));
        assertInvalidActor(() -> harness.service.saveExecutionConfig(null, "101", configSave(null, false, 0L)));
        assertInvalidActor(() -> harness.service.enable(0L, "101", 1L));
        assertInvalidActor(() -> harness.service.disable(-1L, "101", 1L));
    }

    @Test
    void failedOrNeverTestStatusDoesNotBlockManualEnable() {
        Harness harness = harness();
        when(harness.templateMapper.selectCatalogTemplate(0L, 101L)).thenReturn(template(101L, "draft", 4L));
        when(harness.executionConfigMapper.selectCurrent(0L, 101L))
            .thenReturn(config(101L, 201L, "failed", true, 3L));
        when(harness.accountMapper.selectCatalogAccount(0L, 201L)).thenReturn(account(201L, true, 8L));
        when(harness.templateMapper.updateStatusCas(0L, 101L, 4L, "enabled", true, ACTOR_ID)).thenReturn(1);

        harness.service.enable(ACTOR_ID, "101", 4L);

        verify(harness.templateMapper).updateStatusCas(0L, 101L, 4L, "enabled", true, ACTOR_ID);
    }

    @Test
    void enableRejectsTemplateWhoseCategoryIsNoLongerActive() {
        Harness harness = harness();
        when(harness.templateMapper.selectCatalogTemplate(0L, 101L)).thenReturn(template(101L, "draft", 4L));
        when(harness.categoryMapper.selectActiveById(501L)).thenReturn(null);

        assertThatThrownBy(() -> harness.service.enable(ACTOR_ID, "101", 4L))
            .isInstanceOf(ServiceException.class);
        verify(harness.templateMapper, never()).updateStatusCas(any(Long.class), any(Long.class),
            any(Long.class), any(), any(Boolean.class), any(Long.class));
    }

    @Test
    void staleTemplateRevisionCannotEnable() {
        Harness harness = harness();
        when(harness.templateMapper.selectCatalogTemplate(0L, 101L)).thenReturn(template(101L, "draft", 4L));
        when(harness.executionConfigMapper.selectCurrent(0L, 101L))
            .thenReturn(config(101L, 201L, "never", true, 3L));
        when(harness.accountMapper.selectCatalogAccount(0L, 201L)).thenReturn(account(201L, true, 8L));
        when(harness.templateMapper.updateStatusCas(0L, 101L, 4L, "enabled", true, ACTOR_ID)).thenReturn(0);

        assertRevisionConflict(() -> harness.service.enable(ACTOR_ID, "101", 4L));
    }

    @Test
    void executionModesAreExclusiveAndCurrentConfigIsUpdatedByActorCas() {
        Harness harness = harness();
        when(harness.templateMapper.selectCatalogTemplateForUpdate(0L, 101L))
            .thenReturn(template(101L, "enabled", 4L));
        when(harness.accountMapper.selectCatalogAccountForUpdate(0L, 201L)).thenReturn(account(201L, false, 8L));
        when(harness.executionConfigMapper.selectCurrent(0L, 101L))
            .thenReturn(config(101L, 201L, "never", true, 3L));
        when(harness.executionConfigMapper.updateCurrentCas(any(), eq(3L), eq(ACTOR_ID))).thenReturn(1);

        assertThatThrownBy(() -> harness.service.saveExecutionConfig(ACTOR_ID, "101",
            new WorkflowTemplateDTOs.ExecutionConfigSave("runninghub_ai_app", "201", "wf-1", "app-1",
                null, "{}", "{}", 1800, true, false, null, 3L)))
            .isInstanceOf(ServiceException.class);

        harness.service.saveExecutionConfig(ACTOR_ID, "101",
            new WorkflowTemplateDTOs.ExecutionConfigSave("runninghub_ai_app", "201", null, "app-1",
                "plus", "{}", "{}", 1800, true, false, null, 3L));

        harness.service.saveExecutionConfig(ACTOR_ID, "101", configSave(null, false, 3L));

        ArgumentCaptor<WorkflowExecutionConfig> update = ArgumentCaptor.forClass(WorkflowExecutionConfig.class);
        verify(harness.executionConfigMapper, times(2)).updateCurrentCas(update.capture(), eq(3L), eq(ACTOR_ID));
        assertThat(update.getValue().getWorkflowId()).isNull();
        assertThat(update.getValue().getWebappId()).isEqualTo("app-1");
        assertThat(update.getValue().getUpdateBy()).isEqualTo(ACTOR_ID);
    }

    @Test
    void firstConfigInsertRequiresZeroRevisionLocksAccountAndWritesActorAudit() {
        Harness harness = harness();
        when(harness.accountMapper.selectCatalogAccountForUpdate(0L, 201L)).thenReturn(account(201L, false, 8L));
        when(harness.templateMapper.selectCatalogTemplateForUpdate(0L, 101L))
            .thenReturn(template(101L, "draft", 4L));
        when(harness.executionConfigMapper.insert(any(WorkflowExecutionConfig.class))).thenAnswer(invocation -> {
            WorkflowExecutionConfig config = invocation.getArgument(0);
            config.setExecutionConfigId(301L);
            return 1;
        });

        harness.service.saveExecutionConfig(ACTOR_ID, "101", configSave(null, false, 0L));

        InOrder order = inOrder(harness.templateMapper, harness.accountMapper, harness.executionConfigMapper);
        order.verify(harness.templateMapper).selectCatalogTemplateForUpdate(0L, 101L);
        order.verify(harness.accountMapper).selectCatalogAccountForUpdate(0L, 201L);
        order.verify(harness.executionConfigMapper).selectCurrent(0L, 101L);
        ArgumentCaptor<WorkflowExecutionConfig> inserted = ArgumentCaptor.forClass(WorkflowExecutionConfig.class);
        order.verify(harness.executionConfigMapper).insert(inserted.capture());
        assertThat(inserted.getValue().getTenantId()).isZero();
        assertThat(inserted.getValue().getCreateBy()).isEqualTo(ACTOR_ID);
        assertThat(inserted.getValue().getUpdateBy()).isEqualTo(ACTOR_ID);
        assertThat(inserted.getValue().getRowRevision()).isEqualTo(1L);
    }

    @Test
    void firstConfigInsertRejectsNonZeroRevisionAndDuplicateCurrentRow() {
        Harness stale = harness();
        when(stale.accountMapper.selectCatalogAccountForUpdate(0L, 201L)).thenReturn(account(201L, false, 8L));
        when(stale.templateMapper.selectCatalogTemplateForUpdate(0L, 101L))
            .thenReturn(template(101L, "draft", 4L));
        assertRevisionConflict(() -> stale.service.saveExecutionConfig(
            ACTOR_ID, "101", configSave(null, false, 2L)));
        verify(stale.executionConfigMapper, never()).insert(any(WorkflowExecutionConfig.class));

        Harness duplicate = harness();
        when(duplicate.accountMapper.selectCatalogAccountForUpdate(0L, 201L)).thenReturn(account(201L, false, 8L));
        when(duplicate.templateMapper.selectCatalogTemplateForUpdate(0L, 101L))
            .thenReturn(template(101L, "draft", 4L));
        when(duplicate.executionConfigMapper.insert(any(WorkflowExecutionConfig.class)))
            .thenThrow(new DuplicateKeyException("duplicate"));
        assertThatThrownBy(() -> duplicate.service.saveExecutionConfig(
            ACTOR_ID, "101", configSave(null, false, 0L)))
            .isInstanceOfSatisfying(ServiceException.class,
                exception -> assertThat(exception.getCode()).isEqualTo(WorkflowErrorCodes.WORKFLOW_REFERENCE_CONFLICT));
    }

    @Test
    void staleExecutionConfigRevisionIsRejected() {
        Harness harness = updateConfigHarness();
        when(harness.executionConfigMapper.updateCurrentCas(any(), eq(3L), eq(ACTOR_ID))).thenReturn(0);

        assertRevisionConflict(() -> harness.service.saveExecutionConfig(
            ACTOR_ID, "101", configSave(null, false, 3L)));
    }

    @Test
    void secondCreateTokenCannotOverwriteExistingLegacyRevisionZeroConfig() {
        Harness harness = harness();
        WorkflowExecutionConfig current = config(101L, 201L, "never", true, 0L);
        when(harness.templateMapper.selectCatalogTemplateForUpdate(0L, 101L))
            .thenReturn(template(101L, "draft", 4L));
        when(harness.accountMapper.selectCatalogAccountForUpdate(0L, 201L)).thenReturn(account(201L, false, 8L));
        when(harness.executionConfigMapper.selectCurrent(0L, 101L)).thenReturn(current);

        assertRevisionConflict(() -> harness.service.saveExecutionConfig(
            ACTOR_ID, "101", configSave(null, false, 0L)));
        verify(harness.executionConfigMapper, never()).updateCurrentCas(any(), any(Long.class), any(Long.class));
    }

    @Test
    void accessPasswordCanBeClearedOrReplacedAndEveryInputArrayIsZeroed() {
        Harness clear = updateConfigHarness();
        when(clear.executionConfigMapper.updateCurrentCas(any(), eq(3L), eq(ACTOR_ID))).thenReturn(1);
        clear.service.saveExecutionConfig(ACTOR_ID, "101", configSave(null, true, 3L));
        ArgumentCaptor<WorkflowExecutionConfig> cleared = ArgumentCaptor.forClass(WorkflowExecutionConfig.class);
        verify(clear.executionConfigMapper).updateCurrentCas(cleared.capture(), eq(3L), eq(ACTOR_ID));
        assertThat(cleared.getValue().getAccessPasswordCiphertext()).isNull();

        Harness replace = updateConfigHarness();
        when(replace.executionConfigMapper.updateCurrentCas(any(), eq(3L), eq(ACTOR_ID))).thenReturn(1);
        when(replace.credentialWriteService.encryptForStorage(
            eq(WorkflowCredentialPurpose.RUNNINGHUB_ACCESS_PASSWORD), any())).thenReturn("v1:new");
        char[] password = "new-password".toCharArray();
        replace.service.saveExecutionConfig(ACTOR_ID, "101", configSave(password, false, 3L));
        ArgumentCaptor<WorkflowExecutionConfig> replaced = ArgumentCaptor.forClass(WorkflowExecutionConfig.class);
        verify(replace.executionConfigMapper).updateCurrentCas(replaced.capture(), eq(3L), eq(ACTOR_ID));
        verify(replace.credentialWriteService).encryptForStorage(
            eq(WorkflowCredentialPurpose.RUNNINGHUB_ACCESS_PASSWORD), any());
        assertThat(replaced.getValue().getAccessPasswordCiphertext()).isEqualTo("v1:new");
        assertThat(password).containsOnly('\0');

        Harness conflict = harness();
        char[] conflictingPassword = "must-clear".toCharArray();
        assertThatThrownBy(() -> conflict.service.saveExecutionConfig(ACTOR_ID, "101",
            configSave(conflictingPassword, true, 0L)))
            .isInstanceOf(ServiceException.class);
        assertThat(conflictingPassword).containsOnly('\0');
    }

    @Test
    void templateDeleteUsesActorRevisionCasAfterConfigLogicalDeleteInOneTransaction() throws Exception {
        Harness harness = harness();
        when(harness.templateMapper.selectCatalogTemplateForUpdate(0L, 101L))
            .thenReturn(template(101L, "draft", 4L));
        when(harness.executionConfigMapper.logicalDeleteCurrent(0L, 101L, ACTOR_ID)).thenReturn(1);
        when(harness.templateMapper.logicalDelete(0L, 101L, 4L, ACTOR_ID)).thenReturn(1);

        harness.service.delete(ACTOR_ID, "101", 4L);

        InOrder order = inOrder(harness.templateMapper, harness.executionConfigMapper);
        order.verify(harness.templateMapper).selectCatalogTemplateForUpdate(0L, 101L);
        order.verify(harness.executionConfigMapper).logicalDeleteCurrent(0L, 101L, ACTOR_ID);
        order.verify(harness.templateMapper).logicalDelete(0L, 101L, 4L, ACTOR_ID);
        assertThat(WorkflowTemplateServiceImpl.class
            .getMethod("delete", Long.class, String.class, long.class)
            .isAnnotationPresent(Transactional.class)).isTrue();
    }

    @Test
    void staleTemplateDeleteRevisionIsRejected() {
        Harness harness = harness();
        when(harness.templateMapper.selectCatalogTemplateForUpdate(0L, 101L))
            .thenReturn(template(101L, "draft", 4L));
        when(harness.templateMapper.logicalDelete(0L, 101L, 4L, ACTOR_ID)).thenReturn(0);
        assertRevisionConflict(() -> harness.service.delete(ACTOR_ID, "101", 4L));
    }

    @Test
    void publicDtoShapeContainsNoExecutionOrProviderDetails() {
        assertThat(WorkflowTemplateDTOs.PublicCard.class.getRecordComponents())
            .extracting(component -> component.getName().toLowerCase())
            .noneMatch(name -> name.contains("provider") || name.contains("mode") || name.contains("account")
                || name.contains("remote") || name.contains("node") || name.contains("task")
                || name.contains("templateversion") || name.contains("plan"));
        assertThat(WorkflowTemplateDTOs.CreationConfig.class.getRecordComponents())
            .extracting(component -> component.getName().toLowerCase())
            .noneMatch(name -> name.contains("provider") || name.contains("mode") || name.contains("account")
                || name.contains("remote") || name.contains("node") || name.contains("task")
                || name.contains("templateversion") || name.contains("plan"));
    }

    @Test
    void mapperSqlUsesHardVisibilityAndActorCasGates() throws Exception {
        try (var input = Objects.requireNonNull(getClass().getClassLoader()
            .getResourceAsStream("mapper/workflow/WorkflowTemplateMapper.xml"))) {
            String xml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(xml)
                .contains("<sql id=\"publicTemplateColumns\">")
                .contains("t.tenant_id = 0")
                .contains("t.del_flag = '0'")
                .contains("t.status = 'enabled'")
                .contains("c.del_flag = '0'")
                .contains("c.enabled = 1")
                .contains("a.del_flag = '0'")
                .contains("a.enabled = 1")
                .contains("<select id=\"selectCatalogTemplateForUpdate\"")
                .contains("FOR UPDATE")
                .contains("INNER JOIN sys_dict_data cat")
                .contains("cat.dict_type = 'aivideo_discovery_category'")
                .contains("update_by = #{actorId}")
                .contains("row_revision = #{expectedRevision}");

            int columnsStart = xml.indexOf("<sql id=\"publicTemplateColumns\">");
            String publicColumns = xml.substring(columnsStart, xml.indexOf("</sql>", columnsStart));
            assertThat(publicColumns)
                .doesNotContain("last_test", "runninghub_account_id", "execution_mode", "workflow_id", "webapp_id");
        }

        String configUpdateSql = String.join(" ", WorkflowExecutionConfigMapper.class
            .getMethod("updateCurrentCas", WorkflowExecutionConfig.class, long.class, long.class)
            .getAnnotation(Update.class).value());
        String configDeleteSql = String.join(" ", WorkflowExecutionConfigMapper.class
            .getMethod("logicalDeleteCurrent", long.class, long.class, long.class)
            .getAnnotation(Update.class).value());
        assertThat(configUpdateSql).contains("update_by = #{actorId}", "row_revision = #{expectedRevision}");
        assertThat(configDeleteSql).contains("update_by = #{actorId}");

        String categorySql = String.join(" ", DiscoveryCategoryMapper.class
            .getMethod("selectActiveById", long.class)
            .getAnnotation(Select.class).value());
        assertThat(categorySql)
            .contains("FROM sys_dict_data")
            .contains("dict_type = 'aivideo_discovery_category'");
    }

    private static Harness harness() {
        WorkflowTemplateMapper templateMapper = mock(WorkflowTemplateMapper.class);
        WorkflowExecutionConfigMapper configMapper = mock(WorkflowExecutionConfigMapper.class);
        RunningHubAccountMapper accountMapper = mock(RunningHubAccountMapper.class);
        DiscoveryCategoryMapper categoryMapper = mock(DiscoveryCategoryMapper.class);
        when(categoryMapper.selectActiveById(501L)).thenReturn(category(501L));
        DiscoveryTagMapper tagMapper = mock(DiscoveryTagMapper.class);
        when(tagMapper.selectCatalogTags()).thenReturn(List.of());
        IWorkflowCredentialWriteService credentialWriteService = mock(IWorkflowCredentialWriteService.class);
        ISysOssService ossService = mock(ISysOssService.class);
        JsonMapper jsonMapper = JsonMapper.builder().build();
        WorkflowTemplateServiceImpl service = new WorkflowTemplateServiceImpl(
            templateMapper, configMapper, accountMapper, categoryMapper, tagMapper,
            credentialWriteService, ossService, new WorkflowSchemaCanonicalizer(jsonMapper), jsonMapper);
        return new Harness(service, templateMapper, configMapper, accountMapper, categoryMapper,
            credentialWriteService, ossService);
    }

    private static Harness updateConfigHarness() {
        Harness harness = harness();
        WorkflowExecutionConfig current = config(101L, 201L, "never", true, 3L);
        current.setAccessPasswordCiphertext("v1:old");
        when(harness.accountMapper.selectCatalogAccountForUpdate(0L, 201L)).thenReturn(account(201L, false, 8L));
        when(harness.templateMapper.selectCatalogTemplateForUpdate(0L, 101L))
            .thenReturn(template(101L, "draft", 4L));
        when(harness.executionConfigMapper.selectCurrent(0L, 101L)).thenReturn(current);
        return harness;
    }

    private static WorkflowTemplateDTOs.Save save(Long expectedRevision) {
        return save(expectedRevision, "501");
    }

    private static WorkflowTemplateDTOs.Save save(Long expectedRevision, String categoryId) {
        return new WorkflowTemplateDTOs.Save(
            "video_template", "Template", "template", "Summary", "Description", null, categoryId,
            "[]", FORM_SCHEMA, true, 10, 60, expectedRevision);
    }

    private static WorkflowTemplateDTOs.ExecutionConfigSave configSave(
        char[] password, boolean clearPassword, long expectedRevision) {
        return new WorkflowTemplateDTOs.ExecutionConfigSave(
            "runninghub_ai_app", "201", null, "app-1", null, "{}", "{}", 1800,
            true, clearPassword, password, expectedRevision);
    }

    private static WorkflowTemplate template(long id, String status, long revision) {
        WorkflowTemplate template = new WorkflowTemplate();
        template.setTemplateId(id);
        template.setTenantId(0L);
        template.setName("Template");
        template.setSlug("existing-slug");
        template.setCategoryId(501L);
        template.setStatus(status);
        template.setRowRevision(revision);
        template.setDelFlag("0");
        return template;
    }

    private static WorkflowExecutionConfig config(long templateId, long accountId, String testStatus,
                                                    boolean enabled, long revision) {
        WorkflowExecutionConfig config = new WorkflowExecutionConfig();
        config.setExecutionConfigId(301L);
        config.setTenantId(0L);
        config.setTemplateId(templateId);
        config.setRunninghubAccountId(accountId);
        config.setExecutionMode("runninghub_workflow");
        config.setWorkflowId("wf-1");
        config.setInputMappingJson("{}");
        config.setOutputPolicyJson("{}");
        config.setLastTestStatus(testStatus);
        config.setEnabled(enabled);
        config.setRowRevision(revision);
        config.setDelFlag("0");
        return config;
    }

    private static RunningHubAccount account(long id, boolean enabled, long revision) {
        RunningHubAccount account = new RunningHubAccount();
        account.setAccountId(id);
        account.setTenantId(0L);
        account.setEnabled(enabled);
        account.setRowRevision(revision);
        account.setDelFlag("0");
        return account;
    }

    private static DiscoveryCategory category(long id) {
        DiscoveryCategory category = new DiscoveryCategory();
        category.setCategoryId(id);
        category.setStatus("active");
        return category;
    }

    private static void assertInvalidActor(Runnable action) {
        assertThatThrownBy(action::run)
            .isInstanceOfSatisfying(ServiceException.class,
                exception -> assertThat(exception.getCode()).isEqualTo(WorkflowErrorCodes.WORKFLOW_CONFIGURATION_INVALID));
    }

    private static void assertRevisionConflict(Runnable action) {
        assertThatThrownBy(action::run)
            .isInstanceOfSatisfying(ServiceException.class,
                exception -> assertThat(exception.getCode()).isEqualTo(WorkflowErrorCodes.WORKFLOW_REVISION_CONFLICT));
    }

    private record Harness(WorkflowTemplateServiceImpl service, WorkflowTemplateMapper templateMapper,
                           WorkflowExecutionConfigMapper executionConfigMapper,
                           RunningHubAccountMapper accountMapper,
                           DiscoveryCategoryMapper categoryMapper,
                           IWorkflowCredentialWriteService credentialWriteService,
                           ISysOssService ossService) {
    }
}
