package org.dromara.aivideo.platform.knowledge.domain.vo;

import java.util.List;

/** 知识文件导入汇总。 */
public record KnowledgeImportSummaryVo(
    int totalCount,
    int successCount,
    int skippedCount,
    int failedCount,
    List<KnowledgeImportFileVo> files
) {

    /** 单个文件的导入结果。 */
    public record KnowledgeImportFileVo(
        String sourcePath,
        String fileName,
        String status,
        String message,
        Long knowledgeItemId,
        Long knowledgeVersionId,
        String stableCode
    ) {
    }
}
