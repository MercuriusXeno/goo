package com.mercuriusxeno.goo.block.crystallizer;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import java.util.Map;

/**
 * The crystallizer's shapes over plain values, the facing and the crystallized
 * volume, so a unit test reaches them without the block's state properties,
 * whose static init needs the Minecraft bootstrap.
 */
public final class CrystallizerShapes {

    /**
     * The dial and its pointer on the facing face, the operator's model's dial at
     * x 6.5 to 9.5, y 5.5 to 9.5 on its front, turned to each facing.
     */
    private static final Map<Direction, VoxelShape> KNOB_SHAPES = Map.of(
            Direction.SOUTH, pixelBox(6.5, 5.5, 15, 9.5, 9.5, 16),
            Direction.NORTH, pixelBox(6.5, 5.5, 0, 9.5, 9.5, 1),
            Direction.EAST, pixelBox(15, 5.5, 6.5, 16, 9.5, 9.5),
            Direction.WEST, pixelBox(0, 5.5, 6.5, 1, 9.5, 9.5));
    /** The operator's model's body, 14 by 16 by 14. */
    static final VoxelShape BODY_SHAPE = pixelBox(1, 0, 1, 15, 16, 15);
    private static final double TOP = CrystallizerLayout.TOP;
    private static final double PIXELS_PER_BLOCK = 16.0;

    private CrystallizerShapes() {
    }

    /**
     * @param facing the face the dial sits on
     * @return the knob's shape on that face
     */
    public static VoxelShape knobShape(Direction facing) {
        return KNOB_SHAPES.get(facing);
    }

    /**
     * The shape a crosshair or a click resolves against: the body and the dial, and
     * the crystal from its first crystallized mB (decision crystal-hud-shows-on-crystal-look).
     *
     * @param facing       the face the dial sits on
     * @param crystallized the crystallized volume, in mB
     * @return the crystallizer's outline shape
     */
    public static VoxelShape hitShape(Direction facing, long crystallized) {
        return Shapes.or(BODY_SHAPE, knobShape(facing), crystalShape(facing, crystallized));
    }

    /**
     * The box around the quartz cluster, so a click on the crystal lands on the crystallizer.
     *
     * @param facing       the face the dial sits on
     * @param crystallized the crystallized volume, in mB
     * @return the cluster's box, empty while nothing is crystallized
     */
    public static VoxelShape crystalShape(Direction facing, long crystallized) {
        double[] reach = CrystalCluster.reach(crystallized);
        if (reach[1] <= 0) {
            return Shapes.empty();
        }
        double[] center = CrystallizerLayout.modelToWorld(facing, CrystalCluster.BASE_X, CrystalCluster.BASE_Z);
        return pixelBox(center[0] - reach[0], TOP, center[1] - reach[0],
                center[0] + reach[0], TOP + reach[1], center[1] + reach[0]);
    }

    /**
     * Block.box over pixels, without touching the Block class.
     *
     * @param minX low x, in pixels
     * @param minY low y, in pixels
     * @param minZ low z, in pixels
     * @param maxX high x, in pixels
     * @param maxY high y, in pixels
     * @param maxZ high z, in pixels
     * @return the box, in blocks
     */
    private static VoxelShape pixelBox(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        return Shapes.box(minX / PIXELS_PER_BLOCK, minY / PIXELS_PER_BLOCK, minZ / PIXELS_PER_BLOCK,
                maxX / PIXELS_PER_BLOCK, maxY / PIXELS_PER_BLOCK, maxZ / PIXELS_PER_BLOCK);
    }
}
