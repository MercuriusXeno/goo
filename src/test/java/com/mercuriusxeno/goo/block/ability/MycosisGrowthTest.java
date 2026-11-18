package com.mercuriusxeno.goo.block.ability;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Where Mycosis goo grows its next bud: the one free valid spot, paid in
 * layers, and nowhere, its layers kept, once every spot bears fungus.
 * mycosis-goo-disperses-spores-and-grows-shrooms-nearby-depleting
 */
class MycosisGrowthTest {

    private static final BlockPos CELL = new BlockPos(0, 64, 0);
    private static final BlockPos NEAR = CELL.east(2);
    private static final BlockPos FAR = CELL.north(2);
    private static final int LAYERS = 9;
    private static final int PER_BUD = 2;

    @Test
    void growsInTheOneFreeSpotAndSpendsABudsLayers() {
        MycosisGrowth.Growth growth = MycosisGrowth.nextGrowth(List.of(CELL, NEAR, FAR), Set.of(CELL, FAR), LAYERS,
                PER_BUD);

        assertEquals(new MycosisGrowth.Growth(Optional.of(NEAR), LAYERS - PER_BUD), growth);
    }

    @Test
    void saturatedGooGrowsNothingAndKeepsItsLayers() {
        MycosisGrowth.Growth growth = MycosisGrowth.nextGrowth(List.of(CELL, NEAR, FAR), Set.of(CELL, NEAR, FAR),
                LAYERS, PER_BUD);

        assertEquals(new MycosisGrowth.Growth(Optional.empty(), LAYERS), growth);
    }

    @Test
    void growsInItsOwnSpaceFirst() {
        MycosisGrowth.Growth growth = MycosisGrowth.nextGrowth(List.of(CELL, NEAR), Set.of(), LAYERS, PER_BUD);

        assertEquals(Optional.of(CELL), growth.spot());
    }

    @Test
    void gooShortOfABudGrowsNothing() {
        MycosisGrowth.Growth growth = MycosisGrowth.nextGrowth(List.of(CELL), Set.of(), PER_BUD - 1, PER_BUD);

        assertEquals(new MycosisGrowth.Growth(Optional.empty(), PER_BUD - 1), growth);
    }
}
