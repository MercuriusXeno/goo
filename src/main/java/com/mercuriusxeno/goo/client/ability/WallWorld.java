package com.mercuriusxeno.goo.client.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import java.util.function.Predicate;

/**
 * The world a held sweep's fronts walk for walls, read by the sculker
 * echo's rules: which blocks light, which let the front through to what
 * lies behind them, and which sections hold only air.
 * decision held-sweeps-share-one-fast-shell-visual
 * decision diagnose-and-correct-scry-lag-building-over-a-long-cast
 * decision scry-shows-surfaces-as-the-sculker-echo-does
 *
 * @param lightsUp    whether a block lights where the front finds it open
 * @param passesSound whether the front passes through a block, so the side of a block facing it is open
 * @param air         which sections hold only air
 */
record WallWorld(Predicate<BlockPos> lightsUp, Predicate<BlockPos> passesSound, AirSections air) {

    /**
     * A world read block by block.
     *
     * @param lightsUp    whether a block lights where the front finds it open
     * @param passesSound whether the front passes through a block
     */
    WallWorld(Predicate<BlockPos> lightsUp, Predicate<BlockPos> passesSound) {
        this(lightsUp, passesSound, AirSections.NONE);
    }

    /**
     * A level's blocks as the sculker echo reads them: a block with a
     * collision shape lights, and one that does not fill its cell lets the
     * front through to the wall behind it.
     *
     * @param level the level
     * @param air   which of its sections hold only air
     * @return the world
     */
    static WallWorld of(BlockGetter level, AirSections air) {
        return new WallWorld(at -> SculkerSenses.lightsUp(level, at), at -> SculkerSenses.passesSound(level, at), air);
    }
}
