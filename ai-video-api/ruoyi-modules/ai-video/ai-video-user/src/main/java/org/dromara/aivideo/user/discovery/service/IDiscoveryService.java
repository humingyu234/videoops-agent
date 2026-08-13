package org.dromara.aivideo.user.discovery.service;

import org.dromara.aivideo.user.discovery.domain.bo.DiscoveryTemplateQueryBo;
import org.dromara.aivideo.user.discovery.domain.vo.DiscoveryHomeVo;
import org.dromara.aivideo.user.discovery.domain.vo.WorkflowCreationConfigVo;
import org.dromara.aivideo.user.discovery.domain.vo.WorkflowTemplateCardVo;
import org.dromara.aivideo.user.discovery.domain.vo.WorkflowTemplateDetailVo;
import org.dromara.common.core.domain.PageResult;

/** 用户端发现页查询服务。 */
public interface IDiscoveryService {

    DiscoveryHomeVo queryHome();

    PageResult<WorkflowTemplateCardVo> queryTemplates(DiscoveryTemplateQueryBo query);

    WorkflowTemplateDetailVo queryTemplate(String templateId);

    WorkflowCreationConfigVo queryCreationConfig(String templateId);
}
