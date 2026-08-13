package org.dromara.aivideo.platform.knowledge.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dromara.aivideo.platform.knowledge.domain.bo.KnowledgeItemAdminQueryBo;
import org.dromara.aivideo.platform.knowledge.domain.bo.KnowledgeItemSaveBo;
import org.dromara.aivideo.platform.knowledge.domain.bo.KnowledgeItemStatusBo;
import org.dromara.aivideo.platform.knowledge.domain.vo.KnowledgeImportSummaryVo;
import org.dromara.aivideo.platform.knowledge.domain.vo.KnowledgeItemAdminVo;
import org.dromara.aivideo.platform.knowledge.domain.vo.KnowledgeItemDetailVo;
import org.dromara.aivideo.platform.knowledge.service.IKnowledgeAdminService;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.domain.R;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.common.web.core.BaseController;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** 运营端知识库管理入口。 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/admin/knowledge-items")
public class KnowledgeAdminController extends BaseController {

    private final IKnowledgeAdminService knowledgeAdminService;

    /** 分页查询知识条目。 */
    @SaCheckPermission("aivideo:knowledge:query")
    @GetMapping
    public R<PageResult<KnowledgeItemAdminVo>> page(@Valid KnowledgeItemAdminQueryBo query,
                                                    PageQuery pageQuery) {
        return R.ok(knowledgeAdminService.page(query, pageQuery));
    }

    /** 查看知识详情。 */
    @SaCheckPermission("aivideo:knowledge:query")
    @GetMapping("/{id}")
    public R<KnowledgeItemDetailVo> detail(@PathVariable Long id) {
        return R.ok(knowledgeAdminService.detail(id));
    }

    /** 新增知识。 */
    @SaCheckPermission("aivideo:knowledge:add")
    @Log(title = "知识库管理", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Long> create(@Valid @RequestBody KnowledgeItemSaveBo bo) {
        return R.ok(knowledgeAdminService.create(bo, LoginHelper.getUserId()));
    }

    /** 编辑知识。 */
    @SaCheckPermission("aivideo:knowledge:edit")
    @Log(title = "知识库管理", businessType = BusinessType.UPDATE)
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody KnowledgeItemSaveBo bo) {
        knowledgeAdminService.update(id, bo, LoginHelper.getUserId());
        return R.ok();
    }

    /** 修改知识状态。 */
    @SaCheckPermission("aivideo:knowledge:edit")
    @Log(title = "知识库管理", businessType = BusinessType.UPDATE)
    @PutMapping("/{id}/status")
    public R<Void> changeStatus(@PathVariable Long id, @Valid @RequestBody KnowledgeItemStatusBo bo) {
        knowledgeAdminService.changeStatus(id, bo.getStatus(), LoginHelper.getUserId());
        return R.ok();
    }

    /** 删除知识。 */
    @SaCheckPermission("aivideo:knowledge:remove")
    @Log(title = "知识库管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        knowledgeAdminService.delete(id);
        return R.ok();
    }

    /** 批量导入知识文件。 */
    @SaCheckPermission("aivideo:knowledge:import")
    @Log(title = "知识库管理", businessType = BusinessType.IMPORT)
    @PostMapping(path = "/imports", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<KnowledgeImportSummaryVo> imports(@RequestPart("files") List<MultipartFile> files,
                                               @RequestParam("names")
                                               List<String> names,
                                               @RequestParam("knowledgeTypes")
                                               List<String> knowledgeTypes,
                                               @RequestParam("statuses")
                                               List<String> statuses) {
        return R.ok(knowledgeAdminService.importFiles(files, names, knowledgeTypes, statuses,
            LoginHelper.getUserId()));
    }

    /** 发布条目的最新草稿。 */
    @SaCheckPermission("aivideo:knowledge:edit")
    @Log(title = "知识库管理", businessType = BusinessType.UPDATE)
    @PostMapping("/{id}/publish")
    public R<Void> publish(@PathVariable Long id) {
        knowledgeAdminService.changeStatus(id, "published", LoginHelper.getUserId());
        return R.ok();
    }
}
