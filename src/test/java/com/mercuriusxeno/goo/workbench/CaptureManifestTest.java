package com.mercuriusxeno.goo.workbench;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests the slot-capture manifest over JSON text: which items the inventory lists that no capture covers, and the
 * inventory's item ids read from its json.
 */
class CaptureManifestTest {

    private static final Instant WHEN = Instant.parse("2026-10-23T12:00:00Z");
    private static final String INVENTORY = """
            {"items": [
              {"id": "minecraft:oak_planks", "name": "Oak Planks"},
              {"id": "minecraft:chest", "name": "Chest"},
              {"id": "goo:crucible", "name": "Crucible"}
            ], "blocks": [{"id": "minecraft:stone", "name": "Stone"}]}
            """;

    @Nested
    class MissingIds {

        /**
         * An item the manifest names is left out; the rest keep the inventory's order.
         */
        @Test
        void answersOnlyTheIdsTheManifestLacks() {
            CaptureManifest manifest = CaptureManifest.EMPTY.withCapture("minecraft:chest", WHEN);
            assertEquals(List.of("minecraft:oak_planks", "goo:crucible"),
                    manifest.missingFrom(CaptureManifest.itemIdsOf(INVENTORY)));
        }

        /**
         * A manifest read from its json covers what it names, so a second run captures nothing it holds.
         */
        @Test
        void aParsedManifestCoversTheIdsItNames() {
            String json = CaptureManifest.EMPTY.withCapture("minecraft:oak_planks", WHEN)
                    .withCapture("goo:crucible", WHEN).toJson();
            assertEquals(List.of("minecraft:chest"),
                    CaptureManifest.parse(json).missingFrom(CaptureManifest.itemIdsOf(INVENTORY)));
        }

        /**
         * An unreadable manifest covers nothing, so every listed item is captured anew.
         */
        @Test
        void anUnreadableManifestCoversNothing() {
            assertEquals(List.of("minecraft:oak_planks", "minecraft:chest", "goo:crucible"),
                    CaptureManifest.parse("not json").missingFrom(CaptureManifest.itemIdsOf(INVENTORY)));
        }
    }

    /**
     * The inventory's items are read from its items section alone, never its blocks.
     */
    @Test
    void itemIdsComeFromTheItemsSection() {
        assertEquals(List.of("minecraft:oak_planks", "minecraft:chest", "goo:crucible"),
                CaptureManifest.itemIdsOf(INVENTORY));
    }
}
