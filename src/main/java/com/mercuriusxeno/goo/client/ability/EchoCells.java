package com.mercuriusxeno.goo.client.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import java.util.function.Predicate;
import it.unimi.dsi.fastutil.longs.Long2BooleanOpenHashMap;

/**
 * Whether an echo lights each cell a ring crosses, a block with a collision
 * shape beside a block sound passes through, read from the world once and
 * kept: both rings of every sound, and every sound again from the same
 * source, cross the same cells. A cell's answer changes only when it or a
 * block beside it changes or its chunk loads or unloads, so those changes
 * alone drop it.
 * decision diagnose-sculkers-lag-and-prove-it-improved-significantly
 */
final class EchoCells {

    /** The most cells kept before the memory starts over, so a long swim submerged never outgrows it. */
    private static final int MOST_CELLS = 1 << 20;
    private static final Direction[] SIDES = Direction.values();

    private final Predicate<BlockPos> lights;
    private final Predicate<BlockPos> passesSound;
    private final Long2BooleanOpenHashMap answered = new Long2BooleanOpenHashMap();
    private int reads;

    /**
     * @param lights      whether a block lights where an echo reaches it
     * @param passesSound whether sound passes through a block
     */
    EchoCells(Predicate<BlockPos> lights, Predicate<BlockPos> passesSound) {
        this.lights = lights;
        this.passesSound = passesSound;
    }

    /**
     * @param cell a cell a ring crosses
     * @return true where the cell lights and stands beside a block sound passes through
     */
    boolean echoesOff(BlockPos cell) {
        long key = cell.asLong();
        if (answered.containsKey(key)) {
            return answered.get(key);
        }
        if (answered.size() >= MOST_CELLS) {
            answered.clear();
        }
        reads++;
        boolean echoes = lights.test(cell) && besideOpenSpace(cell);
        answered.put(key, echoes);
        return echoes;
    }

    private boolean besideOpenSpace(BlockPos cell) {
        for (Direction side : SIDES) {
            if (passesSound.test(cell.relative(side))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Drops a changed block's answer and its neighbours', whose open sides it sets.
     *
     * @param changed the block that changed
     */
    void forgetAround(BlockPos changed) {
        answered.remove(changed.asLong());
        for (Direction side : SIDES) {
            answered.remove(changed.relative(side).asLong());
        }
    }

    /**
     * The cells whose answer was read from the world since the last ask, not
     * kept from an earlier ring, for the profiler's counter.
     * decision diagnose-and-correct-the-lag-on-four-sense-abilities
     *
     * @return how many, counting from nothing again
     */
    int takeReads() {
        int taken = reads;
        reads = 0;
        return taken;
    }

    /** Forgets every answer, as a chunk loading or unloading or the held effect's end does. */
    void clear() {
        answered.clear();
    }
}
