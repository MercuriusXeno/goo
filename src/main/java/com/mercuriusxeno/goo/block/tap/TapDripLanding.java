package com.mercuriusxeno.goo.block.tap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;
import java.util.function.Function;

/**
 * Where a drip lands: the first block straight below the tap whose collision
 * shape is not empty, the surface the client drip particle stops on
 * (decision drip-falls-to-first-surface).
 *
 * @param pos      the landing block
 * @param surfaceY the world Y of the landing block's collision top
 */
record TapDripLanding(BlockPos pos, double surfaceY) {

    /**
     * Scans the level below a tap for its landing.
     *
     * @param level  the level the tap stands in
     * @param tapPos the tap's position
     * @return the landing, or null over a bottomless drop
     */
    static @Nullable TapDripLanding below(Level level, BlockPos tapPos) {
        return scan(tapPos, level.getMinY(), pos -> level.getBlockState(pos).getCollisionShape(level, pos));
    }

    /**
     * Scans straight down from the block under the tap to the lowest block Y.
     *
     * @param tapPos      the tap's position
     * @param minY        the lowest block Y the scan reaches
     * @param collisionAt the collision shape of the block at a position
     * @return the first landing, or null when every block down to minY is empty
     */
    static @Nullable TapDripLanding scan(BlockPos tapPos, int minY, Function<BlockPos, VoxelShape> collisionAt) {
        BlockPos.MutableBlockPos cursor = tapPos.mutable();
        while (cursor.getY() > minY) {
            cursor.move(Direction.DOWN);
            VoxelShape shape = collisionAt.apply(cursor);
            if (!shape.isEmpty()) {
                return new TapDripLanding(cursor.immutable(), cursor.getY() + shape.max(Direction.Axis.Y));
            }
        }
        return null;
    }
}
