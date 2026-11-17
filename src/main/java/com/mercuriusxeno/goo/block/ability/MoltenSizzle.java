package com.mercuriusxeno.goo.block.ability;

import net.minecraft.util.RandomSource;
import java.util.Optional;

/**
 * What a standing Molten cell lets off on one client tick: now and then a
 * little fire particle, a puff of smoke or a sizzle, from its top surface,
 * and nothing on most ticks, so the heat shows sparsely.
 * molten-sizzles-spits-sparse-fire-particles-and-smokes
 */
public final class MoltenSizzle {

    /** What one tick lets off, each at the share of ticks it is picked on. */
    public enum Emission {
        /** A little fire particle spat from the surface. */
        FLAME(0.03),
        /** A puff of smoke rising from the surface. */
        SMOKE(0.04),
        /**
         * A short hiss, as lava fizzling out, the rarest of the three.
         * operator UAT 2026-11-17: the sizzling is too frequent
         */
        SIZZLE(0.0025);

        private final double rate;

        Emission(double rate) {
            this.rate = rate;
        }

        /**
         * @return the share of ticks this emission is picked on
         */
        public double rate() {
            return rate;
        }
    }

    /**
     * One emission and where it rises from.
     *
     * @param emission what the tick lets off
     * @param height   how far above the cell's floor it rises from, in blocks: the cell's top surface
     */
    public record Puff(Emission emission, double height) {
    }

    private MoltenSizzle() {
    }

    /**
     * Picks what a cell lets off this tick: one roll, landing on each
     * emission at its rate, or on nothing.
     *
     * @param random the client's random source
     * @param layers the layers the cell holds
     * @return the puff, empty on a quiet tick
     */
    public static Optional<Puff> pick(RandomSource random, int layers) {
        double roll = random.nextDouble();
        for (Emission emission : Emission.values()) {
            if (roll < emission.rate()) {
                return Optional.of(new Puff(emission, (double) layers / VolumeCellBlock.MAX_LAYERS));
            }
            roll -= emission.rate();
        }
        return Optional.empty();
    }
}
