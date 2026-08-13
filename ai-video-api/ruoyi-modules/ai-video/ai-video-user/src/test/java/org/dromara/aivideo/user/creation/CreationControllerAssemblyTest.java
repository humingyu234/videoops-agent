package org.dromara.aivideo.user.creation;

import cn.dev33.satoken.annotation.SaCheckPermission;
import org.dromara.aivideo.user.creation.controller.CreationAssetController;
import org.dromara.aivideo.user.creation.controller.CreationProjectController;
import org.dromara.aivideo.user.creation.domain.bo.CreationAssetQueryBo;
import org.dromara.aivideo.user.creation.domain.bo.CreateCreationProjectBo;
import org.dromara.aivideo.user.creation.domain.bo.UpdateCreationProjectBo;
import org.dromara.aivideo.user.creation.domain.bo.UploadCreationAssetBo;
import org.dromara.aivideo.user.task.controller.AiTaskController;
import org.dromara.aivideo.user.task.domain.bo.AiTaskQueryBo;
import org.dromara.aivideo.user.task.domain.bo.RetryAiTaskBo;
import org.dromara.aivideo.user.timeline.controller.TimelineDraftController;
import org.dromara.aivideo.user.timeline.controller.TimelineTaskController;
import org.dromara.aivideo.user.timeline.controller.TimelineVersionController;
import org.dromara.aivideo.user.timeline.domain.bo.CreateFancyTextSuggestionTaskBo;
import org.dromara.aivideo.user.timeline.domain.bo.CreateImagePromptTaskBo;
import org.dromara.aivideo.user.timeline.domain.bo.CreateSubtitleAlignmentTaskBo;
import org.dromara.aivideo.user.timeline.domain.bo.CreateTimelineConflictCopyBo;
import org.dromara.aivideo.user.timeline.domain.bo.CreateTimelineRenderTaskBo;
import org.dromara.aivideo.user.timeline.domain.bo.CreateTimelineVersionBo;
import org.dromara.aivideo.user.timeline.domain.bo.RestoreTimelineVersionBo;
import org.dromara.aivideo.user.timeline.domain.bo.SaveTimelineDraftBo;
import org.dromara.aivideo.user.timeline.domain.bo.TimelineVersionQueryBo;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.redis.annotation.RepeatSubmit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** Ensures creation/timeline/task HTTP remains a thin app-only adapter layer. */
@Tag("dev")
class CreationControllerAssemblyTest {

    private static final List<Class<?>> CONTROLLERS = List.of(
        CreationAssetController.class,
        CreationProjectController.class,
        TimelineDraftController.class,
        TimelineVersionController.class,
        TimelineTaskController.class,
        AiTaskController.class
    );

    @Test
    void allCreationTimelineAndTaskControllersAreThinAppOnlyAdapters() throws Exception {
        List<Endpoint> expected = expectedEndpoints();
        List<Method> mapped = new ArrayList<>();

        for (Class<?> controller : CONTROLLERS) {
            assertThat(controller.isAnnotationPresent(RestController.class)).isTrue();
            assertThat(controller.getPackageName()).startsWith("org.dromara.aivideo.user.");
            assertThat(controller.getProtectionDomain().getCodeSource().getLocation().toString())
                .contains("ai-video-user");
            assertThat(controller.isAnnotationPresent(Transactional.class)).isFalse();
            assertNoMapperFields(controller);

            for (Method method : controller.getDeclaredMethods()) {
                if (!isHttpMethod(method)) {
                    continue;
                }
                mapped.add(method);
                Endpoint endpoint = find(expected, method);
                assertThat(endpoint).as("unexpected endpoint %s#%s", controller.getSimpleName(), method.getName())
                    .isNotNull();
                SaCheckPermission permission = method.getAnnotation(SaCheckPermission.class);
                assertThat(permission).isNotNull();
                assertThat(permission.type()).isEqualTo("app");
                assertThat(permission.value()).containsExactly(endpoint.permission());
                assertThat(method.isAnnotationPresent(RepeatSubmit.class)).isFalse();
                assertThat(method.isAnnotationPresent(Transactional.class)).isFalse();

                Log log = method.getAnnotation(Log.class);
                if (isMutation(method)) {
                    if (log != null) {
                        assertThat(log.isSaveRequestData()).isFalse();
                        assertThat(log.isSaveResponseData()).isFalse();
                    }
                } else {
                    assertThat(log).isNull();
                }
            }
        }
        assertThat(mapped).hasSize(expected.size());
    }

    @Test
    void coreModuleContainsNoCreationTimelineOrTaskHttpController() throws IOException {
        Path coreSource = locateApiRoot().resolve("ruoyi-modules/ai-video/ai-video-core/src/main/java");
        try (Stream<Path> sourceFiles = Files.walk(coreSource)) {
            assertThat(sourceFiles.filter(path -> path.getFileName().toString().endsWith("Controller.java"))
                .toList()).isEmpty();
        }
    }

    private List<Endpoint> expectedEndpoints() {
        return List.of(
            endpoint(CreationAssetController.class, "list", "aivideo:creation-asset:query", CreationAssetQueryBo.class),
            endpoint(CreationAssetController.class, "upload", "aivideo:creation-asset:upload", MultipartFile.class,
                UploadCreationAssetBo.class, MultipartHttpServletRequest.class),
            endpoint(CreationAssetController.class, "detail", "aivideo:creation-asset:query", String.class),
            endpoint(CreationAssetController.class, "content", "aivideo:creation-asset:query", String.class, String.class),
            endpoint(CreationAssetController.class, "delete", "aivideo:creation-asset:delete", String.class),
            endpoint(CreationProjectController.class, "create", "aivideo:creation:edit", CreateCreationProjectBo.class),
            endpoint(CreationProjectController.class, "detail", "aivideo:creation:query", String.class),
            endpoint(CreationProjectController.class, "update", "aivideo:creation:edit", String.class,
                UpdateCreationProjectBo.class),
            endpoint(CreationProjectController.class, "latestOutput", "aivideo:creation:query", String.class),
            endpoint(TimelineDraftController.class, "get", "aivideo:creation:query", String.class),
            endpoint(TimelineDraftController.class, "save", "aivideo:creation:edit", String.class, SaveTimelineDraftBo.class),
            endpoint(TimelineVersionController.class, "list", "aivideo:creation:query", String.class,
                TimelineVersionQueryBo.class),
            endpoint(TimelineVersionController.class, "create", "aivideo:creation:edit", String.class,
                CreateTimelineVersionBo.class),
            endpoint(TimelineVersionController.class, "restore", "aivideo:creation:edit", String.class, String.class,
                RestoreTimelineVersionBo.class),
            endpoint(TimelineVersionController.class, "conflictCopy", "aivideo:creation:edit", String.class,
                CreateTimelineConflictCopyBo.class),
            endpoint(TimelineTaskController.class, "createImagePrompt", "aivideo:creation:generate", String.class,
                CreateImagePromptTaskBo.class),
            endpoint(TimelineTaskController.class, "createFancyText", "aivideo:creation:generate", String.class,
                CreateFancyTextSuggestionTaskBo.class),
            endpoint(TimelineTaskController.class, "createSubtitleAlignment", "aivideo:creation:generate", String.class,
                CreateSubtitleAlignmentTaskBo.class),
            endpoint(TimelineTaskController.class, "createRender", "aivideo:creation:generate", String.class,
                CreateTimelineRenderTaskBo.class),
            endpoint(AiTaskController.class, "list", "aivideo:task:query", AiTaskQueryBo.class),
            endpoint(AiTaskController.class, "detail", "aivideo:task:query", String.class),
            endpoint(AiTaskController.class, "cancel", "aivideo:task:cancel", String.class, RetryAiTaskBo.class),
            endpoint(AiTaskController.class, "retry", "aivideo:task:retry", String.class, RetryAiTaskBo.class)
        );
    }

    private Endpoint endpoint(Class<?> controller, String method, String permission, Class<?>... parameterTypes) {
        return new Endpoint(controller, method, List.of(parameterTypes), permission);
    }

    private Endpoint find(List<Endpoint> expected, Method method) {
        return expected.stream()
            .filter(endpoint -> endpoint.controller().equals(method.getDeclaringClass()))
            .filter(endpoint -> endpoint.method().equals(method.getName()))
            .filter(endpoint -> endpoint.parameterTypes().equals(List.of(method.getParameterTypes())))
            .findFirst()
            .orElse(null);
    }

    private boolean isHttpMethod(Method method) {
        return method.isAnnotationPresent(GetMapping.class)
            || method.isAnnotationPresent(PostMapping.class)
            || method.isAnnotationPresent(PutMapping.class)
            || method.isAnnotationPresent(DeleteMapping.class);
    }

    private boolean isMutation(Method method) {
        return method.isAnnotationPresent(PostMapping.class)
            || method.isAnnotationPresent(PutMapping.class)
            || method.isAnnotationPresent(DeleteMapping.class);
    }

    private void assertNoMapperFields(Class<?> controller) {
        assertThat(List.of(controller.getDeclaredFields()).stream().map(Field::getType).map(Class::getSimpleName)
            .filter(name -> name.endsWith("Mapper")).toList()).isEmpty();
    }

    private Path locateApiRoot() {
        for (Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize(); current != null;
             current = current.getParent()) {
            if (Files.isDirectory(current.resolve("ruoyi-modules/ai-video/ai-video-core/src/main/java"))) {
                return current;
            }
        }
        throw new IllegalStateException("Cannot locate ai-video-api root");
    }

    private record Endpoint(Class<?> controller, String method, List<Class<?>> parameterTypes, String permission) {
    }
}
