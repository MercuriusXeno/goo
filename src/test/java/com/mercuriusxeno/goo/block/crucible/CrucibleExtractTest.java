package com.mercuriusxeno.goo.block.crucible;

import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.GooTypes;
import com.mercuriusxeno.goo.item.GooContents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionResult;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * An empty-hand crucible extract draws from the reservoir only what the inventory took,
 * so goo with no home stays in the crucible (decision drained-goo-fills-carried-containers-first).
 */
class CrucibleExtractTest {

    private static final ResourceKey<GooTypeDefinition> ROCK = GooTypes.ROCK;

    @Test
    void inventoryWithNoHomeLeavesTheReservoirWhole() {
        Map<ResourceKey<GooTypeDefinition>, Integer> reservoir = new HashMap<>(Map.of(ROCK, 5_000));

        InteractionResult result = CrucibleInteraction.extractInto(new GooContents(reservoir),
                (type, volume) -> { reservoir.merge(type, -volume, Integer::sum); return volume; },
                (type, volume) -> volume);

        assertAll(() -> assertEquals(InteractionResult.PASS, result),
                () -> assertEquals(5_000, reservoir.get(ROCK)));
    }

    @Test
    void reservoirKeepsWhatTheInventoryRefused() {
        Map<ResourceKey<GooTypeDefinition>, Integer> reservoir = new HashMap<>(Map.of(ROCK, 5_000));

        InteractionResult result = CrucibleInteraction.extractInto(new GooContents(reservoir),
                (type, volume) -> { reservoir.merge(type, -volume, Integer::sum); return volume; },
                (type, volume) -> volume - 2_000);

        assertAll(() -> assertEquals(InteractionResult.SUCCESS, result),
                () -> assertEquals(3_000, reservoir.get(ROCK)));
    }
}
