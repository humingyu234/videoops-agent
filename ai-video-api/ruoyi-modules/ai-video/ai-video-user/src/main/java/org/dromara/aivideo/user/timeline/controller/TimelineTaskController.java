package org.dromara.aivideo.user.timeline.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dromara.aivideo.identity.security.AppLoginHelper;
import org.dromara.aivideo.user.task.domain.vo.AiTaskVo;
import org.dromara.aivideo.user.timeline.domain.bo.CreateFancyTextSuggestionTaskBo;
import org.dromara.aivideo.user.timeline.domain.bo.CreateImagePromptTaskBo;
import org.dromara.aivideo.user.timeline.domain.bo.CreateSubtitleAlignmentTaskBo;
import org.dromara.aivideo.user.timeline.domain.bo.CreateTimelineRenderTaskBo;
import org.dromara.aivideo.user.timeline.service.TimelineTaskApplicationService;
import org.dromara.common.core.domain.R;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/studio/creation-projects")
public class TimelineTaskController {

    private final TimelineTaskApplicationService taskApplicationService;
    private final AppLoginHelper loginHelper;

    @PostMapping("/{projectId}/image-prompt-tasks")
    @SaCheckPermission(value = "aivideo:creation:generate", type = "app")
    @Log(title = "image prompt task", businessType = BusinessType.INSERT,
        isSaveRequestData = false, isSaveResponseData = false)
    public R<AiTaskVo> createImagePrompt(@PathVariable String projectId,
                                         @Valid @RequestBody CreateImagePromptTaskBo body) {
        return R.ok(AiTaskVo.from(taskApplicationService.createImagePrompt(actorId(), projectId, body)));
    }

    @PostMapping("/{projectId}/fancy-text-suggestion-tasks")
    @SaCheckPermission(value = "aivideo:creation:generate", type = "app")
    @Log(title = "fancy text task", businessType = BusinessType.INSERT,
        isSaveRequestData = false, isSaveResponseData = false)
    public R<AiTaskVo> createFancyText(@PathVariable String projectId,
                                       @Valid @RequestBody CreateFancyTextSuggestionTaskBo body) {
        return R.ok(AiTaskVo.from(taskApplicationService.createFancyText(actorId(), projectId, body)));
    }

    @PostMapping("/{projectId}/subtitle-alignment-tasks")
    @SaCheckPermission(value = "aivideo:creation:generate", type = "app")
    @Log(title = "subtitle alignment task", businessType = BusinessType.INSERT,
        isSaveRequestData = false, isSaveResponseData = false)
    public R<AiTaskVo> createSubtitleAlignment(@PathVariable String projectId,
                                                @Valid @RequestBody CreateSubtitleAlignmentTaskBo body) {
        return R.ok(AiTaskVo.from(taskApplicationService.createSubtitleAlignment(actorId(), projectId, body)));
    }

    @PostMapping("/{projectId}/render-tasks")
    @SaCheckPermission(value = "aivideo:creation:generate", type = "app")
    @Log(title = "timeline render task", businessType = BusinessType.INSERT,
        isSaveRequestData = false, isSaveResponseData = false)
    public R<AiTaskVo> createRender(@PathVariable String projectId,
                                    @Valid @RequestBody CreateTimelineRenderTaskBo body) {
        return R.ok(AiTaskVo.from(taskApplicationService.createRender(actorId(), projectId, body)));
    }

    private long actorId() {
        return loginHelper.getLoginUser().userId();
    }
}
