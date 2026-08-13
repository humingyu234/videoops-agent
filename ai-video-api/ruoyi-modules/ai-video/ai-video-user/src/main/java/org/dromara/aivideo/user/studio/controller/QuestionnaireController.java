package org.dromara.aivideo.user.studio.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.Valid;
import org.dromara.aivideo.user.studio.domain.bo.QuestionnaireGenerateBo;
import org.dromara.aivideo.user.studio.domain.vo.QuestionnaireGenerateVo;
import org.dromara.aivideo.user.studio.service.IQuestionnaireService;
import org.dromara.common.core.domain.R;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

/** 创作端说需求问卷入口，受现有 app 认证拦截器保护。 */
@RestController
@RequestMapping("/api/studio/questionnaires")
public class QuestionnaireController {

    private final IQuestionnaireService questionnaireService;

    public QuestionnaireController(IQuestionnaireService questionnaireService) {
        this.questionnaireService = Objects.requireNonNull(questionnaireService);
    }

    /** 结合当前发布知识生成后续澄清问题。 */
    @PostMapping("/generate")
    @SaCheckPermission(value = "aivideo:studio:generate", type = "app")
    public R<QuestionnaireGenerateVo> generate(@Valid @RequestBody QuestionnaireGenerateBo request) {
        return R.ok(questionnaireService.generate(request));
    }
}
