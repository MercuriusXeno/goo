package com.mercuriusxeno.goo.tools.workbench;

import com.mercuriusxeno.goo.workbench.DumpRunner;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * The gradle run of the goo:values_dumps gametest, held while it runs so Stop can end it
 * (decision topbar-stop-ends-every-workbench-process). Only a repository checkout can run it
 * (decision gradle-run-stays-on-the-gametest-dump).
 */
public final class HeadlessGametestDumpRunner implements DumpRunner {
    private final Path projectDir;
    private final AtomicReference<Process> running = new AtomicReference<>();

    /**
     * @param projectDir the repository root holding the gradle wrapper
     */
    public HeadlessGametestDumpRunner(Path projectDir) {
        this.projectDir = projectDir;
    }

    /**
     * @param projectDir the repository root
     * @return the path of the project's gradle wrapper script for the running platform
     */
    static String gradleWrapper(Path projectDir) {
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        return projectDir.resolve(windows ? "gradlew.bat" : "gradlew").toString();
    }

    @Override
    public int run(Consumer<String> progress) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(gradleWrapper(projectDir), "runGameTestServer",
                "-PgameTestSelect=goo:values_dumps", "--stacktrace")
                .directory(projectDir.toFile()).redirectErrorStream(true).start();
        running.set(process);
        try (BufferedReader output = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            output.lines().forEach(progress);
        } finally {
            running.compareAndSet(process, null);
        }
        return process.waitFor();
    }

    /**
     * Ends the run and every process beneath it: the wrapper starts the gradle client, which starts the
     * game server, and each would outlive the wrapper alone.
     */
    @Override
    public void stop() {
        Process process = running.getAndSet(null);
        if (process == null) {
            return;
        }
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
    }
}
