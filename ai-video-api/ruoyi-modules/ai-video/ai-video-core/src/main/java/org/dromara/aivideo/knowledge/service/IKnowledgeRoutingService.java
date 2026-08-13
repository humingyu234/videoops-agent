package org.dromara.aivideo.knowledge.service;

import org.dromara.aivideo.knowledge.dto.KnowledgeRouteRequestDTO;
import org.dromara.aivideo.knowledge.dto.KnowledgeRouteResultDTO;

/**
 * 知识路由服务。
 */
public interface IKnowledgeRoutingService {

    /**
     * 根据稳定业务代码生成确定性知识路由结果。
     *
     * @param request 知识路由请求
     * @return 知识路由结果
     */
    KnowledgeRouteResultDTO route(KnowledgeRouteRequestDTO request);
}
