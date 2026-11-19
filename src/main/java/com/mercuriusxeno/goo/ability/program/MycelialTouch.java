package com.mercuriusxeno.goo.ability.program;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * The mycelial blocks an entity's body touches, which Transmit starts from:
 * the block it stands on or in, and any it presses against, where a block a
 * step away is near and untouched.
 * transmit-reaches-only-connected-mycelial-blocks-you-touch
 */
public final class MycelialTouch {

    /** How far past the body's box a block still counts as touched: a pixel, so a block stood on reads touched. */
    static final double MARGIN = 1.0 / 16;

    private MycelialTouch() {
    }

    /**
     * The mycelial blocks an entity touches.
     *
     * @param level  the level
     * @param entity the entity
     * @return the touched blocks, empty where it touches none
     */
    public static List<BlockPos> touched(Level level, Entity entity) {
        return touched(entity.getBoundingBox(), MycelialBlocks.in(level));
    }

    /**
     * The mycelial blocks whose cells a body's box, grown by the margin, overlaps.
     *
     * @param body     the body's box
     * @param mycelial whether a place holds a mycelial block
     * @return the touched blocks
     */
    static List<BlockPos> touched(AABB body, Predicate<BlockPos> mycelial) {
        AABB reach = body.inflate(MARGIN);
        List<BlockPos> found = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(reach.minX, reach.minY, reach.minZ),
                BlockPos.containing(reach.maxX, reach.maxY, reach.maxZ))) {
            if (mycelial.test(pos)) {
                found.add(pos.immutable());
            }
        }
        return found;
    }
}
