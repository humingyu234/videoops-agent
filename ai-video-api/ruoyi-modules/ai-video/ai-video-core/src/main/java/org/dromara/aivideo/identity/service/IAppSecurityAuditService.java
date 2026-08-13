package org.dromara.aivideo.identity.service;

import org.dromara.aivideo.identity.dto.AppSecurityAuditDTO;

/**
 * 创作端安全审计追加服务。
 */
public interface IAppSecurityAuditService {

    /**
     * 只追加一条安全审计记录。
     *
     * @param command 安全审计命令
     */
    void append(AppSecurityAuditDTO command);
}
