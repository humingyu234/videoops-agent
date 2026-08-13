package org.dromara.aivideo.user.timeline.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dromara.aivideo.identity.security.AppLoginHelper;
import org.dromara.aivideo.timeline.service.ITimelineDraftService;
import org.dromara.aivideo.user.timeline.domain.bo.SaveTimelineDraftBo;
import org.dromara.aivideo.user.timeline.domain.vo.TimelineDraftVo;
import org.dromara.aivideo.user.timeline.domain.vo.TimelineWriteResultVo;
import org.dromara.common.core.domain.R;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/studio/creation-projects")
public class TimelineDraftController {

    private final ITimelineDraftService draftService;
    private final AppLoginHelper loginHelper;

    @GetMapping("/{projectId}/timeline-draft")
    @SaCheckPermission(value = "aivideo:creation:query", type = "app")
    public R<TimelineDraftVo> get(@PathVariable String projectId) {
        return R.ok(TimelineDraftVo.from(draftService.getOwned(actorId(), projectId)));
    }

    @PutMapping("/{projectId}/timeline-draft")
    @SaCheckPermission(value = "aivideo:creation:edit", type = "app")
    @Log(title = "timeline draft", businessType = BusinessType.UPDATE,
        isSaveRequestData = false, isSaveResponseData = false)
    public R<TimelineWriteResultVo> save(@PathVariable String projectId, @Valid @RequestBody SaveTimelineDraftBo body) {
        return R.ok(TimelineWriteResultVo.from(draftService.save(actorId(), projectId,
            new ITimelineDraftService.SaveTimelineDraftCommand(body.getIdempotencyKey(), body.getExpectedRevision(),
                body.getSchemaVersion(), body.getTimeline()))));
    }

    private long actorId() {
        return loginHelper.getLoginUser().userId();
    }
}
