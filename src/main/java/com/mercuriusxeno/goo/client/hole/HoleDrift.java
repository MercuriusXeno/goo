package com.mercuriusxeno.goo.client.hole;

import com.mercuriusxeno.goo.network.PortableHolesSyncPayload;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The slow path each stored item swims on inside an open hole's mouth: every
 * item circles the mouth's middle at one pace, set apart from the next by the
 * golden angle so no two meet, while its distance from the middle and its
 * height off the face sway on rhythms of its own, so the items read as
 * floating rather than turning on a wheel.
 * decision hole-is-an-in-world-portable-hole-for-items
 */
public final class HoleDrift {

    /** The most stored kinds a mouth shows. */
    public static final int MOST_SHOWN = PortableHolesSyncPayload.MOST_STORED_SHOWN;
    /** How far from the mouth's middle an item may swim. */
    public static final double REACH = 0.3;
    /** How far off the face an item may rise. */
    public static final double MOST_LIFT = 0.15;

    /** The golden angle, the turn between one item's place and the next's. */
    private static final float GOLDEN_ANGLE = 2.3999632F;
    /** The radians every item circles the middle each tick, a lap in about fifteen seconds. */
    private static final float ORBIT = 0.021F;
    /** The middle of an item's sway toward and away from the mouth's middle, as a share of the reach. */
    private static final float SWAY_MIDDLE = 0.6F;
    /** How far that sway reaches either side of its middle, as a share of the reach. */
    private static final float SWAY_REACH = 0.3F;
    /** The radians the sway advances each tick. */
    private static final float SWAY = 0.031F;
    /** The turn between one item's sway and the next's. */
    private static final float SWAY_PHASE = 1.3F;
    /** The middle of an item's bob off the face, as a share of the most lift. */
    private static final float BOB_MIDDLE = 0.5F;
    /** How far the bob reaches either side of its middle, as a share of the most lift. */
    private static final float BOB_REACH = 0.4F;
    /** The radians the bob advances each tick. */
    private static final float BOB = 0.047F;
    /** The turn between one item's bob and the next's. */
    private static final float BOB_PHASE = 0.9F;

    private HoleDrift() {
    }

    /**
     * Where a stored item swims at a moment, in the mouth's own frame: x and z
     * across the face from its middle, y off the face into the open.
     *
     * @param index the item's place among the shown kinds
     * @param time  the game time, partial tick included
     * @return the offset from the mouth's middle
     */
    public static Vec3 offset(int index, float time) {
        float angle = ORBIT * time + index * GOLDEN_ANGLE;
        double radius = REACH * (SWAY_MIDDLE + SWAY_REACH * Mth.sin(SWAY * time + index * SWAY_PHASE));
        double lift = MOST_LIFT * (BOB_MIDDLE + BOB_REACH * Mth.sin(BOB * time + index * BOB_PHASE));
        return new Vec3(radius * Mth.cos(angle), lift, radius * Mth.sin(angle));
    }
}
