package org.dromara.aivideo.identity.http;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 外部 starter 启动失败时的进程回收测试，不依赖业务启动器或本机数据服务。
 */
@Tag("dev")
class ExternalStarterProcessCleanupIT {

    private static final String DATABASE_PASSWORD_ENV = "AI_VIDEO_IT_MYSQL_PASSWORD";
    private static final String DATABASE_PASSWORD = "mysql-password-must-not-appear-in-report";
    private static final String DATASOURCE_URL_ENV = "SPRING_DATASOURCE_DYNAMIC_DATASOURCE_MASTER_URL";
    private static final String DATASOURCE_URL =
        "jdbc:mysql://localhost:3306/ai_video_test?marker=url-must-not-appear-in-report";
    private static final String STANDARD_DATASOURCE_URL_ENV = "SPRING_DATASOURCE_URL";
    private static final String STANDARD_DATASOURCE_URL =
        "jdbc:mysql://localhost:3306/ai_video_test?marker=standard-url-must-not-appear-in-report";
    private static final String RUN_REDIS_PREFIX = "aivideo:it:00000000-0000-4000-8000-000000000003:";


    @Test
    void destroysTheChildProcessWhenItsHealthProbeNeverBecomesReady() throws Exception {
        Path temporaryDirectory = createTemporaryDirectory();
        Path processIdFile = temporaryDirectory.resolve("child.pid");
        Path neverReadyJar = createNeverReadyStarterJar(temporaryDirectory);
        ProcessBuilder processBuilder = new ProcessBuilder(
            Path.of(System.getProperty("java.home"), "bin", javaExecutableName()).toString(),
            "-jar",
            neverReadyJar.toString(),
            processIdFile.toString()
        );
        processBuilder.redirectErrorStream(true);

        try {
            assertThatThrownBy(() -> DualStarterHttpFixture.ExternalStarterProcess.start(
                "never-ready-starter", processBuilder, Duration.ofSeconds(2)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("健康探针");

            long processId = waitForProcessId(processIdFile);
            waitForProcessToStop(processId);
            assertThat(ProcessHandle.of(processId).map(ProcessHandle::isAlive).orElse(false)).isFalse();
        } finally {
            // 即使待修复实现留下子进程，测试自身也必须回收它，避免污染开发机。
            forceStopRecordedProcess(processIdFile);
            deleteTemporaryDirectory(temporaryDirectory);
        }
    }

    @Test
    void redactsSensitiveChildProcessOutputFromStartupFailure() throws Exception {
        Path temporaryDirectory = createTemporaryDirectory();
        Path secretLeakingJar = createSecretLeakingStarterJar(temporaryDirectory);
        ProcessBuilder processBuilder = new ProcessBuilder(
            Path.of(System.getProperty("java.home"), "bin", javaExecutableName()).toString(),
            "-jar",
            secretLeakingJar.toString()
        );
        processBuilder.redirectErrorStream(true);
        processBuilder.environment().put(DATABASE_PASSWORD_ENV, DATABASE_PASSWORD);
        processBuilder.environment().put(DATASOURCE_URL_ENV, DATASOURCE_URL);
        processBuilder.environment().put(STANDARD_DATASOURCE_URL_ENV, STANDARD_DATASOURCE_URL);

        try {
            assertThatThrownBy(() -> DualStarterHttpFixture.ExternalStarterProcess.start(
                "secret-leaking-starter", processBuilder, Duration.ofSeconds(2)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining(DATABASE_PASSWORD)
                .hasMessageNotContaining(DATASOURCE_URL)
                .hasMessageNotContaining(STANDARD_DATASOURCE_URL)
                .hasMessageContaining("***");
        } finally {
            deleteTemporaryDirectory(temporaryDirectory);
        }
    }

    @Test
    void appliesTheSameRunPrefixToGlobalRedissonAndSaTokenKeys() {
        DualStarterHttpFixture.StarterArguments arguments = new DualStarterHttpFixture.StarterArguments(
            "jdbc:mysql://localhost:3306/ai_video_test",
            "ai_video_test",
            "",
            "127.0.0.1",
            6379,
            15,
            "",
            RUN_REDIS_PREFIX
        );
        ProcessBuilder processBuilder = new ProcessBuilder();

        arguments.applyConnectionEnvironment(processBuilder);

        assertThat(processBuilder.environment().get("SA_TOKEN_REDIS_KEY_PREFIX")).isEqualTo(RUN_REDIS_PREFIX);
        assertThat(processBuilder.environment().get("REDISSON_KEY_PREFIX"))
            .isEqualTo(RUN_REDIS_PREFIX.substring(0, RUN_REDIS_PREFIX.length() - 1));
    }

    @Test
    void parsesTomcatAndJettyStartedPorts() {
        assertThat(DualStarterHttpFixture.ExternalStarterProcess.parseStartedPort(
            "Tomcat started on port 8080 (http) with context path '/'"))
            .isEqualTo(8080);
        assertThat(DualStarterHttpFixture.ExternalStarterProcess.parseStartedPort(
            "Started oejs.ServerConnector@56d9bc5f{HTTP/1.1, (http/1.1)}{0.0.0.0:52728}"))
            .isEqualTo(52728);
        assertThat(DualStarterHttpFixture.ExternalStarterProcess.parseStartedPort(
            "Started DromaraApplication in 10.447 seconds"))
            .isNull();
    }

    @Test
    void closeReapsTheChildEvenWhenTheClosingThreadIsInterrupted() throws Exception {
        Path temporaryDirectory = createTemporaryDirectory();
        Path processIdFile = temporaryDirectory.resolve("ready-child.pid");
        Path readyJar = createReadyStarterJar(temporaryDirectory);
        ProcessBuilder processBuilder = new ProcessBuilder(
            Path.of(System.getProperty("java.home"), "bin", javaExecutableName()).toString(),
            "-jar",
            readyJar.toString(),
            processIdFile.toString()
        );
        processBuilder.redirectErrorStream(true);

        try {
            DualStarterHttpFixture.ExternalStarterProcess starter =
                DualStarterHttpFixture.ExternalStarterProcess.start(
                    "interrupt-close-starter", processBuilder, Duration.ofSeconds(5));
            long processId = waitForProcessId(processIdFile);
            AtomicReference<Throwable> closeFailure = new AtomicReference<>();
            AtomicBoolean interruptRestored = new AtomicBoolean();
            Thread closingThread = new Thread(() -> {
                Thread.currentThread().interrupt();
                try {
                    starter.close();
                } catch (Throwable failure) {
                    closeFailure.set(failure);
                } finally {
                    interruptRestored.set(Thread.interrupted());
                }
            }, "interrupt-close-test");

            closingThread.start();
            closingThread.join(Duration.ofSeconds(15).toMillis());

            assertThat(closingThread.isAlive()).isFalse();
            assertThat(closeFailure.get()).isNull();
            assertThat(interruptRestored).isTrue();
            waitForProcessToStop(processId);
        } finally {
            forceStopRecordedProcess(processIdFile);
            deleteTemporaryDirectory(temporaryDirectory);
        }
    }

    private static Path createNeverReadyStarterJar(Path temporaryDirectory) throws IOException {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, NeverReadyStarter.class.getName());

        Path jar = temporaryDirectory.resolve("never-ready-starter.jar");
        String classResource = NeverReadyStarter.class.getName().replace('.', '/') + ".class";
        try (InputStream classBytes = NeverReadyStarter.class.getClassLoader().getResourceAsStream(classResource);
             JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar), manifest)) {
            if (classBytes == null) {
                throw new IOException("无法读取测试启动器类文件：" + classResource);
            }
            output.putNextEntry(new JarEntry(classResource));
            classBytes.transferTo(output);
            output.closeEntry();
        }
        return jar;
    }

    private static Path createSecretLeakingStarterJar(Path temporaryDirectory) throws IOException {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, SecretLeakingStarter.class.getName());

        Path jar = temporaryDirectory.resolve("secret-leaking-starter.jar");
        String classResource = SecretLeakingStarter.class.getName().replace('.', '/') + ".class";
        try (InputStream classBytes = SecretLeakingStarter.class.getClassLoader().getResourceAsStream(classResource);
             JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar), manifest)) {
            if (classBytes == null) {
                throw new IOException("无法读取测试启动器类文件：" + classResource);
            }
            output.putNextEntry(new JarEntry(classResource));
            classBytes.transferTo(output);
            output.closeEntry();
        }
        return jar;
    }

    private static Path createReadyStarterJar(Path temporaryDirectory) throws IOException {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, ReadyStarter.class.getName());

        Path jar = temporaryDirectory.resolve("ready-starter.jar");
        String classResource = ReadyStarter.class.getName().replace('.', '/') + ".class";
        try (InputStream classBytes = ReadyStarter.class.getClassLoader().getResourceAsStream(classResource);
             JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar), manifest)) {
            if (classBytes == null) {
                throw new IOException("无法读取测试启动器类文件：" + classResource);
            }
            output.putNextEntry(new JarEntry(classResource));
            classBytes.transferTo(output);
            output.closeEntry();
        }
        return jar;
    }

    private static Path createTemporaryDirectory() throws IOException {
        Path buildDirectory = Path.of(System.getProperty("user.dir"), "target");
        Files.createDirectories(buildDirectory);
        return Files.createTempDirectory(buildDirectory, "external-starter-cleanup-");
    }

    private static long waitForProcessId(Path processIdFile) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (System.nanoTime() < deadline) {
            if (Files.isRegularFile(processIdFile)) {
                return Long.parseLong(Files.readString(processIdFile, StandardCharsets.UTF_8).trim());
            }
            Thread.sleep(20);
        }
        throw new IllegalStateException("测试子进程未写入进程号文件：" + processIdFile);
    }

    private static void waitForProcessToStop(long processId) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (System.nanoTime() < deadline) {
            if (!ProcessHandle.of(processId).map(ProcessHandle::isAlive).orElse(false)) {
                return;
            }
            Thread.sleep(20);
        }
        throw new IllegalStateException("启动失败后仍残留外部 starter 进程，PID=" + processId);
    }

    private static void forceStopRecordedProcess(Path processIdFile) {
        try {
            if (!Files.isRegularFile(processIdFile)) {
                return;
            }
            long processId = Long.parseLong(Files.readString(processIdFile, StandardCharsets.UTF_8).trim());
            ProcessHandle.of(processId).filter(ProcessHandle::isAlive).ifPresent(ProcessHandle::destroyForcibly);
            waitForProcessToStop(processId);
        } catch (IOException | NumberFormatException ignored) {
            // 子进程还未成功启动并写入 PID 时不存在可清理目标。
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
        }
    }

    private static void deleteTemporaryDirectory(Path temporaryDirectory) throws IOException, InterruptedException {
        IOException latestFailure = null;
        for (int attempt = 0; attempt < 20; attempt++) {
            try (Stream<Path> paths = Files.walk(temporaryDirectory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
                return;
            } catch (IOException deletionFailure) {
                latestFailure = deletionFailure;
                Thread.sleep(50);
            }
        }
        throw latestFailure;
    }

    private static String javaExecutableName() {
        return System.getProperty("os.name", "").toLowerCase().contains("win") ? "java.exe" : "java";
    }

    /**
     * 仅供本测试动态打包：输出可解析端口后持续存活，确保健康探针必然超时。
     */
    public static final class NeverReadyStarter {

        private NeverReadyStarter() {
        }

        public static void main(String[] args) throws Exception {
            Files.writeString(Path.of(args[0]), Long.toString(ProcessHandle.current().pid()), StandardCharsets.UTF_8);
            // 端口 0 不可能成为可通过 HTTP 健康探针验证的远端服务。
            System.out.println("Tomcat started on port 0");
            System.out.flush();
            Thread.sleep(Duration.ofMinutes(10));
        }
    }

    /**
     * 仅供脱敏回归测试动态打包：将注入的密码原样输出后退出。
     */
    public static final class SecretLeakingStarter {

        private SecretLeakingStarter() {
        }

        public static void main(String[] args) {
            System.out.println("startup failure password=" + System.getenv(DATABASE_PASSWORD_ENV));
            System.out.println("startup failure datasource=" + System.getenv(DATASOURCE_URL_ENV));
            System.out.println("startup failure standard-datasource=" + System.getenv(STANDARD_DATASOURCE_URL_ENV));
            System.out.flush();
        }
    }

    /**
     * 仅供中断回收测试动态打包：启动一个始终返回 404 的 HTTP 服务并持续存活。
     */
    public static final class ReadyStarter {

        private ReadyStarter() {
        }

        public static void main(String[] args) throws Exception {
            com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(
                new java.net.InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                exchange.sendResponseHeaders(404, -1);
                exchange.close();
            });
            server.start();
            Files.writeString(Path.of(args[0]), Long.toString(ProcessHandle.current().pid()), StandardCharsets.UTF_8);
            System.out.println("Tomcat started on port " + server.getAddress().getPort());
            System.out.flush();
            Thread.sleep(Duration.ofMinutes(10));
        }
    }
}
