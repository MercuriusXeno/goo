package com.mercuriusxeno.goo.workbench;

import java.io.IOException;

/**
 * Writes the inventory the workbench serves when none stands (decision inventory-dump-from-a-gametest-and-a-command).
 * The gradle run supplies one spawning the gametest server (decision gradle-run-stays-on-the-gametest-dump).
 */
@FunctionalInterface
public interface InventoryDump {

    /**
     * Writes the inventory file, and on a client the slot captures its image folder lacks.
     *
     * @throws IOException          where the dump cannot run
     * @throws InterruptedException where the wait for it is interrupted
     */
    void run() throws IOException, InterruptedException;
}
