package org.dromara.aivideo.timeline;

import com.fasterxml.jackson.annotation.JsonIgnoreType;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("dev")
class TimelineServiceBoundaryTest {

    private static final String CREATION = "org.dromara.aivideo.creation.service.";
    private static final String TIMELINE = "org.dromara.aivideo.timeline.service.";
    private static final String TASK = "org.dromara.aivideo.task.service.";

    @Test
    void frozenInterfacesExposeOnlyExactMethods() throws Exception {
        assertMethods(CREATION + "CreationMediaHandle",
            "close():void", "length():long", "metadata():org.dromara.aivideo.creation.dto.CreationAssetResolveDTO",
            "offset():long", "stream():java.io.InputStream", "totalSize():long");
        assertMethods(TIMELINE + "TimelineRenderOutputHandle",
            "close():void", "metadata():org.dromara.aivideo.timeline.dto.TimelineRenderResultDTO",
            "stream():java.io.InputStream");
        assertMethods(TIMELINE + "TimelineTaskProgressListener",
            "onProgress(org.dromara.aivideo.timeline.dto.TimelineProgressDTO):void");
        assertMethods(CREATION + "ICreationAssetService",
            "assertAssetDeletable(long,java.lang.String):void",
            "deleteOwned(long,java.lang.String):void",
            "findCompensatablePending(java.time.Instant,int):java.util.List<org.dromara.aivideo.creation.dto.PendingRenderOutputDTO>",
            "getOwned(long,java.lang.String):org.dromara.aivideo.creation.dto.CreationAssetDTO",
            "getOwnedTimelineRenderOutput(long,java.lang.String,java.lang.String):org.dromara.aivideo.creation.dto.CreationAssetDTO",
            "markPendingRenderFailed(long,org.dromara.aivideo.creation.dto.RenderOutputFailureDTO):void",
            "openOwnedMedia(long,java.lang.String,org.dromara.aivideo.timeline.enums.TimelineAssetUsageType):org.dromara.aivideo.creation.service.CreationMediaHandle",
            "openOwnedMediaRange(long,java.lang.String,java.lang.String):org.dromara.aivideo.creation.service.CreationMediaHandle",
            "pageOwned(long,org.dromara.aivideo.creation.dto.CreationAssetQueryDTO,org.dromara.common.mybatis.core.page.PageQuery):org.dromara.common.core.domain.PageResult<org.dromara.aivideo.creation.dto.CreationAssetDTO>",
            "registerPendingRenderOutput(long,org.dromara.aivideo.creation.dto.RegisterPendingRenderOutputDTO):org.dromara.aivideo.creation.dto.PendingRenderOutputDTO",
            "resolveDigitalHumanSource(long,java.lang.String):org.dromara.aivideo.creation.dto.DigitalHumanCreationSourceDTO",
            "resolveOwned(long,java.lang.String,org.dromara.aivideo.timeline.enums.TimelineAssetUsageType):org.dromara.aivideo.creation.dto.CreationAssetResolveDTO",
            "storePendingRenderContent(long,java.lang.String,org.dromara.aivideo.timeline.service.TimelineRenderOutputHandle):org.dromara.aivideo.creation.dto.RenderOutputReadyDTO",
            "uploadOwned(long,org.dromara.aivideo.creation.dto.CreationAssetUploadDTO,java.io.InputStream):org.dromara.aivideo.creation.dto.CreationAssetDTO");
        assertMethods(TIMELINE + "ITimelineMediaRenderService",
            "cancel(java.lang.String,java.lang.String):void",
            "measureText(org.dromara.aivideo.timeline.dto.TimelineTextMeasureCommandDTO):org.dromara.aivideo.timeline.dto.TimelineTextMeasureResultDTO",
            "probe(org.dromara.aivideo.creation.service.CreationMediaHandle):org.dromara.aivideo.timeline.dto.TimelineMediaProbeDTO",
            "render(org.dromara.aivideo.timeline.dto.TimelineRenderCommandDTO,java.util.List<org.dromara.aivideo.creation.service.CreationMediaHandle>,org.dromara.aivideo.timeline.service.TimelineTaskProgressListener,java.util.function.BooleanSupplier):org.dromara.aivideo.timeline.service.TimelineRenderOutputHandle");
        assertMethods(TIMELINE + "ITimelineAiSuggestionService",
            "alignFromAudio(org.dromara.aivideo.timeline.dto.TimelineSubtitleAlignmentCommandDTO,org.dromara.aivideo.creation.service.CreationMediaHandle,org.dromara.aivideo.timeline.service.TimelineTaskProgressListener,java.util.function.BooleanSupplier):org.dromara.aivideo.timeline.dto.TimelineSubtitleAlignmentResultDTO",
            "alignFromTrustedCues(org.dromara.aivideo.timeline.dto.TimelineSubtitleAlignmentCommandDTO,org.dromara.aivideo.timeline.service.TimelineTaskProgressListener,java.util.function.BooleanSupplier):org.dromara.aivideo.timeline.dto.TimelineSubtitleAlignmentResultDTO",
            "generateImagePrompt(org.dromara.aivideo.timeline.dto.TimelineImagePromptCommandDTO,org.dromara.aivideo.timeline.service.TimelineTaskProgressListener,java.util.function.BooleanSupplier):org.dromara.aivideo.timeline.dto.TimelineImagePromptResultDTO",
            "suggestFancyText(org.dromara.aivideo.timeline.dto.TimelineFancyTextSuggestionCommandDTO,org.dromara.aivideo.timeline.service.TimelineTaskProgressListener,java.util.function.BooleanSupplier):org.dromara.aivideo.timeline.dto.TimelineFancyTextSuggestionResultDTO");
        assertMethods(TASK + "IAiTaskService",
            "claimNextWorkflow(java.lang.String,int):org.dromara.aivideo.task.dto.AiTaskLeaseDTO",
            "compensatePendingOutputs(java.time.Instant,int):int",
            "createFreeTask(long,org.dromara.aivideo.task.dto.CreateFreeAiTaskDTO):org.dromara.aivideo.task.dto.AiTaskDTO",
            "createWorkflowTask(org.dromara.aivideo.task.dto.AiTaskActorDTO,org.dromara.aivideo.task.dto.CreateWorkflowAiTaskDTO):org.dromara.aivideo.task.dto.AiTaskDTO",
            "dispatchClaimedWorkflow(org.dromara.aivideo.task.dto.AiTaskLeaseDTO):org.dromara.aivideo.task.dto.AiTaskDispatchResultDTO",
            "dispatchNext(java.lang.String,int,int):org.dromara.aivideo.task.dto.AiTaskDispatchResultDTO",
            "getOwned(long,java.lang.String):org.dromara.aivideo.task.dto.AiTaskDTO",
            "getOwned(org.dromara.aivideo.task.dto.AiTaskAccessScopeDTO,java.lang.String):org.dromara.aivideo.task.dto.AiTaskDTO",
            "pageOwned(long,org.dromara.aivideo.task.dto.AiTaskQueryDTO,org.dromara.common.mybatis.core.page.PageQuery):org.dromara.common.core.domain.PageResult<org.dromara.aivideo.task.dto.AiTaskSummaryDTO>",
            "pageOwned(org.dromara.aivideo.task.dto.AiTaskAccessScopeDTO,org.dromara.aivideo.task.dto.AiTaskQueryDTO,org.dromara.common.mybatis.core.page.PageQuery):org.dromara.common.core.domain.PageResult<org.dromara.aivideo.task.dto.AiTaskSummaryDTO>",
            "recoverExpired(java.time.Instant,int):int",
            "releaseClaimedWorkflow(org.dromara.aivideo.task.dto.AiTaskLeaseDTO):boolean",
            "replayTimelineRender(long,java.lang.String,java.lang.String,java.lang.String,java.lang.String):java.util.Optional<org.dromara.aivideo.task.dto.AiTaskDTO>",
            "requestCancellation(long,java.lang.String,java.lang.String):org.dromara.aivideo.task.dto.AiTaskDTO",
            "requestCancellation(org.dromara.aivideo.task.dto.AiTaskAccessScopeDTO,java.lang.String,java.lang.String):org.dromara.aivideo.task.dto.AiTaskDTO",
            "retryOwned(long,org.dromara.aivideo.task.dto.RetryAiTaskDTO):org.dromara.aivideo.task.dto.AiTaskDTO");
    }

    @Test
    void handlesAndPublicSignaturesAreControlled() throws Exception {
        for (String name : List.of(CREATION + "CreationMediaHandle", TIMELINE + "TimelineRenderOutputHandle")) {
            Class<?> type = Class.forName(name);
            assertTrue(type.isInterface());
            assertTrue(AutoCloseable.class.isAssignableFrom(type));
            assertNotNull(type.getAnnotation(JsonIgnoreType.class));
            assertEquals(List.of("java.io.IOException"), Arrays.stream(type.getMethod("close").getExceptionTypes())
                .map(Class::getName).toList());
        }
        for (String name : List.of(CREATION + "ICreationAssetService", TIMELINE + "ITimelineMediaRenderService",
            TIMELINE + "ITimelineAiSuggestionService", TASK + "IAiTaskService")) {
            for (Method method : Class.forName(name).getMethods()) {
                String signature = signature(method);
                assertFalse(signature.matches(".*(java\\.nio\\.file\\.Path|java\\.io\\.File|java\\.net\\.(URI|URL)|MultipartFile|RestClient|\\.infra\\.).*"), signature);
            }
        }
        assertNotNull(Class.forName(TIMELINE + "TimelineTaskProgressListener")
            .getAnnotation(FunctionalInterface.class));
    }

    @Test
    void executionFailureCodesAndSafeExceptionAreFrozen() throws Exception {
        Class<?> codeType = Class.forName("org.dromara.aivideo.timeline.enums.TimelineExecutionFailureCode");
        assertEquals(List.of("CAPABILITY_UNAVAILABLE", "INPUT_INVALID", "INPUT_UNAVAILABLE", "FONT_UNAVAILABLE",
                "TIMEOUT", "PROCESS_FAILED", "OUTPUT_INVALID", "REMOTE_FAILURE", "RESPONSE_TOO_LARGE",
                "RESPONSE_INVALID", "CALLBACK_FAILED"),
            Arrays.stream(codeType.getEnumConstants()).map(Object::toString).toList());
        Class<?> type = Class.forName("org.dromara.aivideo.timeline.exception.TimelineExecutionException");
        assertTrue(Modifier.isFinal(type.getModifiers()));
        Object code = codeType.getEnumConstants()[0];
        Throwable cause = new IllegalStateException("provider-secret");
        Object exception = type.getConstructor(String.class, codeType, boolean.class, Throwable.class)
            .newInstance("safe", code, true, cause);
        assertEquals(code, type.getMethod("code").invoke(exception));
        assertEquals(true, type.getMethod("retryable").invoke(exception));
        assertFalse(exception.toString().contains("provider-secret"));
        assertConstructorRejected(type, codeType, null, code);
        assertConstructorRejected(type, codeType, " ", code);
        assertConstructorRejected(type, codeType, "安".repeat(513), code);
        InvocationTargetException nullCode = assertThrows(InvocationTargetException.class,
            () -> type.getConstructor(String.class, codeType, boolean.class, Throwable.class)
                .newInstance("safe", null, false, null));
        assertInstanceOf(NullPointerException.class, nullCode.getCause());
    }

    @Test
    void whisperKeepsLegacyMethodAndAddsFailClosedDefaultOverload() throws Exception {
        Class<?> serviceType = Class.forName("org.dromara.aivideo.voice.service.IWhisperTranscriptionService");
        assertEquals(2, serviceType.getDeclaredMethods().length);
        Method overload = serviceType.getMethod("transcribe",
            Class.forName("org.dromara.aivideo.voice.dto.WhisperTranscriptionInputDTO"), java.io.InputStream.class);
        assertTrue(overload.isDefault());
        Object proxy = Proxy.newProxyInstance(serviceType.getClassLoader(), new Class<?>[]{serviceType},
            (instance, method, args) -> method.isDefault()
                ? InvocationHandler.invokeDefault(instance, method, args) : null);
        InvocationTargetException failure = assertThrows(InvocationTargetException.class,
            () -> overload.invoke(proxy, new Object[]{null, new ByteArrayInputStream(new byte[0])}));
        assertInstanceOf(Class.forName("org.dromara.aivideo.timeline.exception.TimelineExecutionException"),
            failure.getCause());
    }

    private static void assertMethods(String className, String... expected) throws Exception {
        Class<?> type = Class.forName(className);
        assertTrue(type.isInterface(), className);
        assertEquals(Arrays.stream(expected).sorted().toList(),
            Arrays.stream(type.getDeclaredMethods()).map(TimelineServiceBoundaryTest::signature).sorted().toList());
    }

    private static String signature(Method method) {
        String parameters = Arrays.stream(method.getGenericParameterTypes())
            .map(java.lang.reflect.Type::getTypeName).reduce((left, right) -> left + "," + right).orElse("");
        return method.getName() + "(" + parameters + "):" + method.getGenericReturnType().getTypeName();
    }

    private static void assertConstructorRejected(Class<?> type, Class<?> codeType,
                                                  String message, Object code) throws Exception {
        InvocationTargetException failure = assertThrows(InvocationTargetException.class,
            () -> type.getConstructor(String.class, codeType, boolean.class, Throwable.class)
                .newInstance(message, code, false, null));
        assertInstanceOf(IllegalArgumentException.class, failure.getCause());
    }
}
