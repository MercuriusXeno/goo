package com.mercuriusxeno.goo.workbench;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * Runs the audit and clique dumps, with no dev instance launched for them (decision workbench-reruns-the-audit-headless).
 * InGameDumps supplies the one runner, in-process on the running server
 * (decision hosted-workbench-and-image-captures-leave-tools).
 */
@FunctionalInterface
public interface DumpRunner {

    /**
     * Runs the dumps over the draft rather than the live file, so the audit reads what the manager holds
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
