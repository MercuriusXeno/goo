package com.mercuriusxeno.goo.workbench;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * Runs the audit and clique dumps headless, in one server boot (decision workbench-reruns-the-audit-headless).
 * The gradle run supplies one spawning the gametest server (decision gradle-run-stays-on-the-gametest-dump).
 */
@FunctionalInterface
public interface DumpRunner {

    /**
     * Runs the dumps over the draft rather than the live file, so the audit reads what the page holds
     * (decision workbench-authors-a-draft-and-promotes-by-rename).
     *
     * @param draft    the draft base_values file the run loads
     * @param progress takes each line the run prints, as it prints it
     * @return the run's exit code, 0 where the dumps were written
     * @throws IOException          where the run cannot start
     * @throws InterruptedException where the wait for it is interrupted
     */
    int run(Path draft, Consumer<String> progress) throws IOException, InterruptedException;

    /**
     * Ends the run in flight, where one is.
     */
    default void stop() {
    }
}
