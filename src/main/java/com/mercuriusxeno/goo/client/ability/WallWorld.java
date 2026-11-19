package com.mercuriusxeno.goo.client.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * The world a held sweep's fronts walk for walls: whether a block holds
 * air, its shape, and which sections hold only air.
 * decision held-sweeps-share-one-fast-shell-visual
 * decision diagnose-and-correct-scry-lag-building-over-a-long-cast
 *
 * @param isAir   whether a position holds air
 * @param shapeOf a block's voxel shape boxes, block-local
 * @param air     which sections hold only air
 */
record WallWorld(Predicate<BlockPos> isAir, Function<BlockPos, List<AABB>> shapeOf, AirSections air) {

    /**
     * A world read block by block.
     *
     * @param isAir   whether a position holds air
     * @param shapeOf a block's voxel shape boxes, block-local
     */
    WallWorld(Predicate<BlockPos> isAir, Function<BlockPos, List<AABB>> shapeOf) {
        this(isAir, shapeOf, AirSections.NONE);
    }
}
