package org.dromara.aivideo.knowledge.service;

import org.dromara.aivideo.knowledge.dto.KnowledgeSnapshotDTO;
import org.dromara.aivideo.knowledge.dto.KnowledgeSnapshotRequestDTO;

/**
 * 知识快照服务。
 */
public interface IKnowledgeSnapshotService {

    /**
     * 创建根任务知识快照。
     *
     * @param request 快照请求
     * @return 已创建的知识快照
     */
    KnowledgeSnapshotDTO create(KnowledgeSnapshotRequestDTO request);

    /**
     * 查询根任务对应的知识快照。
     *
     * @param rootTaskId 根任务编号
     * @return 知识快照
     */
    KnowledgeSnapshotDTO getByRootTaskId(Long rootTaskId);
}
