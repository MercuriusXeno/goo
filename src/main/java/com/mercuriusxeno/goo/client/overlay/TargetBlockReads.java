package com.mercuriusxeno.goo.client.overlay;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;

/**
 * What the aim reads off the aimed-at block: whether the block is a water
 * source (decision render-context-is-the-one-emitter).
 */
final class TargetBlockReads {

    private TargetBlockReads() {
    }

    /**
     * Returns true if the block at the given position is a water source.
     *
     * @param level the client level
     * @param pos   the block position
     * @return true if water source
     */
    static boolean isWaterSource(Level level, BlockPos pos) {
        return level.getFluidState(pos).isSource()
                && level.getFluidState(pos).getType() == Fluids.WATER;
    }
}
