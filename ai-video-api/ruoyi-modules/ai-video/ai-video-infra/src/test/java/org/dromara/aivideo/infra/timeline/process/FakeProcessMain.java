package org.dromara.aivideo.infra.timeline.process;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Cross-platform child process used by timeline process-executor tests.
 */
public final class FakeProcessMain {

    private FakeProcessMain() {
    }

    public static void main(String[] args) throws Exception {
        String mode = args[0];
        switch (mode) {
            case "argument" -> System.out.print(args[1]);
            case "stdin-eof" -> System.out.print(System.in.read());
            case "stdout" -> write(System.out, Integer.parseInt(args[1]));
            case "stderr" -> write(System.err, Integer.parseInt(args[1]));
            case "exit" -> System.exit(Integer.parseInt(args[1]));
            case "ready-sleep" -> readyThenSleep(Path.of(args[1]), Long.parseLong(args[2]));
            case "spawn-child" -> spawnChild(Path.of(args[1]), Path.of(args[2]));
            case "environment" -> System.out.print(System.getenv(args[1]));
            default -> throw new IllegalArgumentException("unsupported fake process mode");
        }
    }

    private static void write(java.io.PrintStream stream, int bytes) {
        byte[] payload = new byte[bytes];
        java.util.Arrays.fill(payload, (byte) 'x');
        stream.write(payload, 0, payload.length);
        stream.flush();
    }

    private static void readyThenSleep(Path readyFile, long sleepMillis) throws Exception {
        Files.writeString(readyFile, "ready", StandardCharsets.UTF_8);
        Thread.sleep(sleepMillis);
    }

    private static void spawnChild(Path childReadyFile, Path childPidFile) throws Exception {
        Process child = new ProcessBuilder(javaExecutable(), "-cp", System.getProperty("java.class.path"),
            FakeProcessMain.class.getName(), "ready-sleep", childReadyFile.toString(), "10000").start();
        Files.writeString(childPidFile, Long.toString(child.pid()), StandardCharsets.UTF_8);
        Thread.sleep(10000);
    }

    private static String javaExecutable() {
        String executable = System.getProperty("os.name", "").toLowerCase().contains("win") ? "java.exe" : "java";
        return Path.of(System.getProperty("java.home"), "bin", executable).toString();
    }
}
