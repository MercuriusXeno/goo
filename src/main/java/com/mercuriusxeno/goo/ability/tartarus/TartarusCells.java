package com.mercuriusxeno.goo.ability.tartarus;

import net.minecraft.core.BlockPos;

/**
 * What each block of a level is to a player Tartarus sets down there.
 * decision tartarus-sets-you-on-the-nearest-safe-floor-at-the-scaled-spot-or-refuses
 */
@FunctionalInterface
public interface TartarusCells {

    /** What a block is to a player standing in it, on it or beside it. */
    enum Cell {
        /** Air, room to stand in. */
        OPEN,
        /** Solid ground that holds a player, burning nothing. */
        FLOOR,
        /** Lava, fire, or ground that burns a player standing on or beside it. */
        BURNING,
        /** Anything else: neither room to stand in nor ground to stand on. */
        OTHER
    }

    /**
     * @param pos the block
     * @return what it is to a player
     */
    Cell at(BlockPos pos);
}
