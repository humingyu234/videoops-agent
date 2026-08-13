package org.dromara.aivideo.user.script.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dromara.aivideo.identity.security.AppLoginHelper;
import org.dromara.aivideo.script.dto.UserScriptCreateDTO;
import org.dromara.aivideo.script.dto.UserScriptEditDTO;
import org.dromara.aivideo.script.dto.UserScriptQueryDTO;
import org.dromara.aivideo.script.service.IUserScriptService;
import org.dromara.aivideo.user.script.domain.bo.CreateUserScriptBo;
import org.dromara.aivideo.user.script.domain.bo.EditUserScriptBo;
import org.dromara.aivideo.user.script.domain.vo.ScriptVersionVo;
import org.dromara.aivideo.user.script.domain.vo.UserScriptDetailVo;
import org.dromara.aivideo.user.script.domain.vo.UserScriptListVo;
import org.dromara.aivideo.user.script.domain.vo.UserScriptSaveResultVo;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.domain.R;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.web.core.BaseController;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 创作端当前登录用户的个人文案接口。 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/studio/scripts")
public class UserScriptController extends BaseController {
    private final IUserScriptService userScriptService;
    private final AppLoginHelper loginHelper;

    /** 分页查询当前用户的文案。 */
    @GetMapping
    @SaCheckPermission(value = "aivideo:script:query", type = "app")
    public R<PageResult<UserScriptListVo>> list(
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) String orderByColumn,
        @RequestParam(required = false) String isAsc,
        PageQuery pageQuery
    ) {
        var page = userScriptService.queryPage(new UserScriptQueryDTO(keyword, orderByColumn, isAsc),
            loginHelper.getPrincipal(), pageQuery);
        List<UserScriptListVo> rows = page.getRows().stream().map(UserScriptListVo::from).toList();
        return R.ok(PageResult.build(rows, page.getTotal()));
    }

    /** 查询文案详情与版本摘要。 */
    @GetMapping("/{scriptId}")
    @SaCheckPermission(value = "aivideo:script:query", type = "app")
    public R<UserScriptDetailVo> detail(@PathVariable String scriptId) {
        return R.ok(UserScriptDetailVo.from(
            userScriptService.queryById(scriptId, loginHelper.getPrincipal())));
    }

    /** 查询指定版本正文。 */
    @GetMapping("/{scriptId}/versions/{versionId}")
    @SaCheckPermission(value = "aivideo:script:query", type = "app")
    public R<ScriptVersionVo> version(@PathVariable String scriptId, @PathVariable String versionId) {
        return R.ok(ScriptVersionVo.from(
            userScriptService.queryVersion(scriptId, versionId, loginHelper.getPrincipal())));
    }

    /** 手工创建文案及首个版本。 */
    @PostMapping
    @SaCheckPermission(value = "aivideo:script:edit", type = "app")
    public R<UserScriptSaveResultVo> create(@Valid @RequestBody CreateUserScriptBo body) {
        var result = userScriptService.create(new UserScriptCreateDTO(
            body.getDisplayTitle(), body.getScriptText(), body.getIdempotencyKey()), loginHelper.getPrincipal());
        return R.ok(UserScriptSaveResultVo.from(result));
    }

    /** 基于当前历史版本创建不可变的新版本。 */
    @PostMapping("/{scriptId}/versions")
    @SaCheckPermission(value = "aivideo:script:edit", type = "app")
    public R<UserScriptSaveResultVo> createVersion(
        @PathVariable String scriptId,
        @Valid @RequestBody EditUserScriptBo body
    ) {
        var result = userScriptService.createVersion(new UserScriptEditDTO(scriptId,
            body.getParentVersionId(), body.getExpectedScriptRevision(), body.getDisplayTitle(),
            body.getScriptText(), body.getIdempotencyKey()), loginHelper.getPrincipal());
        return R.ok(UserScriptSaveResultVo.from(result));
    }

    /** 删除当前用户拥有的文案。 */
    @DeleteMapping("/{scriptId}")
    @SaCheckPermission(value = "aivideo:script:remove", type = "app")
    public R<Void> delete(@PathVariable String scriptId) {
        userScriptService.delete(scriptId, loginHelper.getPrincipal());
        return R.ok();
    }
}
