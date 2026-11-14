package com.mercuriusxeno.goo.block.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * A thing whose feet stand inside a volume cell is lifted onto the cell's
 * top before it moves. A cell rises the tick a drip lands or a spread pours
 * into it, and a player's client learns of it a tick later, by when the
 * client already stands the player in it, a layer or two deep; vanilla then
 * slides the player's next move along the cells beside rather than stepping
 * it up, turning it off its line and slowing it. Lifted onto the top, the
 * move steps up as from any ground.
 * operator report 2026-11-14: hitting goo redirects the runner off the track and hitches it
 */
public final class CellLift {

    /** Shaves the box's edges, so a cell the box only touches lifts nothing. */
    private static final double TOUCHING = 1.0E-4;

    private CellLift() {
    }

    /**
     * Lifts a thing onto the top of the volume cells its feet stand inside,
     * where that top stands within its step and its head has room there.
     *
     * @param entity the thing about to move
     */
    public static void liftOntoRisenCells(Entity entity) {
        if (entity.noPhysics) {
            return;
        }
        AABB box = entity.getBoundingBox();
        double lift = liftOutOf(entity, box);
        if (lift > 0 && entity.level().noCollision(entity, box.move(0, lift, 0).deflate(TOUCHING))) {
            entity.setPos(entity.getX(), entity.getY() + lift, entity.getZ());
        }
    }

    /**
     * How far a box's feet stand inside the volume cells under it: the
     * highest such cell's top over the feet, where it stands within the
     * thing's step.
     *
     * @param entity the thing
     * @param box    its box
     * @return the lift, 0 where its feet stand in no cell
     */
    static double liftOutOf(Entity entity, AABB box) {
        double lift = 0;
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(box.minX + TOUCHING, box.minY, box.minZ + TOUCHING),
                BlockPos.containing(box.maxX - TOUCHING, box.minY, box.maxZ - TOUCHING))) {
            BlockState state = entity.level().getBlockState(pos);
            if (state.getBlock() instanceof VolumeCellBlock) {
                double above = pos.getY() + (double) VolumeCellBlock.layersOf(state) / VolumeCellBlock.MAX_LAYERS
                        - box.minY;
                if (above > TOUCHING && above <= entity.maxUpStep()) {
                    lift = Math.max(lift, above);
                }
            }
        }
        return lift;
    }
}
