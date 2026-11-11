package com.mercuriusxeno.goo.client.hole;

import com.mercuriusxeno.goo.network.PortableHolesSyncPayload;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The slow path each stored item swims on sunk inside an open hole: the items
 * scatter as a sunflower across the whole mouth, each a golden angle round from
 * the last and farther out, and each at a depth of its own below the face, so
 * they fill the hole as a soup rather than a ring. The whole soup turns slowly,
 * each item swaying in and out and bobbing in depth on rhythms of its own, and
 * every item stays wholly inside the pit behind the mouth.
 * decision hole-is-an-in-world-portable-hole-for-items
 * decision hole-items-are-a-three-dimensional-soup-sunk-in-the-hole
 */
public final class HoleDrift {

    /** The most stored kinds a mouth shows. */
    public static final int MOST_SHOWN = PortableHolesSyncPayload.MOST_STORED_SHOWN;
    /** How far the pit behind the mouth reaches into its blocks. */
    public static final double PIT_DEPTH = 0.95;
    /** The size an item draws at, its model's largest span. */
    public static final double ITEM_SIZE = 0.25;
    /** How far any part of an item, turned any way, reaches from its middle: half its cube's diagonal. */
    public static final double DRAWN_RADIUS = ITEM_SIZE * Math.sqrt(3) / 2;

    /** The golden angle, the turn between one item's place and the next's. */
    private static final float GOLDEN_ANGLE = 2.3999632F;
    /** The golden ratio's conjugate, which steps the items' depths apart. */
    private static final float GOLDEN_STEP = 0.618034F;
    /** The radians the whole soup turns each tick, a lap in about half a minute. */
    private static final float ORBIT = 0.01F;
    /** How far out the outermost item sits, as a share of the room across. */
    private static final float SPREAD = 0.82F;
    /** How far an item sways in and out, as a share of the room across. */
    private static final float SWAY_REACH = 0.08F;
    /** The radians the sway advances each tick. */
    private static final float SWAY = 0.031F;
    /** The turn between one item's sway and the next's. */
    private static final float SWAY_PHASE = 1.3F;
    /** The share of the room in depth an item's own depth keeps clear at either end for its bob. */
    private static final float BOB_REACH = 0.08F;
    /** The radians the bob advances each tick. */
    private static final float BOB = 0.047F;
    /** The turn between one item's bob and the next's. */
    private static final float BOB_PHASE = 0.9F;
    private static final float HALF = 0.5F;
    /** The two ends of the room in depth, the face's and the pit floor's, each kept clear. */
    private static final int BOTH_ENDS = 2;

    private HoleDrift() {
    }

    /**
     * Where a stored item swims at a moment, in the mouth's own frame: x across
     * the face from its middle, z along it, and y off the face, negative into
     * the pit behind it.
     *
     * @param index      the item's place among the shown kinds
     * @param time       the game time, partial tick included
     * @param halfAcross the mouth's half span across, along x
     * @param halfAlong  the mouth's half span along, along z
     * @return the offset of the item's middle from the mouth's middle
     */
    public static Vec3 offset(int index, float time, double halfAcross, double halfAlong) {
        // a point in the unit disc, turned with the whole soup and swaying, stays in the disc
        float out = SPREAD * Mth.sqrt((index + HALF) / MOST_SHOWN)
                + SWAY_REACH * Mth.sin(SWAY * time + index * SWAY_PHASE);
        float angle = ORBIT * time + index * GOLDEN_ANGLE;
        double across = out * Mth.cos(angle) * (halfAcross - DRAWN_RADIUS);
        double along = out * Mth.sin(angle) * (halfAlong - DRAWN_RADIUS);
        float layer = Mth.frac(HALF + index * GOLDEN_STEP);
        float sunk = BOB_REACH + (1F - BOTH_ENDS * BOB_REACH) * layer
                + BOB_REACH * Mth.sin(BOB * time + index * BOB_PHASE);
        double depth = DRAWN_RADIUS + (PIT_DEPTH - BOTH_ENDS * DRAWN_RADIUS) * sunk;
        return new Vec3(across, -depth, along);
    }
}
