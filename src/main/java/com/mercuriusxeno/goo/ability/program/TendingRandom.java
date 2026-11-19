package com.mercuriusxeno.goo.ability.program;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;

/**
 * The random source the stream steps tending plants hand each plant's
 * random tick: the level's own, unless a harness holds another for the
 * calls it makes, so a gametest reads a plant's growth on a known tick.
 * Outside a harness nothing is held, and the plants draw on the level.
 * gametests-waiting-on-a-random-draw-are-found-and-made-deterministic
 */
public final class TendingRandom {

    private static RandomSource held;

    private TendingRandom() {
    }

    /**
     * The random source a plant tended in a level draws on.
     *
     * @param level the level the plant stands in
     * @return the held source, or the level's own where none is held
     */
    static RandomSource of(ServerLevel level) {
        return held != null ? held : level.getRandom();
    }

    /**
     * Holds a random source for every plant tended from now on.
     *
     * @param random the source the plants draw on
     * @return releases the hold, the plants drawing on the level again
     */
    public static Runnable hold(RandomSource random) {
        held = random;
        return () -> held = null;
    }
}
