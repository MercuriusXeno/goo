package com.mercuriusxeno.goo.block.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelReader;

/**
 * The support rule a block attached to a face shares, the glow crystal and
 * the prism alike: it stands while the block behind it offers a sturdy face,
 * and breaks once that support is gone, as a torch breaks.
 */
final class FaceSupport {

    private FaceSupport() {
    }

    /**
     * Whether the block behind an attached block offers a sturdy face to it.
     *
     * @param level  the level
     * @param pos    the attached block's position
     * @param facing the face the attached block grew from
     * @return true when the support stands
     */
    static boolean supports(LevelReader level, BlockPos pos, Direction facing) {
        BlockPos support = pos.relative(facing.getOpposite());
        return level.getBlockState(support).isFaceSturdy(level, support, facing);
    }

    /**
     * Breaks an attached block that no longer survives, on the server.
     *
     * @param survives whether the block's support still stands
     * @param level    the level
     * @param pos      the attached block's position
     * @param drop     whether the broken block drops its loot
     */
    static void breakUnsupported(boolean survives, LevelReader level, BlockPos pos, boolean drop) {
        if (!survives && level instanceof ServerLevel serverLevel) {
            serverLevel.destroyBlock(pos, drop);
        }
    }
}
