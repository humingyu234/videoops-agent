package org.dromara.aivideo.workflow.mapper;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Param;
import org.dromara.aivideo.workflow.domain.WorkflowTemplate;
import org.dromara.aivideo.workflow.dto.WorkflowTemplateDTOs;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

import java.util.List;

public interface WorkflowTemplateMapper extends BaseMapperPlus<WorkflowTemplate, WorkflowTemplate> {

    WorkflowTemplate selectCatalogTemplate(@Param("tenantId") long tenantId,
                                            @Param("templateId") long templateId);

    WorkflowTemplate selectCatalogTemplateForUpdate(@Param("tenantId") long tenantId,
                                                     @Param("templateId") long templateId);

    Page<WorkflowTemplateDTOs.TemplateRow> selectAdminPage(
        Page<WorkflowTemplateDTOs.TemplateRow> page,
        @Param("tenantId") long tenantId,
        @Param("query") WorkflowTemplateDTOs.AdminQuery query);

    WorkflowTemplateDTOs.TemplateRow selectAdminDetail(@Param("tenantId") long tenantId,
                                                       @Param("templateId") long templateId);

    List<WorkflowTemplateDTOs.TemplateRow> selectOptions(@Param("tenantId") long tenantId);

    Page<WorkflowTemplateDTOs.TemplateRow> selectVisiblePage(
        Page<WorkflowTemplateDTOs.TemplateRow> page,
        @Param("query") WorkflowTemplateDTOs.PublicQuery query);

    WorkflowTemplateDTOs.TemplateRow selectVisibleDetail(@Param("templateId") long templateId);

    List<WorkflowTemplateDTOs.TemplateRow> selectVisibleRecommendations(@Param("limit") int limit);

    List<WorkflowTemplateDTOs.CountRow> selectVisibleChannelCounts();

    List<WorkflowTemplateDTOs.CountRow> selectVisibleCategoryCounts();

    int updateContentCas(@Param("template") WorkflowTemplate template,
                         @Param("expectedRevision") long expectedRevision,
                         @Param("actorId") long actorId);

    int updateStatusCas(@Param("tenantId") long tenantId, @Param("templateId") long templateId,
                        @Param("expectedRevision") long expectedRevision,
                        @Param("status") String status, @Param("setEnabledAt") boolean setEnabledAt,
                        @Param("actorId") long actorId);

    int logicalDelete(@Param("tenantId") long tenantId, @Param("templateId") long templateId,
                      @Param("expectedRevision") long expectedRevision, @Param("actorId") long actorId);
}
