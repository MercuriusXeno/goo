package com.mercuriusxeno.goo.workbench;

import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the files the in-game workbench resolves for a world: the live file and draft in the world's goo_values
 * datapack, and the inventory and dumps in a folder beside the datapacks
 * (decision goo-workbench-command-serves-the-page-from-the-game).
 */
class WorkbenchPathsTest {

    private static final Path WORLD = Path.of("saves", "Goo Lab");

    @Test
    void liveFileAndDraftStandInTheWorldsDatapack() {
        WorkbenchPaths paths = WorkbenchPaths.inWorld(WORLD);
        Path values = WORLD.resolve("datapacks/goo_values/data/goo/goo_values");
        assertEquals(values.resolve("base_values.json"), paths.baseValues());
        assertEquals(values.resolve("base_values.draft.json"), paths.draft());
        assertEquals(WORLD.resolve("datapacks/goo_values"), WorkbenchPaths.packRoot(WORLD));
    }

    @Test
    void inventoryAndDumpsStandOutsideTheDatapacks() {
        WorkbenchPaths paths = WorkbenchPaths.inWorld(WORLD);
        Path workbench = WORLD.resolve("goo_workbench");
        for (Path file : new Path[] {paths.inventoryFile(), paths.imageFolder(), paths.auditDump(), paths.cliqueDump(),
                paths.auditJson()}) {
            assertEquals(workbench, file.getParent(), file.toString());
        }
        assertTrue(paths.auditJson().endsWith("goo_audit.json"));
    }
}
