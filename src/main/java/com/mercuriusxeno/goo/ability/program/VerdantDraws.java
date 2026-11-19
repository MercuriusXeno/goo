package com.mercuriusxeno.goo.ability.program;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The random source a Verdant prism's pulse picks its cells and its crop
 * from: the level's own, unless a harness holds another for that prism, so
 * a gametest's prism draws the same picks every run. Outside a harness
 * nothing is held, and every prism draws on its level.
 * gametests-waiting-on-a-random-draw-are-found-and-made-deterministic
 */
public final class VerdantDraws {

    private static final Map<BlockPos, RandomSource> HELD = new ConcurrentHashMap<>();

    private VerdantDraws() {
    }

    /**
     * The random source a prism's pulse draws on.
     *
     * @param level the prism's level
     * @param prism the prism's cell
     * @return the source held for the prism, or the level's own where none is held
     */
    static RandomSource of(ServerLevel level, BlockPos prism) {
        RandomSource held = HELD.get(prism);
        return held != null ? held : level.getRandom();
    }

    /**
     * Holds a random source for every pulse of the prism at a cell from now on.
     *
     * @param prism  the prism's cell
     * @param random the source its pulses draw on
     * @return releases the hold, the prism drawing on its level again
     */
    public static Runnable hold(BlockPos prism, RandomSource random) {
        BlockPos key = prism.immutable();
        HELD.put(key, random);
        return () -> HELD.remove(key);
    }
}
