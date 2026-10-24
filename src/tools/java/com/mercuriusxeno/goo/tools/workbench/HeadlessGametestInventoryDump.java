package com.mercuriusxeno.goo.tools.workbench;

import com.mercuriusxeno.goo.workbench.InventoryDump;
import com.mercuriusxeno.goo.workbench.WorkbenchPaths;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Runs the goo:inventory_dump gametest headless through the project's gradle wrapper
 * (decision gradle-run-stays-on-the-gametest-dump), then captures each item the manifest lacks in a client run.
 * decision images-are-slot-captures-taken-on-detection
 */
public final class HeadlessGametestInventoryDump implements InventoryDump {
    private final Path projectDir;
    private final SlotCaptureStep captures;

    /**
     * @param projectDir the repository root holding the gradle wrapper
     * @param paths      the workbench's files, whose inventory and image folder the capture step reads
     */
    public HeadlessGametestInventoryDump(Path projectDir, WorkbenchPaths paths) {
        this.projectDir = projectDir;
        this.captures = SlotCaptureStep.inheritingConsole(projectDir, paths);
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
        captures.captureMissing();
    }

    /**
     * Captures each item a standing inventory lists that the manifest lacks, before the workbench serves, since the
     * dump itself runs only where no inventory stands.
     *
     * @throws IOException          where the files cannot be read or the client fails
     * @throws InterruptedException where the wait on the client is interrupted
     */
    public void captureMissing() throws IOException, InterruptedException {
        captures.captureMissing();
    }
}
