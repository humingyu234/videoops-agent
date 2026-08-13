package org.dromara.aivideo.user.portrait.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dromara.aivideo.asset.dto.AssetDTO;
import org.dromara.aivideo.asset.dto.UploadPortraitImageDTO;
import org.dromara.aivideo.asset.service.IAssetService;
import org.dromara.aivideo.identity.dto.AppPrincipalSnapshotDTO;
import org.dromara.aivideo.identity.security.AppLoginHelper;
import org.dromara.aivideo.portrait.dto.CreatePortraitDTO;
import org.dromara.aivideo.portrait.dto.PortraitDTO;
import org.dromara.aivideo.portrait.dto.PortraitQueryDTO;
import org.dromara.aivideo.portrait.dto.UpdatePortraitDTO;
import org.dromara.aivideo.portrait.service.IPortraitService;
import org.dromara.aivideo.user.portrait.domain.bo.CreatePortraitBo;
import org.dromara.aivideo.user.portrait.domain.bo.UpdatePortraitBo;
import org.dromara.aivideo.user.portrait.domain.vo.PortraitAccessUrlVo;
import org.dromara.aivideo.user.portrait.domain.vo.PortraitAssetVo;
import org.dromara.aivideo.user.portrait.domain.vo.PortraitDetailVo;
import org.dromara.aivideo.user.portrait.domain.vo.PortraitListVo;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.redis.annotation.RepeatSubmit;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/** 创作端用户人物形象接口。 */
@Validated
@RestController
@RequiredArgsConstructor
public class PortraitController {
    private final IPortraitService portraitService;
    private final IAssetService assetService;
    private final AppLoginHelper loginHelper;

    @GetMapping("/api/portraits")
    public R<PageResult<PortraitListVo>> list(@RequestParam(required = false) String keyword,
                                          @RequestParam(required = false) String availabilityStatus,
                                          @RequestParam(required = false) String gender,
                                          PageQuery pageQuery) {
        AppPrincipalSnapshotDTO principal = loginHelper.getPrincipal();
        PageResult<PortraitDTO> page = portraitService.queryPage(
            new PortraitQueryDTO(keyword, availabilityStatus, gender), principal, pageQuery);
        List<PortraitListVo> rows = page.getRows().stream().map(PortraitListVo::from).toList();
        return R.ok(PageResult.build(rows, page.getTotal()));
    }

    @GetMapping("/api/portraits/{portraitId}")
    public R<PortraitDetailVo> detail(@PathVariable String portraitId) {
        return R.ok(PortraitDetailVo.from(portraitService.queryById(portraitId, loginHelper.getPrincipal())));
    }

    @PostMapping("/api/assets/uploads/portrait-images")
    public R<PortraitAssetVo> upload(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) throw new ServiceException("人物照片不能为空", 46201);
        try {
            AssetDTO asset = assetService.uploadPortraitImage(new UploadPortraitImageDTO(
                file.getOriginalFilename(), file.getContentType(), file.getBytes()), loginHelper.getPrincipal());
            return R.ok(new PortraitAssetVo(asset.assetId(), asset.availabilityStatus()));
        } catch (IOException exception) {
            throw new ServiceException("人物照片读取失败", 46201);
        }
    }

    @PostMapping("/api/portraits")
    public R<PortraitDetailVo> create(@Valid @RequestBody CreatePortraitBo body) {
        PortraitDTO created = portraitService.create(new CreatePortraitDTO(
            body.assetId(), body.name(), body.gender(), body.sceneTags(), body.note(), body.idempotencyKey()),
            loginHelper.getPrincipal());
        return R.ok(PortraitDetailVo.from(created));
    }

    @PutMapping("/api/portraits/{portraitId}")
    @RepeatSubmit
    public R<PortraitDetailVo> update(@PathVariable String portraitId, @Valid @RequestBody UpdatePortraitBo body) {
        PortraitDTO updated = portraitService.update(new UpdatePortraitDTO(portraitId, body.name(), body.gender(),
            body.sceneTags(), body.note(), body.expectedRevision()), loginHelper.getPrincipal());
        return R.ok(PortraitDetailVo.from(updated));
    }

    @DeleteMapping("/api/portraits/{portraitId}")
    public R<Void> delete(@PathVariable String portraitId, @RequestParam String expectedRevision) {
        portraitService.delete(portraitId, expectedRevision, loginHelper.getPrincipal());
        return R.ok();
    }

    @GetMapping("/api/portraits/{portraitId}/access-url")
    public R<PortraitAccessUrlVo> accessUrl(@PathVariable String portraitId) {
        var access = portraitService.createAccessUrl(portraitId, loginHelper.getPrincipal());
        return R.ok(new PortraitAccessUrlVo(access.url(), access.expiresAt(), access.contentType()));
    }
}
