package com.mercuriusxeno.goo.block.ability;

import com.mercuriusxeno.goo.block.crystallizer.CrystalCluster;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The plain prism's column, shared by its model and its voxel shape: one six-sided,
 * flat-faced {@link CrystalCluster.Prism} half a block wide with a pointed tip,
 * standing on the center of the face the prism landed on and pointing into the cell.
 * decision prism-is-one-pointed-quartz-column
 */
public final class PrismColumn {

    /** The hexagon's corner radius in model pixels: 8 pixels across its corners, half a block. */
    public static final double RADIUS = 4;
    /** The column's length to its point in model pixels; the last quarter is the point. */
    public static final double LENGTH = 14;
    /** The one upright column, in model pixels on {@link CrystalCluster}'s base point along +y. */
    public static final CrystalCluster.Prism PRISM = new CrystalCluster.Prism(0, 0, LENGTH, RADIUS);

    private static final float PIXELS_PER_BLOCK = 16f;
    private static final float HALF = 0.5f;
    /** The hexagon's half-width across its flats, against its corner radius. */
    private static final double FLATS_SHARE = Math.cos(Math.PI / 6);

    private PrismColumn() {
    }

    /**
     * The turn that stands the column, drawn on {@link CrystalCluster}'s base point along
     * +y in block units, on the center of the landing face pointing along {@code facing}.
     *
     * @param facing the prism's facing, the face it landed on being the opposite one
     * @return the turn, in block units
     */
    public static Matrix4f placement(Direction facing) {
        Vector3f landing = new Vector3f(HALF - HALF * facing.getStepX(), HALF - HALF * facing.getStepY(),
                HALF - HALF * facing.getStepZ());
        Quaternionf upToFacing = new Quaternionf().rotationTo(0, 1, 0,
                facing.getStepX(), facing.getStepY(), facing.getStepZ());
        return new Matrix4f().translation(landing).rotate(upToFacing).translate(
                (float) -CrystalCluster.BASE_X / PIXELS_PER_BLOCK, (float) -CrystalCluster.BASE_Y / PIXELS_PER_BLOCK,
                (float) -CrystalCluster.BASE_Z / PIXELS_PER_BLOCK);
    }

    /**
     * The column's voxel shape: one cuboid around the whole column, its point included
     * (operator ruling: a cuboid, since stepped boxes read as a pencil), turned to the
     * prism's facing.
     *
     * @param facing the prism's facing
     * @return the shape
     */
    public static VoxelShape shapeFor(Direction facing) {
        return turnedBox(placement(facing), RADIUS, RADIUS * FLATS_SHARE, 0, LENGTH);
    }

    /**
     * @param placement   the column's turn, in block units
     * @param halfAcross  the box's half-width along the hexagon's corners, in model pixels
     * @param halfFlats   its half-width across the hexagon's flats, in model pixels
     * @param bottom      its bottom up the axis, in model pixels
     * @param top         its top up the axis, in model pixels
     * @return the box turned into the cell
     */
    private static VoxelShape turnedBox(Matrix4f placement, double halfAcross, double halfFlats, double bottom,
                                        double top) {
        Vector3f low = new Vector3f(Float.MAX_VALUE);
        Vector3f high = new Vector3f(-Float.MAX_VALUE);
        for (double x : new double[] {-halfAcross, halfAcross}) {
            for (double y : new double[] {bottom, top}) {
                for (double z : new double[] {-halfFlats, halfFlats}) {
                    Vector3f corner = placement.transformPosition(new Vector3f(
                            (float) ((CrystalCluster.BASE_X + x) / PIXELS_PER_BLOCK),
                            (float) ((CrystalCluster.BASE_Y + y) / PIXELS_PER_BLOCK),
                            (float) ((CrystalCluster.BASE_Z + z) / PIXELS_PER_BLOCK)));
                    low.min(corner);
                    high.max(corner);
                }
            }
        }
        // The turn's float error can stray a hair past the cell, which Shapes.box refuses.
        low.max(new Vector3f(0));
        high.min(new Vector3f(1));
        return Shapes.box(low.x, low.y, low.z, high.x, high.y, high.z);
    }
}
