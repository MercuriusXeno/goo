package com.mercuriusxeno.goo.block.ability;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Where a Creep cell places its next bud: the one free valid edge space
 * whatever the roll, paid in a fixed count of layers; nowhere, its layers
 * kept, once no edge space is free and valid; and never a change to a bud
 * or nether wart already standing.
 * spores-goo-spends-on-edge-growth-and-grows-to-buds
 */
class CreepGrowthTest {

    private static final BlockPos CELL = new BlockPos(0, 64, 0);
    private static final BlockPos FREE = CELL.east();
    private static final BlockPos INVALID = CELL.west();
    private static final BlockPos BUD_AT_AGE_ONE = CELL.north();
    private static final BlockPos WART_AT_AGE_ZERO = CELL.south();
    private static final int LAYERS = 9;
    private static final int PER_BUD = 2;
    private static final int ROLLS = 64;

    private static final CreepGrowth.Edge BUD = new CreepGrowth.Edge(BUD_AT_AGE_ONE, false, false);
    private static final CreepGrowth.Edge WART = new CreepGrowth.Edge(WART_AT_AGE_ZERO, false, false);
    private static final CreepGrowth.Edge NO_FLOOR = new CreepGrowth.Edge(INVALID, true, false);

    @Test
    void everyRollPlacesInTheOneFreeValidSpaceAndSpendsABudsLayers() {
        List<CreepGrowth.Edge> edges = List.of(BUD, new CreepGrowth.Edge(FREE, true, true), NO_FLOOR, WART);

        IntStream.range(-ROLLS, ROLLS).forEach(roll -> assertEquals(
                new CreepGrowth.Budding(Optional.of(FREE), LAYERS - PER_BUD),
                CreepGrowth.nextBud(edges, roll, LAYERS, PER_BUD), "roll " + roll));
    }

    @Test
    void noFreeValidSpacePlacesNothingAndKeepsTheLayers() {
        List<CreepGrowth.Edge> edges = List.of(BUD, NO_FLOOR, WART);

        IntStream.range(-ROLLS, ROLLS).forEach(roll -> assertEquals(
                new CreepGrowth.Budding(Optional.empty(), LAYERS),
                CreepGrowth.nextBud(edges, roll, LAYERS, PER_BUD), "roll " + roll));
    }

    @Test
    void aStandingBudAndWartAreNeverAnsweredForAChange() {
        List<CreepGrowth.Edge> edges = List.of(BUD, WART);

        IntStream.range(-ROLLS, ROLLS).forEach(roll -> assertEquals(Optional.empty(),
                CreepGrowth.nextBud(edges, roll, LAYERS, PER_BUD).spot(), "roll " + roll));
    }

    @Test
    void gooShortOfABudPlacesNothing() {
        List<CreepGrowth.Edge> edges = List.of(new CreepGrowth.Edge(FREE, true, true));

        assertEquals(new CreepGrowth.Budding(Optional.empty(), PER_BUD - 1),
                CreepGrowth.nextBud(edges, 0, PER_BUD - 1, PER_BUD));
    }

    @Test
    void theRollChoosesAmongSeveralFreeValidSpaces() {
        List<CreepGrowth.Edge> edges = List.of(new CreepGrowth.Edge(FREE, true, true),
                new CreepGrowth.Edge(INVALID, true, true));

        assertEquals(List.of(FREE, INVALID), IntStream.range(0, 2)
                .mapToObj(roll -> CreepGrowth.nextBud(edges, roll, LAYERS, PER_BUD).spot().orElseThrow()).toList());
    }
}
