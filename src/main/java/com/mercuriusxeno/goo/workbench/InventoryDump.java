package com.mercuriusxeno.goo.workbench;

import java.io.IOException;

/**
 * Writes the inventory the workbench serves when none stands (decision inventory-dump-from-a-gametest-and-a-command).
 * InGameDumps supplies the one dump, from the running server's registries
 * (decision hosted-workbench-and-image-captures-leave-tools).
 */
@FunctionalInterface
public interface InventoryDump {

    /**
     * Writes the inventory file.
     *
     * @throws IOException          where the dump cannot run
     * @throws InterruptedException where the wait for it is interrupted
     */
    void run() throws IOException, InterruptedException;
}
