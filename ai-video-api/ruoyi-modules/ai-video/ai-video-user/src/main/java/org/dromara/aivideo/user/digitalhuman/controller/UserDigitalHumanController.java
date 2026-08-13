package org.dromara.aivideo.user.digitalhuman.controller;

import org.dromara.aivideo.digitalhuman.dto.CreateDigitalHumanVideoByResourceDTO;
import org.dromara.aivideo.digitalhuman.dto.CreateVoiceGenerationByResourceDTO;
import org.dromara.aivideo.digitalhuman.service.IDigitalHumanResourceGenerationService;
import org.dromara.aivideo.user.digitalhuman.domain.bo.CreateVideoJobByResourceBo;
import org.dromara.aivideo.user.digitalhuman.domain.bo.CreateVoiceJobByResourceBo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.Valid;
import org.dromara.aivideo.digitalhuman.dto.CreateDigitalHumanVideoJobDTO;
import org.dromara.aivideo.digitalhuman.dto.CreateVoiceGenerationJobDTO;
import org.dromara.aivideo.digitalhuman.dto.DigitalHumanMediaContentDTO;
import org.dromara.aivideo.digitalhuman.dto.DigitalHumanOwnerDTO;
import org.dromara.aivideo.digitalhuman.service.IDigitalHumanGenerationService;
import org.dromara.aivideo.identity.dto.AppPrincipalSnapshotDTO;
import org.dromara.aivideo.identity.dto.AppWorkspaceSessionSnapshotDTO;
import org.dromara.aivideo.identity.security.AppLoginHelper;
import org.dromara.aivideo.identity.security.ConditionalOnAppSecurityEnabled;
import org.dromara.aivideo.user.digitalhuman.domain.bo.CreateVideoJobBo;
import org.dromara.aivideo.user.digitalhuman.domain.bo.CreateVoiceJobBo;
import org.dromara.aivideo.user.digitalhuman.domain.vo.DigitalHumanJobVo;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

@Validated
@RestController
@ConditionalOnAppSecurityEnabled
@RequestMapping("/api/studio")
public class UserDigitalHumanController {

    private static final Set<String> VOICE_PARAMETERS = Set.of("scriptText");
    private static final Set<String> VOICE_FILES = Set.of("referenceAudio");
    private static final Set<String> VIDEO_PARAMETERS = Set.of("voiceJobId");
    private static final Set<String> VIDEO_FILES = Set.of("portraitImage");

    private final IDigitalHumanGenerationService generationService;
    private final IDigitalHumanResourceGenerationService resourceGenerationService;
    private final AppLoginHelper loginHelper;

    @Autowired
    public UserDigitalHumanController(IDigitalHumanGenerationService generationService,
                                      IDigitalHumanResourceGenerationService resourceGenerationService,
                                      AppLoginHelper loginHelper) {
        this.generationService = generationService;
        this.resourceGenerationService = resourceGenerationService;
        this.loginHelper = loginHelper;
    }

    public UserDigitalHumanController(IDigitalHumanGenerationService generationService, AppLoginHelper loginHelper) {
        this(generationService, null, loginHelper);
    }

    @SaCheckPermission(value = "aivideo:studio:generate", type = "app")
    @PostMapping(value = "/voice-jobs", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<DigitalHumanJobVo> createVoiceJob(
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @Valid @ModelAttribute CreateVoiceJobBo body,
        MultipartHttpServletRequest request) {
        requireExactMultipart(request, VOICE_PARAMETERS, VOICE_FILES);
        try {
            return R.ok(DigitalHumanJobVo.from(generationService.createVoiceJob(new CreateVoiceGenerationJobDTO(
                currentOwner(), idempotencyKey, body.getScriptText(), body.getReferenceAudio().getOriginalFilename(),
                body.getReferenceAudio().getContentType(), body.getReferenceAudio().getBytes()))));
        } catch (IOException exception) {
            throw new ServiceException("读取参考音频失败");
        }
    }

    @SaCheckPermission(value = "aivideo:studio:generate", type = "app")
    @PostMapping(value = "/voice-jobs", consumes = MediaType.APPLICATION_JSON_VALUE)
    public R<DigitalHumanJobVo> createVoiceJobByResource(
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @Valid @RequestBody CreateVoiceJobByResourceBo body) {
        AppPrincipalSnapshotDTO principal = loginHelper.getPrincipal();
        return R.ok(DigitalHumanJobVo.from(resourceGenerationService.createVoiceJob(
            new CreateVoiceGenerationByResourceDTO(principal, idempotencyKey,
                body.scriptText(), body.referenceVoiceId()))));
    }

    @SaCheckPermission(value = "aivideo:studio:generate", type = "app")
    @PostMapping("/voice-jobs/{jobId}/confirmation")
    public R<DigitalHumanJobVo> confirmVoiceJob(@PathVariable Long jobId) {
        return R.ok(DigitalHumanJobVo.from(generationService.confirmVoiceJob(jobId, currentOwner())));
    }

    @SaCheckPermission(value = "aivideo:studio:generate", type = "app")
    @PostMapping(value = "/video-jobs", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<DigitalHumanJobVo> createVideoJob(
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @Valid @ModelAttribute CreateVideoJobBo body,
        MultipartHttpServletRequest request) {
        requireExactMultipart(request, VIDEO_PARAMETERS, VIDEO_FILES);
        try {
            return R.ok(DigitalHumanJobVo.from(generationService.createVideoJob(new CreateDigitalHumanVideoJobDTO(
                currentOwner(), idempotencyKey, body.getVoiceJobId(), body.getPortraitImage().getOriginalFilename(),
                body.getPortraitImage().getContentType(), body.getPortraitImage().getBytes()))));
        } catch (IOException exception) {
            throw new ServiceException("读取人物图片失败");
        }
    }

    @SaCheckPermission(value = "aivideo:studio:generate", type = "app")
    @PostMapping(value = "/video-jobs", consumes = MediaType.APPLICATION_JSON_VALUE)
    public R<DigitalHumanJobVo> createVideoJobByResource(
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @Valid @RequestBody CreateVideoJobByResourceBo body) {
        AppPrincipalSnapshotDTO principal = loginHelper.getPrincipal();
        return R.ok(DigitalHumanJobVo.from(resourceGenerationService.createVideoJob(
            new CreateDigitalHumanVideoByResourceDTO(principal, idempotencyKey,
                body.voiceJobId(), body.portraitId()))));
    }

    @SaCheckPermission(value = "aivideo:studio:query", type = "app")
    @GetMapping("/jobs/{jobId}")
    public R<DigitalHumanJobVo> getJob(@PathVariable Long jobId) {
        return R.ok(DigitalHumanJobVo.from(generationService.getJob(jobId, currentOwner())));
    }

    @SaCheckPermission(value = "aivideo:studio:query", type = "app")
    @GetMapping("/jobs/{jobId}/media")
    public ResponseEntity<byte[]> getMedia(@PathVariable Long jobId) {
        DigitalHumanMediaContentDTO media = generationService.getOutputMedia(jobId, currentOwner());
        MediaType type;
        try {
            type = MediaType.parseMediaType(media.mediaType());
        } catch (RuntimeException exception) {
            type = MediaType.APPLICATION_OCTET_STREAM;
        }
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .contentType(type)
            .contentLength(media.content().length)
            .header(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.inline().filename(media.fileName(), StandardCharsets.UTF_8).build().toString())
            .body(media.content());
    }

    private DigitalHumanOwnerDTO currentOwner() {
        AppPrincipalSnapshotDTO principal = loginHelper.getPrincipal();
        AppWorkspaceSessionSnapshotDTO workspace = principal.workspace();
        if (workspace == null || workspace.tenantId() == null || workspace.tenantId() <= 0
            || principal.appUserId() == null || principal.appUserId() <= 0) {
            throw new ServiceException("当前工作区无效");
        }
        return new DigitalHumanOwnerDTO(workspace.tenantId(), principal.appUserId());
    }

    private static void requireExactMultipart(MultipartHttpServletRequest request,
                                              Set<String> expectedParameters,
                                              Set<String> expectedFiles) {
        boolean parametersMatch = request.getParameterMap().keySet().equals(expectedParameters)
            && request.getParameterMap().values().stream()
            .allMatch(values -> values != null && values.length == 1);
        boolean filesMatch = request.getMultiFileMap().keySet().equals(expectedFiles)
            && request.getMultiFileMap().values().stream()
            .allMatch(files -> files != null && files.size() == 1);
        if (!parametersMatch || !filesMatch) {
            throw new ServiceException("请求字段不符合契约");
        }
    }
}
