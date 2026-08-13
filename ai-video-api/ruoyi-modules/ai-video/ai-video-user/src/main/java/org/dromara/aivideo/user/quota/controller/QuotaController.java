package org.dromara.aivideo.user.quota.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.aivideo.identity.security.AppLoginHelper;
import org.dromara.aivideo.quota.service.IQuotaAccountService;
import org.dromara.aivideo.user.quota.domain.vo.QuotaAccountVo;
import org.dromara.common.core.domain.R;
import org.dromara.common.web.core.BaseController;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 创作端个人积分查询。
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/quota")
public class QuotaController extends BaseController {

    private final AppLoginHelper appLoginHelper;
    private final IQuotaAccountService quotaAccountService;

    /**
     * 查询当前登录用户自己的个人积分账户。
     *
     * @return 当前个人积分账户
     */
    @SaCheckPermission(value = "aivideo:quota:query", type = "app")
    @GetMapping("/account")
    public R<QuotaAccountVo> account() {
        long appUserId = appLoginHelper.getLoginUser().userId();
        return R.ok(QuotaAccountVo.from(quotaAccountService.queryPersonalAccount(appUserId)));
    }
}
