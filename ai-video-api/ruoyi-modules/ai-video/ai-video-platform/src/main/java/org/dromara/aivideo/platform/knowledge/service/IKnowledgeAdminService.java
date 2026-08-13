package org.dromara.aivideo.platform.knowledge.service;

import org.dromara.aivideo.platform.knowledge.domain.bo.KnowledgeItemAdminQueryBo;
import org.dromara.aivideo.platform.knowledge.domain.bo.KnowledgeItemSaveBo;
import org.dromara.aivideo.platform.knowledge.domain.vo.KnowledgeImportSummaryVo;
import org.dromara.aivideo.platform.knowledge.domain.vo.KnowledgeItemAdminVo;
import org.dromara.aivideo.platform.knowledge.domain.vo.KnowledgeItemDetailVo;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** 运营端知识库管理服务。 */
public interface IKnowledgeAdminService {

    /** 分页查询知识条目及其最新版本状态。 */
    PageResult<KnowledgeItemAdminVo> page(KnowledgeItemAdminQueryBo query, PageQuery pageQuery);

    /** 查询知识详情。 */
    KnowledgeItemDetailVo detail(Long knowledgeItemId);

    /** 新增知识。 */
    Long create(KnowledgeItemSaveBo bo, Long operatorId);

    /** 编辑知识。 */
    void update(Long knowledgeItemId, KnowledgeItemSaveBo bo, Long operatorId);

    /** 修改知识状态。 */
    void changeStatus(Long knowledgeItemId, String status, Long operatorId);

    /** 删除不存在发布或退役历史的知识及其版本和绑定。 */
    void delete(Long knowledgeItemId);

    /** 导入知识文件，并允许逐文件覆盖名称、类型和状态。 */
    KnowledgeImportSummaryVo importFiles(List<MultipartFile> files, List<String> names,
                                         List<String> knowledgeTypes, List<String> statuses, Long operatorId);

    /** 发布指定条目的最新草稿。 */
    void publish(Long knowledgeItemId, Long operatorId);
}
