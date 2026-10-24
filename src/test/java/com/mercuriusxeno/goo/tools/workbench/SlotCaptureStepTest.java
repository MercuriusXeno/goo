package com.mercuriusxeno.goo.tools.workbench;

import com.mercuriusxeno.goo.workbench.CaptureManifest;
import com.mercuriusxeno.goo.workbench.WorkbenchPaths;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that the gradle workbench starts the client capture only when the manifest lacks an item the inventory
 * lists, through a fake process runner (decision images-are-slot-captures-taken-on-detection). The inventory and
 * manifest are files the step reads, so this test writes them under a temp folder.
 */
class SlotCaptureStepTest {

    private static final String INVENTORY = """
            {"items": [{"id": "minecraft:chest", "name": "Chest"}, {"id": "minecraft:oak_planks", "name": "Oak Planks"}]}
            """;
    private static final Instant WHEN = Instant.parse("2026-10-23T12:00:00Z");

    @TempDir
    Path temp;

    private WorkbenchPaths paths;
    private final List<List<String>> commandsRun = new ArrayList<>();

    @BeforeEach
    void writeInventory() throws IOException {
        paths = WorkbenchPaths.inProject(temp);
        Files.createDirectories(paths.inventoryFile().getParent());
        Files.writeString(paths.inventoryFile(), INVENTORY);
    }

    private SlotCaptureStep step() {
        return new SlotCaptureStep(temp, paths, command -> {
            commandsRun.add(command);
            return 0;
        }, line -> { });
    }

    /**
     * An inventory whose every item the manifest names starts no client.
     */
    @Test
    void coveredInventoryStartsNoProcess() throws IOException, InterruptedException {
        CaptureManifest.EMPTY.withCapture("minecraft:chest", WHEN).withCapture("minecraft:oak_planks", WHEN)
                .write(CaptureManifest.fileBeside(paths.imageFolder()));
        step().captureMissing();
        assertEquals(List.of(), commandsRun);
    }

    /**
     * An item the manifest lacks starts the captureInventory command once, naming the image folder, and the step
     * returns only after the runner answered.
     */
    @Test
    void uncapturedItemStartsTheCaptureOnce() throws IOException, InterruptedException {
        CaptureManifest.EMPTY.withCapture("minecraft:chest", WHEN)
                .write(CaptureManifest.fileBeside(paths.imageFolder()));
        step().captureMissing();
        assertEquals(1, commandsRun.size());
        List<String> command = commandsRun.getFirst();
        assertTrue(command.contains("captureInventory"), command.toString());
        assertTrue(command.contains("-PinventoryImages=" + paths.imageFolder()), command.toString());
    }

    /**
     * A capture client exiting nonzero fails the step, so the workbench reports it rather than serving silently.
     */
    @Test
    void failedCaptureThrows() {
        SlotCaptureStep failing = new SlotCaptureStep(temp, paths, command -> 1, line -> { });
        assertThrows(IOException.class, failing::captureMissing);
    }
}
