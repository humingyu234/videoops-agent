package org.dromara.aivideo.user.studio.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dromara.aivideo.user.studio.domain.bo.ScriptGenerateBo;
import org.dromara.aivideo.user.studio.domain.vo.ScriptGenerateVo;
import org.dromara.aivideo.user.studio.service.IScriptGenerationService;
import org.dromara.common.core.domain.R;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 用户端文案生成接口。 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/studio/scripts")
public class ScriptGenerationController {

    private final IScriptGenerationService scriptGenerationService;

    /** 生成三套候选文案。 */
    @PostMapping("/generate")
    @SaCheckPermission(value = "aivideo:studio:generate", type = "app")
    public R<ScriptGenerateVo> generate(@Valid @RequestBody ScriptGenerateBo request) {
        return R.ok(scriptGenerationService.generate(request));
    }
}
