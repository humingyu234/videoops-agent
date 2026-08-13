package org.dromara.aivideo.platform.identity.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dromara.aivideo.platform.identity.domain.bo.AppIdentityAdminBos.AppLoginLogQueryBo;
import org.dromara.aivideo.platform.identity.domain.bo.AppIdentityAdminBos.AppSecurityAuditQueryBo;
import org.dromara.aivideo.platform.identity.domain.vo.AppIdentityAdminVos.AppLoginLogAdminVo;
import org.dromara.aivideo.platform.identity.domain.vo.AppIdentityAdminVos.AppSecurityAuditAdminVo;
import org.dromara.aivideo.platform.identity.service.IAppIdentityAdminService;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.domain.R;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.web.core.BaseController;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 运营端创作身份安全日志查询入口。
 */
@Validated
@RequiredArgsConstructor
@RestController
public class AppSecurityLogAdminController extends BaseController {

    private final IAppIdentityAdminService appIdentityAdminService;

    /**
     * 分页查询创作端登录日志。
     */
    @SaCheckPermission("aivideo:app-login-log:query")
    @GetMapping("/api/admin/app-login-logs")
    public R<PageResult<AppLoginLogAdminVo>> pageLoginLogs(@Valid AppLoginLogQueryBo query, PageQuery pageQuery) {
        return R.ok(appIdentityAdminService.pageLoginLogs(query, pageQuery));
    }

    /**
     * 分页查询创作端安全审计日志。
     */
    @SaCheckPermission("aivideo:app-security-audit:query")
    @GetMapping("/api/admin/app-security-audits")
    public R<PageResult<AppSecurityAuditAdminVo>> pageSecurityAudits(@Valid AppSecurityAuditQueryBo query,
                                                                      PageQuery pageQuery) {
        return R.ok(appIdentityAdminService.pageSecurityAudits(query, pageQuery));
    }
}
