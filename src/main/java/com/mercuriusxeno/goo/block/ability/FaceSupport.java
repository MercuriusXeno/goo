package com.mercuriusxeno.goo.block.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;

/**
 * The support rule a block attached to a face shares, the glow crystal and
 * the prism alike: it stands while the block behind it offers a sturdy face,
 * and breaks once that support is gone, as a torch breaks.
 */
public final class FaceSupport {

    private FaceSupport() {
    }

    /**
     * Whether an attached block can grow from an aimed face: the aimed block
     * offers it a sturdy face, the one rule the prism survives by, which the
     * aim's highlight and the cast read alike.
     * decision prism-highlight-draws-only-on-a-face-a-prism-grows-from
     *
     * @param level the level
     * @param aimed the aimed block
     * @param face  the aimed face
     * @return true for a sturdy face
     */
    public static boolean growsFrom(BlockGetter level, BlockPos aimed, Direction face) {
        return level.getBlockState(aimed).isFaceSturdy(level, aimed, face);
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
        return growsFrom(level, pos.relative(facing.getOpposite()), facing);
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
