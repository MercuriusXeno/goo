package com.mercuriusxeno.goo.block.quantum;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.Optional;

/**
 * Pushes whatever stands inside a block stepping back into phase out to the
 * nearest room beside it, so nothing is left buried in the wall.
 * portable-hole-phases-blocks-for-a-while
 */
public final class PhaseClearance {

    /** The farthest, in blocks, an entity is pushed looking for room. */
    static final int MAX_PUSH = 3;

    /** The directions tried at each distance, sideways first, then up, then down. */
    private static final Direction[] PUSH_ORDER = {
            Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP, Direction.DOWN};

    private PhaseClearance() {
    }

    /**
     * Pushes every entity overlapping the cell to the nearest place it fits.
     *
     * @param level the level
     * @param pos   the cell that turned solid
     */
    public static void pushClear(ServerLevel level, BlockPos pos) {
        for (Entity entity : level.getEntities((Entity) null, new AABB(pos))) {
            if (!level.noCollision(entity, entity.getBoundingBox())) {
                roomFor(level, entity).ifPresent(spot -> entity.teleportTo(spot.x, spot.y, spot.z));
            }
        }
    }

    /**
     * The nearest offset of an entity, one whole block at a time out to
     * {@link #MAX_PUSH}, where its box meets no collision.
     *
     * @param level  the level
     * @param entity the buried entity
     * @return where it fits, or empty when nothing within reach does
     */
    static Optional<Vec3> roomFor(ServerLevel level, Entity entity) {
        for (int distance = 1; distance <= MAX_PUSH; distance++) {
            for (Direction direction : PUSH_ORDER) {
                Vec3 offset = Vec3.atLowerCornerOf(direction.getUnitVec3i()).scale(distance);
                if (level.noCollision(entity, entity.getBoundingBox().move(offset))) {
                    return Optional.of(entity.position().add(offset));
                }
            }
        }
        return Optional.empty();
    }
}
