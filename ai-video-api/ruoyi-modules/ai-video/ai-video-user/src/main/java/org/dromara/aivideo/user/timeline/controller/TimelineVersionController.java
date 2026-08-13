package org.dromara.aivideo.user.timeline.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dromara.aivideo.identity.security.AppLoginHelper;
import org.dromara.aivideo.timeline.service.ITimelineVersionService;
import org.dromara.aivideo.user.timeline.domain.bo.CreateTimelineConflictCopyBo;
import org.dromara.aivideo.user.timeline.domain.bo.CreateTimelineVersionBo;
import org.dromara.aivideo.user.timeline.domain.bo.RestoreTimelineVersionBo;
import org.dromara.aivideo.user.timeline.domain.bo.TimelineVersionQueryBo;
import org.dromara.aivideo.user.timeline.domain.vo.TimelineVersionVo;
import org.dromara.aivideo.user.timeline.domain.vo.TimelineWriteResultVo;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.domain.R;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/studio/creation-projects")
public class TimelineVersionController {

    private final ITimelineVersionService versionService;
    private final AppLoginHelper loginHelper;

    @GetMapping("/{projectId}/timeline-versions")
    @SaCheckPermission(value = "aivideo:creation:query", type = "app")
    public R<PageResult<TimelineVersionVo>> list(@PathVariable String projectId, @Valid TimelineVersionQueryBo query) {
        int pageSize = query == null || query.pageSize() == null ? 20 : query.pageSize();
        int pageNum = query == null || query.pageNum() == null ? 1 : query.pageNum();
        PageResult<ITimelineVersionService.TimelineVersionView> page = versionService.pageOwnedVersions(actorId(),
            projectId, new PageQuery(pageSize, pageNum));
        return R.ok(PageResult.build(page.getRows().stream().map(TimelineVersionVo::from).toList(), page.getTotal()));
    }

    @PostMapping("/{projectId}/timeline-versions")
    @SaCheckPermission(value = "aivideo:creation:edit", type = "app")
    @Log(title = "timeline version", businessType = BusinessType.INSERT,
        isSaveRequestData = false, isSaveResponseData = false)
    public R<TimelineVersionVo> create(@PathVariable String projectId,
                                       @Valid @RequestBody CreateTimelineVersionBo body) {
        return R.ok(TimelineVersionVo.from(versionService.createManualVersion(actorId(), projectId,
            new ITimelineVersionService.CreateManualVersionCommand(body.getIdempotencyKey(), body.getExpectedRevision()))));
    }

    @PostMapping("/{projectId}/timeline-versions/{versionId}/restorations")
    @SaCheckPermission(value = "aivideo:creation:edit", type = "app")
    @Log(title = "timeline version restoration", businessType = BusinessType.UPDATE,
        isSaveRequestData = false, isSaveResponseData = false)
    public R<TimelineWriteResultVo> restore(@PathVariable String projectId, @PathVariable String versionId,
                                            @Valid @RequestBody RestoreTimelineVersionBo body) {
        return R.ok(TimelineWriteResultVo.from(versionService.restoreVersion(actorId(), projectId, versionId,
            new ITimelineVersionService.RestoreTimelineVersionCommand(body.getIdempotencyKey(),
                body.getExpectedRevision()))));
    }

    @PostMapping("/{projectId}/timeline-versions/conflict-copies")
    @SaCheckPermission(value = "aivideo:creation:edit", type = "app")
    @Log(title = "timeline conflict copy", businessType = BusinessType.INSERT,
        isSaveRequestData = false, isSaveResponseData = false)
    public R<TimelineVersionVo> conflictCopy(@PathVariable String projectId,
                                             @Valid @RequestBody CreateTimelineConflictCopyBo body) {
        return R.ok(TimelineVersionVo.from(versionService.createConflictCopy(actorId(), projectId,
            new ITimelineVersionService.CreateConflictCopyCommand(body.getIdempotencyKey(), body.getBaseRevision(),
                body.getSchemaVersion(), body.getTimeline()))));
    }

    private long actorId() {
        return loginHelper.getLoginUser().userId();
    }
}
