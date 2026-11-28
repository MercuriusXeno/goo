package com.mercuriusxeno.goo.client.ability;

import net.minecraft.core.BlockPos;
import java.util.function.Predicate;

/**
 * A world of air and whole blocks as the held sweeps walk it: every block
 * that is not air lights, and the front passes through air alone, as the
 * sculker echo's rules read a world with no partial block in it.
 * decision scry-shows-surfaces-as-the-sculker-echo-does
 */
final class CubeWalls {

    private CubeWalls() {
    }

    /**
     * @param isAir whether a position holds air
     * @return the world, read block by block
     */
    static WallWorld of(Predicate<BlockPos> isAir) {
        return new WallWorld(isAir.negate(), isAir);
    }
}
