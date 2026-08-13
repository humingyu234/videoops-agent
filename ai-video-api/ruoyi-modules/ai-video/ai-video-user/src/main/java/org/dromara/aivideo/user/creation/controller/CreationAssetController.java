package org.dromara.aivideo.user.creation.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dromara.aivideo.creation.dto.CreationAssetDTO;
import org.dromara.aivideo.creation.dto.CreationAssetQueryDTO;
import org.dromara.aivideo.creation.dto.CreationAssetUploadDTO;
import org.dromara.aivideo.creation.service.CreationMediaHandle;
import org.dromara.aivideo.creation.service.ICreationAssetService;
import org.dromara.aivideo.identity.security.AppLoginHelper;
import org.dromara.aivideo.timeline.constant.TimelineErrorCodes;
import org.dromara.aivideo.user.creation.domain.bo.CreationAssetQueryBo;
import org.dromara.aivideo.user.creation.domain.bo.UploadCreationAssetBo;
import org.dromara.aivideo.user.creation.domain.vo.CreationAssetVo;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/studio/creation-assets")
public class CreationAssetController {

    private final ICreationAssetService assetService;
    private final AppLoginHelper loginHelper;

    @GetMapping
    @SaCheckPermission(value = "aivideo:creation-asset:query", type = "app")
    public R<PageResult<CreationAssetVo>> list(@Valid CreationAssetQueryBo query) {
        PageQuery page = new PageQuery(query == null || query.pageSize() == null ? 20 : query.pageSize(),
            query == null || query.pageNum() == null ? 1 : query.pageNum());
        PageResult<CreationAssetDTO> result = assetService.pageOwned(actorId(), new CreationAssetQueryDTO(
            query == null ? null : query.assetType(), null, query == null ? null : query.status(), null), page);
        return R.ok(PageResult.build(result.getRows().stream().map(CreationAssetVo::from).toList(), result.getTotal()));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @SaCheckPermission(value = "aivideo:creation-asset:upload", type = "app")
    public R<CreationAssetVo> upload(@RequestParam("file") MultipartFile file,
                                     @Valid @ModelAttribute UploadCreationAssetBo metadata,
                                     MultipartHttpServletRequest request) {
        requireOnlyCreationAssetUploadParts(request);
        if (file == null || file.isEmpty()) {
            throw new ServiceException("创作素材文件不能为空", 46320);
        }
        try (InputStream input = file.getInputStream()) {
            CreationAssetDTO result = assetService.uploadOwned(actorId(), new CreationAssetUploadDTO(
                file.getOriginalFilename(), file.getContentType(), metadata.usageIntent(), metadata.idempotencyKey(),
                "server-calculated", file.getSize()), input);
            return R.ok(CreationAssetVo.from(result));
        } catch (IOException exception) {
            throw new ServiceException("创作素材读取失败", 46320);
        }
    }

    @GetMapping("/{assetId}")
    @SaCheckPermission(value = "aivideo:creation-asset:query", type = "app")
    public R<CreationAssetVo> detail(@PathVariable String assetId) {
        return R.ok(CreationAssetVo.from(assetService.getOwned(actorId(), assetId)));
    }

    @GetMapping("/{assetId}/content")
    @SaCheckPermission(value = "aivideo:creation-asset:query", type = "app")
    public ResponseEntity<StreamingResponseBody> content(@PathVariable String assetId,
                                                           @RequestHeader(value = HttpHeaders.RANGE, required = false) String range) {
        CreationAssetDTO metadata = assetService.getOwned(actorId(), assetId);
        CreationMediaHandle handle;
        try {
            handle = range == null
                ? assetService.openOwnedMedia(actorId(), assetId, null)
                : assetService.openOwnedMediaRange(actorId(), assetId, range);
        } catch (ServiceException exception) {
            if (range != null && exception.getCode() == 416) {
                return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                    .header(HttpHeaders.CONTENT_RANGE, "bytes */" + metadata.sizeBytes())
                    .build();
            }
            throw exception;
        }
        long start = handle.offset();
        long length = handle.length();
        StreamingResponseBody body = output -> {
            try (handle) {
                handle.stream().transferTo(output);
            }
        };
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(metadata.mimeType()));
        headers.setContentLength(length);
        headers.set(HttpHeaders.ACCEPT_RANGES, "bytes");
        headers.setCacheControl("no-store");
        headers.setContentDisposition(ContentDisposition.inline()
            .filename(safeInlineName(metadata.originalName()), StandardCharsets.UTF_8).build());
        if (range != null) {
            headers.set(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + (start + length - 1)
                + "/" + handle.totalSize());
        }
        return new ResponseEntity<>(body, headers, range == null ? HttpStatus.OK : HttpStatus.PARTIAL_CONTENT);
    }

    @DeleteMapping("/{assetId}")
    @SaCheckPermission(value = "aivideo:creation-asset:delete", type = "app")
    public R<Void> delete(@PathVariable String assetId) {
        assetService.deleteOwned(actorId(), assetId);
        return R.ok();
    }

    private long actorId() {
        return loginHelper.getLoginUser().userId();
    }

    private void requireOnlyCreationAssetUploadParts(MultipartHttpServletRequest request) {
        if (request == null) {
            throw new ServiceException("Invalid creation asset upload fields", TimelineErrorCodes.TIMELINE_ASSET_INVALID);
        }
        Set<String> partNames = new HashSet<>(request.getParameterMap().keySet());
        partNames.addAll(request.getMultiFileMap().keySet());
        if (!partNames.equals(Set.of("file", "usageIntent", "idempotencyKey"))
            || !hasOneValue(request, "usageIntent") || !hasOneValue(request, "idempotencyKey")
            || request.getFiles("file").size() != 1) {
            throw new ServiceException("Invalid creation asset upload fields", TimelineErrorCodes.TIMELINE_ASSET_INVALID);
        }
    }

    private boolean hasOneValue(MultipartHttpServletRequest request, String name) {
        String[] values = request.getParameterValues(name);
        return values != null && values.length == 1;
    }

    private String safeInlineName(String name) {
        if (name == null || name.isBlank()) {
            return "asset";
        }
        return name.replace('\\', '_').replace('/', '_').replace('\r', '_').replace('\n', '_');
    }
}
