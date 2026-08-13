package org.dromara.aivideo.user.creation.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import org.dromara.aivideo.creation.dto.CreationAssetDTO;
import org.dromara.aivideo.creation.dto.CreationAssetResolveDTO;
import org.dromara.aivideo.creation.enums.CreationAssetStatus;
import org.dromara.aivideo.creation.enums.CreationAssetType;
import org.dromara.aivideo.creation.enums.CreationAssetUsageOrigin;
import org.dromara.aivideo.creation.service.CreationMediaHandle;
import org.dromara.aivideo.creation.service.ICreationAssetService;
import org.dromara.aivideo.identity.dto.AppPrincipalSnapshotDTO;
import org.dromara.aivideo.identity.security.AppLoginHelper;
import org.dromara.aivideo.identity.security.AppLoginUser;
import org.dromara.aivideo.user.creation.domain.bo.UploadCreationAssetBo;
import org.dromara.aivideo.user.creation.domain.bo.CreationAssetQueryBo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("dev")
class CreationAssetControllerTest {

    @Test
    void routesUseTheFrozenAppPermissions() throws Exception {
        var upload = Arrays.stream(CreationAssetController.class.getDeclaredMethods())
            .filter(method -> method.getName().equals("upload")).findFirst().orElseThrow();
        assertThat(upload.getAnnotation(PostMapping.class).consumes()).containsExactly("multipart/form-data");
        assertPermission(upload.getAnnotation(SaCheckPermission.class), "aivideo:creation-asset:upload");

        var content = CreationAssetController.class.getDeclaredMethod("content", String.class, String.class);
        assertThat(content.getAnnotation(GetMapping.class).value())
            .containsExactly("/{assetId}/content");
        assertPermission(content.getAnnotation(SaCheckPermission.class), "aivideo:creation-asset:query");

        var list = CreationAssetController.class.getDeclaredMethod("list", CreationAssetQueryBo.class);
        assertPermission(list.getAnnotation(SaCheckPermission.class), "aivideo:creation-asset:query");
        var detail = CreationAssetController.class.getDeclaredMethod("detail", String.class);
        assertPermission(detail.getAnnotation(SaCheckPermission.class), "aivideo:creation-asset:query");
        var delete = CreationAssetController.class.getDeclaredMethod("delete", String.class);
        assertThat(delete.getAnnotation(DeleteMapping.class).value()).containsExactly("/{assetId}");
        assertPermission(delete.getAnnotation(SaCheckPermission.class), "aivideo:creation-asset:delete");
    }

    @Test
    void contentStreamsAndClosesTheCreationMediaHandle() throws Exception {
        ICreationAssetService service = mock(ICreationAssetService.class);
        AppLoginHelper loginHelper = mock(AppLoginHelper.class);
        AppPrincipalSnapshotDTO principal = mock(AppPrincipalSnapshotDTO.class);
        when(principal.appUserId()).thenReturn(7L);
        when(loginHelper.getLoginUser()).thenReturn(new AppLoginUser(principal, "session"));
        CreationMediaHandle handle = mock(CreationMediaHandle.class);
        when(handle.offset()).thenReturn(2L);
        when(handle.length()).thenReturn(3L);
        when(handle.totalSize()).thenReturn(10L);
        when(handle.stream()).thenReturn(new ByteArrayInputStream(new byte[] {3, 4, 5}));
        when(service.getOwned(7L, "88")).thenReturn(asset());
        when(service.openOwnedMediaRange(7L, "88", "bytes=2-4")).thenReturn(handle);

        var response = new CreationAssetController(service, loginHelper).content("88", "bytes=2-4");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ((StreamingResponseBody) response.getBody()).writeTo(out);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PARTIAL_CONTENT);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_RANGE)).isEqualTo("bytes 2-4/10");
        assertThat(out.toByteArray()).containsExactly(3, 4, 5);
        verify(handle).close();
    }

    @Test
    void contentClosesTheUnderlyingResponseStreamExactlyOnce() throws Exception {
        ICreationAssetService service = mock(ICreationAssetService.class);
        AppLoginHelper loginHelper = mock(AppLoginHelper.class);
        AppPrincipalSnapshotDTO principal = mock(AppPrincipalSnapshotDTO.class);
        when(principal.appUserId()).thenReturn(7L);
        when(loginHelper.getLoginUser()).thenReturn(new AppLoginUser(principal, "session"));
        CountingHandle handle = new CountingHandle();
        when(service.getOwned(7L, "88")).thenReturn(asset());
        when(service.openOwnedMedia(7L, "88", null)).thenReturn(handle);

        var response = new CreationAssetController(service, loginHelper).content("88", null);
        ((StreamingResponseBody) response.getBody()).writeTo(new ByteArrayOutputStream());

        assertThat(handle.closeCalls).isEqualTo(1);
        assertThat(handle.stream.closeCalls).isEqualTo(1);
    }

    @Test
    void contentClosesTheHandleWhenTheClientStopsReading() throws Exception {
        ICreationAssetService service = mock(ICreationAssetService.class);
        AppLoginHelper loginHelper = loginHelperFor(7L);
        CountingHandle handle = new CountingHandle();
        when(service.getOwned(7L, "88")).thenReturn(asset());
        when(service.openOwnedMedia(7L, "88", null)).thenReturn(handle);

        var response = new CreationAssetController(service, loginHelper).content("88", null);
        assertThatThrownBy(() -> ((StreamingResponseBody) response.getBody()).writeTo(new OutputStream() {
            @Override public void write(int ignored) throws IOException {
                throw new IOException("client disconnected");
            }
        })).isInstanceOf(IOException.class);

        assertThat(handle.closeCalls).isEqualTo(1);
        assertThat(handle.stream.closeCalls).isEqualTo(1);
    }

    @Test
    void invalidRangeReturns416WithTheKnownTotalWithoutOpeningAStream() {
        ICreationAssetService service = mock(ICreationAssetService.class);
        AppLoginHelper loginHelper = loginHelperFor(7L);
        when(service.getOwned(7L, "88")).thenReturn(asset());
        when(service.openOwnedMediaRange(7L, "88", "bytes=10-10"))
            .thenThrow(new org.dromara.common.core.exception.ServiceException("invalid range", 416));

        var response = new CreationAssetController(service, loginHelper).content("88", "bytes=10-10");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_RANGE)).isEqualTo("bytes */10");
        assertThat(response.getBody()).isNull();
    }

    @Test
    void uploadRejectsUnexpectedMultipartFieldsBeforeCallingTheService() {
        ICreationAssetService service = mock(ICreationAssetService.class);
        AppLoginHelper loginHelper = mock(AppLoginHelper.class);
        MultipartFile file = mock(MultipartFile.class);
        MultipartHttpServletRequest request = mock(MultipartHttpServletRequest.class);
        when(request.getParameterMap()).thenReturn(Map.of(
            "usageIntent", new String[] {"image"},
            "idempotencyKey", new String[] {"upload-key"},
            "ownerUserId", new String[] {"7"}));
        LinkedMultiValueMap<String, MultipartFile> files = new LinkedMultiValueMap<>();
        files.add("file", file);
        when(request.getMultiFileMap()).thenReturn(files);

        assertThatThrownBy(() -> new CreationAssetController(service, loginHelper).upload(file,
            new UploadCreationAssetBo("image", "upload-key"), request))
            .isInstanceOf(org.dromara.common.core.exception.ServiceException.class);

        verifyNoInteractions(service);
    }

    private void assertPermission(SaCheckPermission permission, String expected) {
        assertThat(permission.type()).isEqualTo("app");
        assertThat(permission.value()).containsExactly(expected);
    }

    private AppLoginHelper loginHelperFor(long actorId) {
        AppLoginHelper loginHelper = mock(AppLoginHelper.class);
        AppPrincipalSnapshotDTO principal = mock(AppPrincipalSnapshotDTO.class);
        when(principal.appUserId()).thenReturn(actorId);
        when(loginHelper.getLoginUser()).thenReturn(new AppLoginUser(principal, "session"));
        return loginHelper;
    }

    private CreationAssetDTO asset() {
        return new CreationAssetDTO("88", "clip.mp4", "video/mp4", "a".repeat(64),
            CreationAssetType.VIDEO, CreationAssetUsageOrigin.UPLOAD, CreationAssetStatus.READY,
            10L, null, null, null, true, true, Instant.EPOCH);
    }

    private static final class CountingHandle implements CreationMediaHandle {
        private final CountingInputStream stream = new CountingInputStream();
        private int closeCalls;

        @Override
        public CreationAssetResolveDTO metadata() {
            return new CreationAssetResolveDTO("88", "video/mp4", "a".repeat(64),
                CreationAssetType.VIDEO, null, 10L, null, null, null, true, true);
        }

        @Override
        public InputStream stream() {
            return stream;
        }

        @Override
        public long offset() {
            return 0L;
        }

        @Override
        public long length() {
            return 3L;
        }

        @Override
        public long totalSize() {
            return 3L;
        }

        @Override
        public void close() throws IOException {
            closeCalls++;
            stream.close();
        }
    }

    private static final class CountingInputStream extends ByteArrayInputStream {
        private int closeCalls;

        private CountingInputStream() {
            super(new byte[] {3, 4, 5});
        }

        @Override
        public void close() throws IOException {
            closeCalls++;
            super.close();
        }
    }
}
