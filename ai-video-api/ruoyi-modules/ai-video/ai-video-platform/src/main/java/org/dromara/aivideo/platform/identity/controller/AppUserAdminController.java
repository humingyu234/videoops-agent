package org.dromara.aivideo.platform.identity.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dromara.aivideo.platform.identity.domain.bo.AppIdentityAdminBos.AppUserQueryBo;
import org.dromara.aivideo.platform.identity.domain.bo.AppIdentityAdminBos.ChangeAppUserStatusBo;
import org.dromara.aivideo.platform.identity.domain.bo.AppIdentityAdminBos.CreateAppUserBo;
import org.dromara.aivideo.platform.identity.domain.bo.AppIdentityAdminBos.KickoutAppUserBo;
import org.dromara.aivideo.platform.identity.domain.bo.AppIdentityAdminBos.ReplaceAppUserRolesBo;
import org.dromara.aivideo.platform.identity.domain.bo.AppIdentityAdminBos.ResetAppUserPasswordBo;
import org.dromara.aivideo.platform.identity.domain.bo.AppIdentityAdminBos.UpdateAppUserBo;
import org.dromara.aivideo.platform.identity.domain.vo.AppIdentityAdminVos.AppUserAdminVo;
import org.dromara.aivideo.platform.identity.domain.vo.AppIdentityAdminVos.AppUserDetailAdminVo;
import org.dromara.aivideo.platform.identity.domain.vo.AppIdentityAdminVos.AppUserInitialPasswordVo;
import org.dromara.aivideo.platform.identity.service.IAppIdentityAdminService;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.domain.R;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.web.core.BaseController;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 运营端创作用户管理入口。
 *
 * <p>所有操作均走默认 sys 权限体系；Controller 不签发或代理 app token。</p>
 */
@Validated
@RequiredArgsConstructor
@RestController
public class AppUserAdminController extends BaseController {

    private final IAppIdentityAdminService appIdentityAdminService;

    /**
     * 分页查询创作端用户。
     */
    @SaCheckPermission("aivideo:app-user:query")
    @GetMapping("/api/admin/app-users")
    public R<PageResult<AppUserAdminVo>> page(@Valid AppUserQueryBo query, PageQuery pageQuery) {
        return R.ok(appIdentityAdminService.pageUsers(query, pageQuery));
    }

    /**
     * 新建创作端用户并返回一次性初始密码。
     */
    @SaCheckPermission("aivideo:app-user:add")
    @Log(title = "创作端用户管理", businessType = BusinessType.INSERT,
        isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping("/api/admin/app-users")
    public R<AppUserInitialPasswordVo> create(@Valid @RequestBody CreateAppUserBo command) {
        return R.ok(appIdentityAdminService.createUser(command));
    }

    /**
     * 查询创作端用户详情。
     *
     * @param id 创作端用户编号（字符串）
     */
    @SaCheckPermission("aivideo:app-user:query")
    @GetMapping("/api/admin/app-users/{id}")
    public R<AppUserDetailAdminVo> get(@PathVariable String id) {
        return R.ok(appIdentityAdminService.getUser(id));
    }

    /**
     * 更新创作端用户资料。
     *
     * @param id 创作端用户编号（字符串）
    */
    @SaCheckPermission("aivideo:app-user:edit")
    @Log(title = "创作端用户管理", businessType = BusinessType.UPDATE,
        isSaveRequestData = false, isSaveResponseData = false)
    @PutMapping("/api/admin/app-users/{id}")
    public R<Void> update(@PathVariable String id, @Valid @RequestBody UpdateAppUserBo command) {
        appIdentityAdminService.updateUser(id, command);
        return R.ok();
    }

    /**
     * 变更创作端用户状态。
     *
     * @param id 创作端用户编号（字符串）
     */
    @SaCheckPermission("aivideo:app-user:edit")
    @Log(title = "创作端用户管理", businessType = BusinessType.UPDATE)
    @PostMapping("/api/admin/app-users/{id}/status-changes")
    public R<Void> changeStatus(@PathVariable String id,
                                @Valid @RequestBody ChangeAppUserStatusBo command) {
        appIdentityAdminService.changeUserStatus(id, command);
        return R.ok();
    }

    /**
     * 重置创作端用户密码并返回一次性密码。
     *
     * @param id 创作端用户编号（字符串）
     */
    @SaCheckPermission("aivideo:app-user:reset-password")
    @Log(title = "创作端用户管理", businessType = BusinessType.UPDATE,
        isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping("/api/admin/app-users/{id}/password-resets")
    public R<AppUserInitialPasswordVo> resetPassword(@PathVariable String id,
                                                     @Valid @RequestBody ResetAppUserPasswordBo command) {
        return R.ok(appIdentityAdminService.resetUserPassword(id, command));
    }

    /**
     * 强制下线创作端用户的全部会话。
     *
     * @param id 创作端用户编号（字符串）
     */
    @SaCheckPermission("aivideo:app-user:kickout")
    @Log(title = "创作端用户管理", businessType = BusinessType.FORCE)
    @PostMapping("/api/admin/app-users/{id}/kickouts")
    public R<Void> kickout(@PathVariable String id, @Valid @RequestBody KickoutAppUserBo command) {
        appIdentityAdminService.kickoutUser(id, command);
        return R.ok();
    }

    /**
     * 替换创作端用户的角色集合。
     *
     * @param id 创作端用户编号（字符串）
     */
    @SaCheckPermission("aivideo:app-user:assign-role")
    @Log(title = "创作端用户管理", businessType = BusinessType.GRANT)
    @PutMapping("/api/admin/app-users/{id}/roles")
    public R<Void> replaceRoles(@PathVariable String id,
                                @Valid @RequestBody ReplaceAppUserRolesBo command) {
        appIdentityAdminService.replaceUserRoles(id, command);
        return R.ok();
    }
}
