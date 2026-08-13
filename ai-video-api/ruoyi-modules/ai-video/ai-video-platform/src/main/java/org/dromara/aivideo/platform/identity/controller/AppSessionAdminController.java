package org.dromara.aivideo.platform.identity.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dromara.aivideo.platform.identity.domain.bo.AppIdentityAdminBos.AppSessionQueryBo;
import org.dromara.aivideo.platform.identity.domain.bo.AppIdentityAdminBos.KickoutAppSessionBo;
import org.dromara.aivideo.platform.identity.domain.vo.AppIdentityAdminVos.AppSessionAdminVo;
import org.dromara.aivideo.platform.identity.service.IAppIdentityAdminService;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.domain.R;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.web.core.BaseController;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 运营端创作会话管理入口。
 */
@Validated
@RequiredArgsConstructor
@RestController
public class AppSessionAdminController extends BaseController {

    private final IAppIdentityAdminService appIdentityAdminService;

    /**
     * 分页查询创作端在线会话。
     */
    @SaCheckPermission("aivideo:app-session:query")
    @GetMapping("/api/admin/app-sessions")
    public R<PageResult<AppSessionAdminVo>> page(@Valid AppSessionQueryBo query, PageQuery pageQuery) {
        return R.ok(appIdentityAdminService.pageSessions(query, pageQuery));
    }

    /**
     * 强制下线指定创作端会话。
     *
     * @param id 创作端会话编号（字符串）
     */
    @SaCheckPermission("aivideo:app-session:kickout")
    @Log(title = "创作端会话管理", businessType = BusinessType.FORCE)
    @DeleteMapping("/api/admin/app-sessions/{id}")
    public R<Void> kickout(@PathVariable String id, @Valid @RequestBody KickoutAppSessionBo command) {
        appIdentityAdminService.kickoutSession(id, command);
        return R.ok();
    }
}
