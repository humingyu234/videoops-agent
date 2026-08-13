package org.dromara.aivideo.knowledge.service;

import org.dromara.aivideo.knowledge.dto.KnowledgeContextDTO;
import org.dromara.aivideo.knowledge.dto.KnowledgeContextRequestDTO;

/**
 * 简化知识上下文只读服务。
 */
public interface IKnowledgeContextService {

    /**
     * 解析与稳定业务代码匹配的知识上下文。
     *
     * @param request 知识上下文请求
     * @return 知识上下文
     */
    KnowledgeContextDTO resolve(KnowledgeContextRequestDTO request);
}
