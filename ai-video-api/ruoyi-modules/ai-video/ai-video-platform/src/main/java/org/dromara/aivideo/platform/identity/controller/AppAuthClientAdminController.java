package org.dromara.aivideo.platform.identity.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dromara.aivideo.platform.identity.domain.bo.AppIdentityAdminBos.AppAuthClientQueryBo;
import org.dromara.aivideo.platform.identity.domain.bo.AppIdentityAdminBos.CreateAppAuthClientBo;
import org.dromara.aivideo.platform.identity.domain.bo.AppIdentityAdminBos.RotateAppAuthClientSecretBo;
import org.dromara.aivideo.platform.identity.domain.bo.AppIdentityAdminBos.UpdateAppAuthClientBo;
import org.dromara.aivideo.platform.identity.domain.vo.AppIdentityAdminVos.AppAuthClientAdminVo;
import org.dromara.aivideo.platform.identity.domain.vo.AppIdentityAdminVos.AppAuthClientSecretVo;
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
 * 运营端创作认证客户端管理入口。
 *
 * <p>仅返回当前成功响应中的一次性客户端密钥，不提供 app token 签发或冒充能力。</p>
 */
@Validated
@RequiredArgsConstructor
@RestController
public class AppAuthClientAdminController extends BaseController {

    private final IAppIdentityAdminService appIdentityAdminService;

    /**
     * 分页查询创作端认证客户端。
     */
    @SaCheckPermission("aivideo:app-auth-client:query")
    @GetMapping("/api/admin/app-auth-clients")
    public R<PageResult<AppAuthClientAdminVo>> page(@Valid AppAuthClientQueryBo query, PageQuery pageQuery) {
        return R.ok(appIdentityAdminService.pageAuthClients(query, pageQuery));
    }

    /**
     * 创建创作端认证客户端并返回一次性密钥。
     */
    @SaCheckPermission("aivideo:app-auth-client:edit")
    @Log(title = "创作端认证客户端管理", businessType = BusinessType.INSERT,
        isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping("/api/admin/app-auth-clients")
    public R<AppAuthClientSecretVo> create(@Valid @RequestBody CreateAppAuthClientBo command) {
        return R.ok(appIdentityAdminService.createAuthClient(command));
    }

    /**
     * 更新创作端认证客户端策略。
     *
     * @param id 创作端认证客户端编号（字符串）
     */
    @SaCheckPermission("aivideo:app-auth-client:edit")
    @Log(title = "创作端认证客户端管理", businessType = BusinessType.UPDATE)
    @PutMapping("/api/admin/app-auth-clients/{id}")
    public R<Void> update(@PathVariable String id, @Valid @RequestBody UpdateAppAuthClientBo command) {
        appIdentityAdminService.updateAuthClient(id, command);
        return R.ok();
    }

    /**
     * 轮换创作端认证客户端密钥。
     *
     * @param id 创作端认证客户端编号（字符串）
     */
    @SaCheckPermission("aivideo:app-auth-client:rotate-secret")
    @Log(title = "创作端认证客户端管理", businessType = BusinessType.UPDATE,
        isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping("/api/admin/app-auth-clients/{id}/secret-rotations")
    public R<AppAuthClientSecretVo> rotateSecret(@PathVariable String id,
                                                 @Valid @RequestBody RotateAppAuthClientSecretBo command) {
        return R.ok(appIdentityAdminService.rotateAuthClientSecret(id, command));
    }
}
