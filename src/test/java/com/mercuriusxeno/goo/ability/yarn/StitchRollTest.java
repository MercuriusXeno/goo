package com.mercuriusxeno.goo.ability.yarn;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Covers the roll a slime on Stitch takes: only the mobs the cell's layers
 * afford are drawn, each weighed by its layers, and the cell keeps its layers
 * less the mob's.
 * stitch-is-yarns-residual-that-turns-slimes-into-passive-mobs
 */
class StitchRollTest {

    private static final StitchMob CHICKEN = new StitchMob(Identifier.withDefaultNamespace("chicken"), 2);
    private static final StitchMob COW = new StitchMob(Identifier.withDefaultNamespace("cow"), 6);
    private static final List<StitchMob> TABLE = List.of(CHICKEN, COW);
    private static final int FULL_CELL = 15;

    @ParameterizedTest
    @ValueSource(doubles = {0.0, 0.25, 0.5, 0.99})
    void threeLayersAlwaysGiveTheChicken(double draw) {
        assertEquals(Optional.of(new StitchRoll(CHICKEN, 1)), StitchRoll.roll(3, TABLE, draw));
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, 0.1, 0.24})
    void fifteenLayersGiveTheChickenInTheFirstQuarter(double draw) {
        assertEquals(Optional.of(new StitchRoll(CHICKEN, FULL_CELL - 2)), StitchRoll.roll(FULL_CELL, TABLE, draw));
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.25, 0.5, 0.99})
    void fifteenLayersGiveTheCowInTheRest(double draw) {
        assertEquals(Optional.of(new StitchRoll(COW, FULL_CELL - 6)), StitchRoll.roll(FULL_CELL, TABLE, draw));
    }

    @Test
    void aCellAffordingNoMobRollsNothing() {
        assertEquals(Optional.empty(), StitchRoll.roll(1, TABLE, 0.5));
    }
}
