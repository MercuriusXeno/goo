package com.mercuriusxeno.goo.block.crucible;

import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The crucible's collision and outline shape: the goocible model's body and
 * collar with the drawn cavity cut out, so a dropped item falls onto the basin
 * floor (decision collision-is-the-drawn-cavity).
 *
 * <p>The body and collar mirror the first two elements of
 * {@code assets/goo/models/block/goocible.json}; the cavity reads
 * {@link CrucibleBasin}'s bounds, so the drawn basin and the solid one stay one truth.
 */
public final class CrucibleShape {

    /** The body's top in block-relative Y, the outer ledge an item can rest on. */
    public static final double LEDGE_Y = 13.0 / 16.0;
    /** The collar's low X and Z edge in block-relative coords, the rim walls' outer face. */
    public static final double COLLAR_MIN = 2.0 / 16.0;
    /** The collar's high X and Z edge in block-relative coords. */
    public static final double COLLAR_MAX = 14.0 / 16.0;

    /** The body, full width up to the ledge. */
    private static final VoxelShape BODY = Shapes.box(0, 0, 0, 1, LEDGE_Y, 1);
    /** The collar the rim walls are cut from, ledge to rim. */
    private static final VoxelShape COLLAR = Shapes.box(
        COLLAR_MIN, LEDGE_Y, COLLAR_MIN, COLLAR_MAX, CrucibleBasin.RIM_Y, COLLAR_MAX);
    /** The drawn cavity, footprint by floor to rim. */
    public static final VoxelShape CAVITY = Shapes.box(
        CrucibleBasin.FOOTPRINT_MIN, CrucibleBasin.FLOOR_Y, CrucibleBasin.FOOTPRINT_MIN,
        CrucibleBasin.FOOTPRINT_MAX, CrucibleBasin.RIM_Y, CrucibleBasin.FOOTPRINT_MAX);
    /** The standing shape: body and collar less the cavity. */
    public static final VoxelShape SHAPE = Shapes.join(Shapes.or(BODY, COLLAR), CAVITY, BooleanOp.ONLY_FIRST);

    private CrucibleShape() {}
}
