package org.dromara.aivideo.identity.http;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarFile;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 外部双启动器 HTTP 测试的打包产物检查。
 *
 * <p>此测试不连接本机数据服务；它只确认 Failsafe 传入绝对目录，且两个 starter jar 已准备为
 * 独立外部 JVM 进程的启动输入。</p>
 */
@Tag("dev")
class ExternalStarterJarAssemblyIT {

    private static final String STARTER_JAR_DIRECTORY_PROPERTY = "it.starter.jar.directory";
    private static final String CREATOR_RUNTIME_MARKER = "META-INF/aivideo-creator-runtime.marker";

    @Test
    void starterJarsArePreparedInAnAbsoluteDirectoryForSeparateExternalProcesses() {
        Path starterJarDirectory = starterJarDirectory();

        assertThat(starterJarDirectory.resolve("ai-video-user-api.jar"))
            .as("创作端 starter 必须作为外部进程 jar 准备好")
            .matches(Files::isRegularFile);
        assertThat(starterJarDirectory.resolve("ruoyi-admin.jar"))
            .as("运营端 starter 必须作为外部进程 jar 准备好")
            .matches(Files::isRegularFile);
    }

    @Test
    void creatorRuntimeMarkerIsPackagedOnlyInTheCreatorStarter() throws IOException {
        Path starterJarDirectory = starterJarDirectory();

        assertThat(hasEntry(starterJarDirectory.resolve("ai-video-user-api.jar"), CREATOR_RUNTIME_MARKER))
            .as("创作端 starter 必须包含运行时安全标记")
            .isTrue();
        assertThat(hasEntry(starterJarDirectory.resolve("ruoyi-admin.jar"), CREATOR_RUNTIME_MARKER))
            .as("运营端 starter 不得包含创作端运行时安全标记")
            .isFalse();
    }

    private static Path starterJarDirectory() {
        String configuredDirectory = System.getProperty(STARTER_JAR_DIRECTORY_PROPERTY);
        assertThat(configuredDirectory)
            .as("Failsafe 必须传入 starter jar 的绝对目录")
            .isNotBlank();

        Path path = Path.of(configuredDirectory);
        assertThat(path)
            .as("Failsafe starter jar 目录必须是绝对 target 目录")
            .isAbsolute();
        return path.normalize();
    }

    private static boolean hasEntry(Path jarPath, String entryName) throws IOException {
        try (JarFile jarFile = new JarFile(jarPath.toFile())) {
            return jarFile.getJarEntry(entryName) != null;
        }
    }
}
