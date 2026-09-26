package com.mercuriusxeno.goo.block.tap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import java.util.Map;

/**
 * The valve handle's box, kept apart from {@link TapBlock} so its hit shape
 * and the rate panel above it read without a Minecraft bootstrap.
 */
public final class TapValve {

    /** Pixels per block. */
    private static final double PIXELS_PER_BLOCK = 16;
    /** Low X and Z edge of the valve, in pixels. */
    private static final double MIN_XZ_PX = 6.5;
    /** High X and Z edge of the valve, in pixels. */
    private static final double MAX_XZ_PX = 9.5;
    /** Underside of the valve, in pixels. */
    private static final double BOTTOM_PX = 4;
    /** Top of the valve, in pixels. */
    private static final double TOP_PX = 6.5;
    /** Gap between the valve's top and the rate panel above it, in blocks. */
    private static final double PANEL_LIFT = 1.0 / PIXELS_PER_BLOCK;

    /** South-facing valve shape (toggle region). */
    static final VoxelShape SOUTH = Shapes.box(MIN_XZ_PX / PIXELS_PER_BLOCK, BOTTOM_PX / PIXELS_PER_BLOCK,
            MIN_XZ_PX / PIXELS_PER_BLOCK, MAX_XZ_PX / PIXELS_PER_BLOCK, TOP_PX / PIXELS_PER_BLOCK,
            MAX_XZ_PX / PIXELS_PER_BLOCK);

    /** Per-facing valve shapes for hit detection. */
    static final Map<Direction, VoxelShape> SHAPES = TapShapeBuilder.buildSubShapes(SOUTH);

    private TapValve() {
    }

    /**
     * @param facing the tap's facing
     * @return the valve shape for that facing, block-local
     */
    public static VoxelShape shape(Direction facing) {
        return SHAPES.getOrDefault(facing, SOUTH);
    }

    /**
     * Where the rate panel stands: just above the top center of the valve
     * (decision valve-panel-reads-rate).
     *
     * @param pos    the tap's block position
     * @param facing the tap's facing
     * @return the panel anchor in world coordinates
     */
    public static Vec3 panelAnchor(BlockPos pos, Direction facing) {
        AABB valve = shape(facing).bounds();
        return new Vec3(pos.getX() + valve.getCenter().x, pos.getY() + valve.maxY + PANEL_LIFT,
                pos.getZ() + valve.getCenter().z);
    }
}
