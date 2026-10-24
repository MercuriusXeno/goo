package com.mercuriusxeno.goo.tools.workbench;

import com.mercuriusxeno.goo.workbench.CaptureManifest;
import com.mercuriusxeno.goo.workbench.WorkbenchPaths;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

/**
 * The gradle workbench's capture step: compares the inventory's items against the manifest beside the image folder
 * and, only where any lack a capture, starts gradlew captureInventory and waits for it, so the page serves each item
 * as its slot draws it. Where none lack one, no client starts.
 * decision images-are-slot-captures-taken-on-detection
 */
final class SlotCaptureStep {
    private static final String CAPTURE_TASK = "captureInventory";
    private static final String IMAGES_PROPERTY = "-PinventoryImages=";
    private static final String CAPTURING = "capturing ";
    private static final String ITEMS = " items";
    private static final String CAPTURE_EXITED = "The inventory capture client exited with ";

    private final Path projectDir;
    private final WorkbenchPaths paths;
    private final ProcessRunner runner;
    private final Consumer<String> console;

    /**
     * Runs a command to its end and answers its exit code.
     */
    @FunctionalInterface
    interface ProcessRunner {
        /**
         * @param command the command line
         * @return the process's exit code
         * @throws IOException          where the process cannot start
         * @throws InterruptedException where the wait for it is interrupted
         */
        int run(List<String> command) throws IOException, InterruptedException;
    }

    /**
     * @param projectDir the repository root holding the gradle wrapper
     * @param paths      the workbench's files, whose inventory and image folder the step reads
     * @param runner     starts the capture client and waits for it
     * @param console    takes the line naming how many items it captures
     */
    SlotCaptureStep(Path projectDir, WorkbenchPaths paths, ProcessRunner runner, Consumer<String> console) {
        this.projectDir = projectDir;
        this.paths = paths;
        this.runner = runner;
        this.console = console;
    }

    /**
     * @param projectDir the repository root
     * @param paths      the workbench's files
     * @return a step whose capture client shares this JVM's console, as the gametest dump does
     */
    static SlotCaptureStep inheritingConsole(Path projectDir, WorkbenchPaths paths) {
        return new SlotCaptureStep(projectDir, paths,
                command -> new ProcessBuilder(command).directory(projectDir.toFile()).inheritIO().start().waitFor(),
                System.out::println);
    }

    /**
     * Captures each item the inventory lists that the manifest lacks, or does nothing where none lacks one or no
     * inventory stands.
     *
     * @throws IOException          where the files cannot be read or the client fails
     * @throws InterruptedException where the wait on the client is interrupted
     */
    void captureMissing() throws IOException, InterruptedException {
        if (!Files.isRegularFile(paths.inventoryFile())) {
            return;
        }
        List<String> missing = CaptureManifest.read(CaptureManifest.fileBeside(paths.imageFolder()))
                .missingFrom(CaptureManifest.itemIdsOf(Files.readString(paths.inventoryFile(), StandardCharsets.UTF_8)));
        if (missing.isEmpty()) {
            return;
        }
        console.accept(CAPTURING + missing.size() + ITEMS);
        int exit = runner.run(command());
        if (exit != 0) {
            throw new IOException(CAPTURE_EXITED + exit);
        }
    }

    private List<String> command() {
        return List.of(HeadlessGametestDumpRunner.gradleWrapper(projectDir), CAPTURE_TASK,
                IMAGES_PROPERTY + paths.imageFolder());
    }
}
