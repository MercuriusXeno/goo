package com.mercuriusxeno.goo.ability.program;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What a Spores cell emits: its spore particle at the cell on each period,
 * nothing between periods.
 * spores-is-shrooms-residual-a-passive-mycosis
 */
class SporesStepTest {

    private static final Identifier SPORE = Identifier.fromNamespaceAndPath("goo", "spore");
    private static final int PERIOD = 20;
    private static final BlockPos CELL = new BlockPos(3, 64, -7);
    private static final SporesStep SPORES = new SporesStep(PERIOD, 1, SPORE);
    private static final long PERIODS = 5;

    @Test
    void emitsItsSporesAtTheCellOnEachPeriod() {
        LongStream.range(0, PERIODS).map(period -> period * PERIOD).forEach(time -> assertEquals(
                Optional.of(new SporesStep.SporeEmission(SPORE, CELL)), SPORES.emissionAt(time, CELL), "tick " + time));
    }

    @Test
    void emitsNothingBetweenPeriods() {
        LongStream.range(0, PERIODS * PERIOD).filter(time -> time % PERIOD != 0).forEach(time -> assertEquals(
                Optional.empty(), SPORES.emissionAt(time, CELL), "tick " + time));
    }
}
