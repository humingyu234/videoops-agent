package org.dromara.aivideo.infra.timeline;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 * 时间轴媒体基础设施的部署期安全配置。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "aivideo.timeline")
public class TimelineInfrastructureProperties {

    private static final int MIN_CONCURRENCY_LIMIT = 1;
    private static final int MAX_CONCURRENCY_LIMIT = 100;
    private static final int MAX_WORKER_ID_LENGTH = 128;
    private static final long MAX_OUTPUT_BYTES = 2L * 1024L * 1024L * 1024L;
    private static final long MAX_AI_RESPONSE_BYTES = 1024L * 1024L;
    private static final List<String> REQUIRED_FONT_FILES = List.of(
        "NotoSansCJKsc-Regular.otf", "NotoSerifCJKsc-Regular.otf");

    /** 默认禁用，避免未配置媒体能力时产生假成功。 */
    private boolean enabled;
    private String ffmpegPath;
    private String ffprobePath;
    private String workRoot;
    private String fontRoot;
    private Duration processTimeout = Duration.ofMinutes(5);
    private long maxOutputBytes = 512L * 1024L * 1024L;
    private int perUserConcurrencyLimit = MIN_CONCURRENCY_LIMIT;
    private int systemConcurrencyLimit = MIN_CONCURRENCY_LIMIT;
    private String workerId = "timeline-worker";
    private Duration pollDelay = Duration.ofSeconds(1);
    private int recoveryBatchLimit = 100;
    private Ai ai = new Ai();

    /**
     * 在启用真实媒体能力时验证所有运行时依赖；禁用态保留 fail-closed 回退 Bean。
     */
    @PostConstruct
    public void validateOnStartup() {
        validate();
    }

    /**
     * 校验已绑定的配置并在不安全时阻止真实媒体 Bean 装配。
     */
    public void validate() {
        if (!enabled) {
            return;
        }
        validateExecutable("ffmpegPath", ffmpegPath);
        validateExecutable("ffprobePath", ffprobePath);
        validateWritableDirectory("workRoot", workRoot);
        Path validatedFontRoot = validateReadableDirectory("fontRoot", fontRoot);
        for (String fileName : REQUIRED_FONT_FILES) {
            Path font = validatedFontRoot.resolve(fileName);
            if (!Files.isRegularFile(font, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(font)) {
                throw invalid("fontRoot must contain " + fileName);
            }
        }
        validatePositiveDuration("processTimeout", processTimeout);
        if (maxOutputBytes <= 0 || maxOutputBytes > MAX_OUTPUT_BYTES) {
            throw invalid("maxOutputBytes must be within 1..2147483648");
        }
        validateConcurrencyLimits();
        if (!StringUtils.hasText(workerId) || !workerId.equals(workerId.trim())
            || workerId.length() > MAX_WORKER_ID_LENGTH) {
            throw invalid("workerId must be 1..128 characters without surrounding whitespace");
        }
        validatePositiveDuration("pollDelay", pollDelay);
        if (recoveryBatchLimit < 1 || recoveryBatchLimit > MAX_CONCURRENCY_LIMIT) {
            throw invalid("recoveryBatchLimit must be within 1..100");
        }
        validateAi();
    }

    /**
     * 返回已校验的 FFmpeg 绝对路径。
     */
    public Path ffmpegBinary() {
        validate();
        return realPath(ffmpegPath, "ffmpegPath");
    }

    /**
     * 返回已校验的 ffprobe 绝对路径。
     */
    public Path ffprobeBinary() {
        validate();
        return realPath(ffprobePath, "ffprobePath");
    }

    /**
     * 返回已校验的媒体临时工作根。
     */
    public Path validatedWorkRoot() {
        validate();
        return realPath(workRoot, "workRoot");
    }

    /**
     * 返回已校验的字体根。
     */
    public Path validatedFontRoot() {
        validate();
        return realPath(fontRoot, "fontRoot");
    }

    private void validateConcurrencyLimits() {
        if (perUserConcurrencyLimit < MIN_CONCURRENCY_LIMIT
            || perUserConcurrencyLimit > MAX_CONCURRENCY_LIMIT) {
            throw invalid("perUserConcurrencyLimit must be within 1..100");
        }
        if (systemConcurrencyLimit < MIN_CONCURRENCY_LIMIT
            || systemConcurrencyLimit > MAX_CONCURRENCY_LIMIT) {
            throw invalid("systemConcurrencyLimit must be within 1..100");
        }
        if (perUserConcurrencyLimit > systemConcurrencyLimit) {
            throw invalid("perUserConcurrencyLimit must not exceed systemConcurrencyLimit");
        }
    }

    private void validateAi() {
        if (ai == null) {
            throw invalid("ai must not be null");
        }
        if (!ai.enabled) {
            return;
        }
        requireText("ai.baseUrl", ai.baseUrl);
        requireText("ai.apiKey", ai.apiKey);
        requireText("ai.model", ai.model);
        validateAiBaseUrl(ai.baseUrl);
        validatePositiveDuration("ai.timeout", ai.timeout);
        if (ai.maxResponseBytes <= 0 || ai.maxResponseBytes > MAX_AI_RESPONSE_BYTES) {
            throw invalid("ai.maxResponseBytes must be within 1..1048576");
        }
    }

    private static void validateAiBaseUrl(String baseUrl) {
        try {
            URI uri = URI.create(baseUrl.trim());
            boolean secure = "https".equalsIgnoreCase(uri.getScheme());
            boolean localHttp = "http".equalsIgnoreCase(uri.getScheme())
                && ("127.0.0.1".equals(uri.getHost()) || "localhost".equalsIgnoreCase(uri.getHost()));
            if ((!secure && !localHttp) || !StringUtils.hasText(uri.getHost()) || uri.getUserInfo() != null
                || uri.getQuery() != null || uri.getFragment() != null) {
                throw invalid("ai.baseUrl must be HTTPS or a loopback HTTP URL");
            }
        } catch (IllegalArgumentException exception) {
            if (exception.getMessage() != null && exception.getMessage().startsWith("ai.baseUrl")) {
                throw exception;
            }
            throw invalid("ai.baseUrl is invalid");
        }
    }

    private static void validateExecutable(String property, String value) {
        Path path = realPath(value, property);
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path)
            || !Files.isExecutable(path)) {
            throw invalid(property + " must be an executable regular file");
        }
    }

    private static Path validateWritableDirectory(String property, String value) {
        Path path = validateReadableDirectory(property, value);
        if (!Files.isWritable(path)) {
            throw invalid(property + " must be writable");
        }
        return path;
    }

    private static Path validateReadableDirectory(String property, String value) {
        Path path = realPath(value, property);
        if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path)) {
            throw invalid(property + " must be a real directory");
        }
        return path;
    }

    private static Path realPath(String value, String property) {
        requireText(property, value);
        try {
            Path configured = Path.of(value.trim());
            if (!configured.isAbsolute()) {
                throw invalid(property + " must be absolute");
            }
            return configured.toRealPath(LinkOption.NOFOLLOW_LINKS);
        } catch (IOException | InvalidPathException exception) {
            throw invalid(property + " does not resolve to a local path");
        }
    }

    private static void validatePositiveDuration(String property, Duration value) {
        if (value == null || value.isNegative() || value.isZero()) {
            throw invalid(property + " must be positive");
        }
    }

    private static void requireText(String property, String value) {
        if (!StringUtils.hasText(value)) {
            throw invalid(property + " must not be blank");
        }
    }

    private static IllegalStateException invalid(String message) {
        return new IllegalStateException(message);
    }

    /** AI 建议供应商的受控配置。 */
    @Getter
    @Setter
    public static class Ai {
        private boolean enabled;
        private String baseUrl;
        private String apiKey;
        private String model;
        private Duration timeout = Duration.ofSeconds(30);
        private long maxResponseBytes = 128L * 1024L;
    }
}
