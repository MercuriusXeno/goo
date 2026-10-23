package com.mercuriusxeno.goo.tools.workbench;

import com.mercuriusxeno.goo.workbench.InventoryDump;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Runs the goo:inventory_dump gametest headless through the project's gradle wrapper
 * (decision gradle-run-stays-on-the-gametest-dump).
 */
public final class HeadlessGametestInventoryDump implements InventoryDump {
    private final Path projectDir;

    /**
     * @param projectDir the repository root holding the gradle wrapper
     */
    public HeadlessGametestInventoryDump(Path projectDir) {
        this.projectDir = projectDir;
    }

    @Override
    public void run() throws IOException, InterruptedException {
        Process process = new ProcessBuilder(HeadlessGametestDumpRunner.gradleWrapper(projectDir), "runGameTestServer",
                "-PgameTestSelect=goo:inventory_dump")
                .directory(projectDir.toFile()).inheritIO().start();
        int exit = process.waitFor();
        if (exit != 0) {
            throw new IOException("The inventory dump gametest exited with " + exit);
        }
    }
}
