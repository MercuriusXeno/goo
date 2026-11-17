package com.mercuriusxeno.goo.block.ability;

import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A standing Molten cell's client tick emits a fire particle, a smoke puff or
 * a sizzle only now and then, each at its named rate, and nothing on most
 * ticks, from the cell's top surface; a seeded random source keeps the run
 * repeatable (decision molten-sizzles-spits-sparse-fire-particles-and-smokes).
 */
class MoltenSizzleTest {

    private static final int TICKS = 10_000;
    private static final long SEED = 20_261_117L;
    /** How far a counted rate may stand from its named rate, as a share of it. */
    private static final double TOLERANCE = 0.3;
    private static final int QUIET_PER_LOUD = 10;

    @Nested
    class Rates {

        private final Map<MoltenSizzle.Emission, Integer> counts = new EnumMap<>(MoltenSizzle.Emission.class);
        private int quiet;

        Rates() {
            RandomSource random = RandomSource.create(SEED);
            for (int tick = 0; tick < TICKS; tick++) {
                Optional<MoltenSizzle.Puff> puff = MoltenSizzle.pick(random, VolumeCellBlock.MAX_LAYERS);
                puff.ifPresentOrElse(emitted -> counts.merge(emitted.emission(), 1, Integer::sum), () -> quiet++);
            }
        }

        @Test
        void eachEmissionOccursNearItsNamedRate() {
            for (MoltenSizzle.Emission emission : MoltenSizzle.Emission.values()) {
                double counted = counts.getOrDefault(emission, 0) / (double) TICKS;
                assertTrue(counted > 0 && Math.abs(counted - emission.rate()) <= emission.rate() * TOLERANCE,
                        emission + " counted at " + counted + " against " + emission.rate());
            }
        }

        @Test
        void quietTicksOutnumberEmittingTicksTenToOne() {
            int loud = TICKS - quiet;
            assertTrue(quiet >= loud * QUIET_PER_LOUD, quiet + " quiet ticks against " + loud);
        }
    }

    @Test
    void aPuffRisesFromTheCellsTopSurface() {
        RandomSource random = RandomSource.create(SEED);
        for (int layers = 1; layers <= VolumeCellBlock.MAX_LAYERS; layers++) {
            int held = layers;
            Optional<MoltenSizzle.Puff> puff = Optional.empty();
            while (puff.isEmpty()) {
                puff = MoltenSizzle.pick(random, held);
            }
            assertEquals((double) held / VolumeCellBlock.MAX_LAYERS, puff.get().height(), 1e-9);
        }
    }
}
