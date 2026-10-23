package com.mercuriusxeno.goo.tools.workbench;

import com.mercuriusxeno.goo.workbench.DumpRunner;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * The gradle run of the goo:values_dumps gametest, held while it runs so Stop can end it
 * (decision topbar-stop-ends-every-workbench-process). Only a repository checkout can run it
 * (decision gradle-run-stays-on-the-gametest-dump).
 */
public final class HeadlessGametestDumpRunner implements DumpRunner {
    private static final String PACKS_ROOT = "runs/gameTestServer/workbench_packs";
    private static final String DRAFT_PACK = "goo_values_draft";
    private static final String PACK_VALUES = "data/goo/goo_values/base_values.json";
    private static final String PACK_MCMETA = "pack.mcmeta";
    // The format the gametest folder packs under src/test/resources/datapacks declare.
    private static final String PACK_MCMETA_TEXT = """
            {
              "pack": {
                "description": "The values workbench's draft base_values.json, loaded over the jar's for the dump run",
                "min_format": 101,
                "max_format": 101
              }
            }
            """;
    private static final String PACKS_PROPERTY = "-PgameTestPacks=";

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

    /**
     * Writes the draft into a folder datapack, so the gametest server loads it over the jar's file and the
     * audit reads what the page holds (decision workbench-authors-a-draft-and-promotes-by-rename).
     *
     * @param draft the draft base_values file
     * @return the packs root the gametest server's --packs names, holding the draft pack alone
     * @throws IOException where the pack cannot be written
     */
    Path writeDraftPacks(Path draft) throws IOException {
        Path packs = projectDir.resolve(PACKS_ROOT);
        Path pack = packs.resolve(DRAFT_PACK);
        Path values = pack.resolve(PACK_VALUES);
        Files.createDirectories(values.getParent());
        Files.writeString(pack.resolve(PACK_MCMETA), PACK_MCMETA_TEXT);
        Files.copy(draft, values, StandardCopyOption.REPLACE_EXISTING);
        return packs;
    }

    /**
     * @param packs the packs root the run loads
     * @return the gradlew command running the values dumps over those packs
     */
    List<String> command(Path packs) {
        return List.of(gradleWrapper(projectDir), "runGameTestServer", "-PgameTestSelect=goo:values_dumps",
                PACKS_PROPERTY + packs, "--stacktrace");
    }

    @Override
    public int run(Path draft, Consumer<String> progress) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command(writeDraftPacks(draft)))
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
