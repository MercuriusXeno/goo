package com.mercuriusxeno.goo.tools.workbench;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Runs the audit and clique dumps headless, in one server boot (decision workbench-reruns-the-audit-headless).
 */
@FunctionalInterface
public interface DumpRunner {

    /**
     * Runs the dumps.
     *
     * @param progress takes each line the run prints, as it prints it
     * @return the run's exit code, 0 where the dumps were written
     * @throws IOException          where the run cannot start
     * @throws InterruptedException where the wait for it is interrupted
     */
    int run(Consumer<String> progress) throws IOException, InterruptedException;

    /**
     * @param projectDir the repository root
     * @return a runner starting the goo:values_dumps gametest through the project's gradle wrapper
     */
    static DumpRunner headlessGametest(Path projectDir) {
        return progress -> {
            boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
            String wrapper = projectDir.resolve(windows ? "gradlew.bat" : "gradlew").toString();
            Process process = new ProcessBuilder(wrapper, "runGameTestServer", "-PgameTestSelect=goo:values_dumps")
                    .directory(projectDir.toFile()).redirectErrorStream(true).start();
            try (BufferedReader output = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                output.lines().forEach(progress);
            }
            return process.waitFor();
        };
    }
}
