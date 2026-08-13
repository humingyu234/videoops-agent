package org.dromara.aivideo.platform.identity.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dromara.aivideo.platform.identity.domain.bo.AppIdentityAdminBos.AppRoleQueryBo;
import org.dromara.aivideo.platform.identity.domain.bo.AppIdentityAdminBos.CreateAppRoleBo;
import org.dromara.aivideo.platform.identity.domain.bo.AppIdentityAdminBos.ReplaceAppRolePermissionsBo;
import org.dromara.aivideo.platform.identity.domain.bo.AppIdentityAdminBos.UpdateAppRoleBo;
import org.dromara.aivideo.platform.identity.domain.vo.AppIdentityAdminVos.AppPermissionAdminVo;
import org.dromara.aivideo.platform.identity.domain.vo.AppIdentityAdminVos.AppRoleAdminVo;
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

import java.util.List;

/**
 * 运营端创作角色与权限注册表管理入口。
 */
@Validated
@RequiredArgsConstructor
@RestController
public class AppRoleAdminController extends BaseController {

    private final IAppIdentityAdminService appIdentityAdminService;

    /**
     * 分页查询创作端角色。
     */
    @SaCheckPermission("aivideo:app-role:query")
    @GetMapping("/api/admin/app-roles")
    public R<PageResult<AppRoleAdminVo>> page(@Valid AppRoleQueryBo query, PageQuery pageQuery) {
        return R.ok(appIdentityAdminService.pageRoles(query, pageQuery));
    }

    /**
     * 新建创作端角色。
     */
    @SaCheckPermission("aivideo:app-role:edit")
    @Log(title = "创作端角色管理", businessType = BusinessType.INSERT)
    @PostMapping("/api/admin/app-roles")
    public R<AppRoleAdminVo> create(@Valid @RequestBody CreateAppRoleBo command) {
        return R.ok(appIdentityAdminService.createRole(command));
    }

    /**
     * 更新创作端角色元数据。
     *
     * @param id 创作端角色编号（字符串）
     */
    @SaCheckPermission("aivideo:app-role:edit")
    @Log(title = "创作端角色管理", businessType = BusinessType.UPDATE)
    @PutMapping("/api/admin/app-roles/{id}")
    public R<Void> update(@PathVariable String id, @Valid @RequestBody UpdateAppRoleBo command) {
        appIdentityAdminService.updateRole(id, command);
        return R.ok();
    }

    /**
     * 替换创作端角色的权限集合。
     *
     * @param id 创作端角色编号（字符串）
     */
    @SaCheckPermission("aivideo:app-role:assign-permission")
    @Log(title = "创作端角色管理", businessType = BusinessType.GRANT)
    @PutMapping("/api/admin/app-roles/{id}/permissions")
    public R<Void> replacePermissions(@PathVariable String id,
                                      @Valid @RequestBody ReplaceAppRolePermissionsBo command) {
        appIdentityAdminService.replaceRolePermissions(id, command);
        return R.ok();
    }

    /**
     * 查询创作端权限注册表。
     */
    @SaCheckPermission("aivideo:app-role:query")
    @GetMapping("/api/admin/app-permissions")
    public R<List<AppPermissionAdminVo>> listPermissions() {
        return R.ok(appIdentityAdminService.listPermissions());
    }
}
