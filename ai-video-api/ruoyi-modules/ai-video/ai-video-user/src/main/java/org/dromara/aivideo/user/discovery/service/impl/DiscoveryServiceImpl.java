package org.dromara.aivideo.user.discovery.service.impl;

import lombok.RequiredArgsConstructor;
import org.dromara.aivideo.user.discovery.domain.bo.DiscoveryTemplateQueryBo;
import org.dromara.aivideo.user.discovery.domain.vo.DiscoveryHomeVo;
import org.dromara.aivideo.user.discovery.domain.vo.WorkflowCreationConfigVo;
import org.dromara.aivideo.user.discovery.domain.vo.WorkflowTemplateCardVo;
import org.dromara.aivideo.user.discovery.domain.vo.WorkflowTemplateDetailVo;
import org.dromara.aivideo.user.discovery.service.IDiscoveryService;
import org.dromara.aivideo.workflow.dto.WorkflowTemplateDTOs;
import org.dromara.aivideo.workflow.service.IWorkflowTemplateService;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** 用户端发现页查询服务实现。 */
@RequiredArgsConstructor
@Service
public class DiscoveryServiceImpl implements IDiscoveryService {

    private final IWorkflowTemplateService workflowTemplateService;

    @Override
    public DiscoveryHomeVo queryHome() {
        return DiscoveryHomeVo.from(workflowTemplateService.queryDiscoveryHome());
    }

    @Override
    public PageResult<WorkflowTemplateCardVo> queryTemplates(DiscoveryTemplateQueryBo query) {
        Objects.requireNonNull(query, "query");
        WorkflowTemplateDTOs.PublicQuery publicQuery = new WorkflowTemplateDTOs.PublicQuery(
            query.getChannel(), query.getCategoryCode(), splitTagCodes(query.getTagCodes()),
            query.getKeyword(), query.getSort());
        PageResult<WorkflowTemplateDTOs.PublicCard> source = workflowTemplateService.queryVisiblePage(
            publicQuery, new PageQuery(query.getPageSize(), query.getPageNum()));
        List<WorkflowTemplateCardVo> rows = source.getRows().stream()
            .map(WorkflowTemplateCardVo::from)
            .toList();
        return PageResult.build(rows, source.getTotal());
    }

    @Override
    public WorkflowTemplateDetailVo queryTemplate(String templateId) {
        return WorkflowTemplateDetailVo.from(workflowTemplateService.queryVisibleDetail(templateId));
    }

    @Override
    public WorkflowCreationConfigVo queryCreationConfig(String templateId) {
        return WorkflowCreationConfigVo.from(workflowTemplateService.queryCreationConfig(templateId));
    }

    private List<String> splitTagCodes(String tagCodes) {
        if (tagCodes == null || tagCodes.isBlank()) {
            return List.of();
        }
        return Arrays.stream(tagCodes.split(","))
            .map(String::trim)
            .filter(code -> !code.isEmpty())
            .toList();
    }
}
