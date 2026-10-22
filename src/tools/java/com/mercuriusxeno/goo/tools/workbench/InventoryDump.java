package com.mercuriusxeno.goo.tools.workbench;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Writes the inventory the workbench serves when none stands (decision inventory-dump-from-a-gametest-and-a-command).
 */
@FunctionalInterface
public interface InventoryDump {

    /**
     * Writes the inventory file and its image folder.
     *
     * @throws IOException          where the dump cannot run
     * @throws InterruptedException where the wait for it is interrupted
     */
    void run() throws IOException, InterruptedException;

    /**
     * @param projectDir the repository root
     * @return a dump that runs the goo:inventory_dump gametest headless through the project's gradle wrapper
     */
    static InventoryDump headlessGametest(Path projectDir) {
        return () -> {
            boolean windows = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win");
            String wrapper = projectDir.resolve(windows ? "gradlew.bat" : "gradlew").toString();
            Process process = new ProcessBuilder(wrapper, "runGameTestServer", "-PgameTestSelect=goo:inventory_dump")
                    .directory(projectDir.toFile()).inheritIO().start();
            int exit = process.waitFor();
            if (exit != 0) {
                throw new IOException("The inventory dump gametest exited with " + exit);
            }
        };
    }
}
