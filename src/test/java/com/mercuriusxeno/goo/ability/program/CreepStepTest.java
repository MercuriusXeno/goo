package com.mercuriusxeno.goo.ability.program;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What a Creep cell emits on a display tick: its spore particle at the cell
 * for a roll under the tuned chance, nothing for a roll at or over it, as
 * mycelium puffs its own.
 * host-and-spores-combine-into-one-residual
 * operator ruling 2026-11-18: give them a random tick chance on these spores
 */
class CreepStepTest {

    private static final Identifier SPORE = Identifier.fromNamespaceAndPath("goo", "spore");
    private static final float CHANCE = 0.1f;
    private static final BlockPos CELL = new BlockPos(3, 64, -7);
    private static final CreepStep CREEP = new CreepStep(3, 8, 20, 1, SPORE, CHANCE);
    private static final int STEPS = 100;

    @Test
    void emitsItsSporesAtTheCellForARollUnderTheChance() {
        IntStream.range(0, STEPS).mapToObj(step -> CHANCE * step / STEPS).forEach(roll -> assertEquals(
                Optional.of(new CreepStep.SporeEmission(SPORE, CELL)), CREEP.emissionFor(roll, CELL), "roll " + roll));
    }

    @Test
    void emitsNothingForARollAtOrOverTheChance() {
        IntStream.range(0, STEPS).mapToObj(step -> CHANCE + (1 - CHANCE) * step / STEPS).forEach(roll -> assertEquals(
                Optional.empty(), CREEP.emissionFor(roll, CELL), "roll " + roll));
    }
}
