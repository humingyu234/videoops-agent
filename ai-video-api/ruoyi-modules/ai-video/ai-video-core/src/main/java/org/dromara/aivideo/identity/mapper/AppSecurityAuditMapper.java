package org.dromara.aivideo.identity.mapper;

import org.dromara.aivideo.identity.domain.AppSecurityAudit;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

import java.util.List;

/**
 * 创作端安全审计数据访问接口。
 */
public interface AppSecurityAuditMapper extends BaseMapperPlus<AppSecurityAudit, AppSecurityAudit> {

    /**
     * 按请求追踪编号查询全部安全审计记录。
     *
     * @param requestId 请求追踪编号
     * @return 查询到的安全审计记录列表
     */
    default List<AppSecurityAudit> selectListByRequestId(String requestId) {
        return this.lambda()
            .eq(AppSecurityAudit::getRequestId, requestId)
            .list();
    }
}
