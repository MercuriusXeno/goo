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
    /** Gap between the panel and the nearest corner of the tap in front of it, in blocks. */
    private static final double PANEL_CLEARANCE = 0.5 / PIXELS_PER_BLOCK;

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
     * Where the rate panel stands: just above the top center of the valve,
     * drawn toward the camera until the valve and a slotted canister lie
     * wholly behind the camera-facing panel, so it prints in front of them
     * from any side and never inside them (decision valve-panel-reads-rate).
     *
     * @param pos         the tap's block position
     * @param facing      the tap's facing
     * @param camera      the camera position in world coordinates
     * @param hasCanister whether a canister stands in the slot
     * @return the panel anchor in world coordinates
     */
    public static Vec3 panelAnchor(BlockPos pos, Direction facing, Vec3 camera, boolean hasCanister) {
        AABB valve = shape(facing).bounds();
        Vec3 aboveValve = new Vec3(pos.getX() + valve.getCenter().x, pos.getY() + valve.maxY + PANEL_LIFT,
                pos.getZ() + valve.getCenter().z);
        Vec3 toCamera = camera.subtract(aboveValve);
        if (toCamera.lengthSqr() == 0) {
            return aboveValve;
        }
        Vec3 view = toCamera.normalize();
        double depth = cornerDepth(valve.move(pos), aboveValve, view);
        if (hasCanister) {
            depth = Math.max(depth, cornerDepth(TapHitRegion.canisterSlotShape(facing).bounds().move(pos),
                    aboveValve, view));
        }
        return aboveValve.add(view.scale(Math.max(0, depth) + PANEL_CLEARANCE));
    }

    /**
     * @param box    a box in world coordinates
     * @param origin the point measured from
     * @param view   the unit direction toward the camera
     * @return how far the box's nearest-to-camera corner stands toward the camera from the origin
     */
    static double cornerDepth(AABB box, Vec3 origin, Vec3 view) {
        double depth = Double.NEGATIVE_INFINITY;
        for (double x : new double[] {box.minX, box.maxX}) {
            for (double y : new double[] {box.minY, box.maxY}) {
                for (double z : new double[] {box.minZ, box.maxZ}) {
                    depth = Math.max(depth, new Vec3(x, y, z).subtract(origin).dot(view));
                }
            }
        }
        return depth;
    }
}
