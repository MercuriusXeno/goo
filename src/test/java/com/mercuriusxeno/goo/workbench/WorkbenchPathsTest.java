package com.mercuriusxeno.goo.workbench;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
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

    /**
     * The dev-client gate's walk from the run directory to the source base_values.json
     * (decision dev-client-workbench-targets-the-source-base-values).
     */
    @Nested
    class Standing {
        private static final String SOURCE_VALUES = "src/main/resources/data/goo/goo_values/base_values.json";

        @TempDir
        Path tree;

        private Path runDirectoryUnderSource() throws IOException {
            Path source = tree.resolve(SOURCE_VALUES);
            Files.createDirectories(source.getParent());
            Files.writeString(source, "{}");
            Path runDirectory = tree.resolve("runs/client");
            Files.createDirectories(runDirectory);
            return runDirectory;
        }

        @Test
        void devClientStandsOnTheSourceFileAboveTheRunDirectory() throws IOException {
            WorkbenchPaths paths = WorkbenchPaths.standing(tree.resolve(WORLD), runDirectoryUnderSource(), true);
            assertEquals(tree.resolve(SOURCE_VALUES), paths.baseValues());
            assertEquals(tree.resolve(SOURCE_VALUES).resolveSibling("base_values.draft.json"), paths.draft());
            assertEquals(WorkbenchPaths.inProject(tree), paths);
        }

        @Test
        void closedGateStandsInTheWorldPack() throws IOException {
            Path world = tree.resolve(WORLD);
            assertEquals(WorkbenchPaths.inWorld(world), WorkbenchPaths.standing(world, runDirectoryUnderSource(), false));
        }

        @Test
        void noSourceFileAboveStandsInTheWorldPack() throws IOException {
            Path runDirectory = tree.resolve("runs/client");
            Files.createDirectories(runDirectory);
            Path world = tree.resolve(WORLD);
            assertEquals(WorkbenchPaths.inWorld(world), WorkbenchPaths.standing(world, runDirectory, true));
        }
    }
}
