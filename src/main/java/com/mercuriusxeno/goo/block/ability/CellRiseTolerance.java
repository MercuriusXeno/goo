package com.mercuriusxeno.goo.block.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The server lets a player's move stand where its only new overlap is a
 * volume cell risen within a step of their feet. A cell rises on the server
 * the tick a drip lands or a spread pours into it, and the player's client
 * learns of it a tick later, so a runner moving into it puts their feet a
 * layer or two inside it; vanilla takes any move newly overlapping a block as
 * wrong and snaps the player back, zeroing their speed, several times a
 * second while they run into the goo they spray.
 * operator report 2026-11-14: running on slick and spraying straight ahead hitches several times a second
 * slick-coats-the-ground-with-kinetic-speed
 */
public final class CellRiseTolerance {

    private static final double DEFLATE = 1.0E-5;

    private CellRiseTolerance() {
    }

    /**
     * Whether a move newly overlaps anything besides volume cells risen
     * within a step of the mover's feet: a block, an entity it collides
     * with, or the world border, as vanilla reads them.
     *
     * @param level   the level
     * @param entity  the mover
     * @param oldBox  its box before the move
     * @param newBox  its box after the move
     * @return true where the move collides with something new that is no risen cell
     */
    public static boolean collidesBesidesRisenCells(LevelReader level, Entity entity, AABB oldBox, AABB newBox) {
        AABB moved = newBox.deflate(DEFLATE);
        VoxelShape before = Shapes.create(oldBox.deflate(DEFLATE));
        VoxelShape after = Shapes.create(moved);
        CollisionContext context = CollisionContext.of(entity);
        double stepTop = newBox.minY + entity.maxUpStep();
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(moved.minX, moved.minY, moved.minZ),
                BlockPos.containing(moved.maxX, moved.maxY, moved.maxZ))) {
            BlockState state = level.getBlockState(pos);
            VoxelShape placed = state.getCollisionShape(level, pos, context).move(pos);
            if (newlyOverlaps(placed, after, before) && !risenCellWithinAStep(state, placed, stepTop)) {
                return true;
            }
        }
        for (VoxelShape shape : level.getEntityCollisions(entity, moved)) {
            if (!Shapes.joinIsNotEmpty(shape, before, BooleanOp.AND)) {
                return true;
            }
        }
        return !level.getWorldBorder().isWithinBounds(moved);
    }

    private static boolean newlyOverlaps(VoxelShape shape, VoxelShape after, VoxelShape before) {
        return !shape.isEmpty() && Shapes.joinIsNotEmpty(shape, after, BooleanOp.AND)
                && !Shapes.joinIsNotEmpty(shape, before, BooleanOp.AND);
    }

    private static boolean risenCellWithinAStep(BlockState state, VoxelShape placed, double stepTop) {
        return state.getBlock() instanceof VolumeCellBlock && placed.max(Direction.Axis.Y) <= stepTop;
    }
}
