package com.mercuriusxeno.goo.block.crucible;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Pulls item entities into the crucible's mouth and takes them at the kill box
 * (decision rim-and-mouth-items-slide-inward). The field reaches a sixteenth of a
 * block past each side, measured to the item's edge, so an item grazing the block's
 * corner counts. Over the rim, a quarter block deep, it damps a thrown item's
 * sideways speed and draws it toward the mouth, so a throw drops in rather than
 * sailing over. Below the rim, down to an eighth of a block under the block's outer
 * top edge (where its side meets the ledge), it lifts an item up and over the collar,
 * so one that clips that edge or lands on the ledge never slides down or catches there.
 */
public final class CrucibleItemDrift {

    /** How far above the rim the pull field reaches. */
    static final double FIELD_HEIGHT = 0.25;
    /** How far past each side of the block the field reaches, to the item's near edge. */
    static final double FIELD_REACH = 1.0 / 16.0;
    /** An item entity's half-width, a quarter block wide. */
    static final double ITEM_HALF_WIDTH = 0.125;
    /** How far past each side an item's center may sit while its edge is in reach. */
    private static final double CENTER_REACH = FIELD_REACH + ITEM_HALF_WIDTH;
    /** How far under the block's outer top edge the lift reaches down its side. */
    static final double LIFT_DEPTH = 0.125;
    /** The lift's floor in block-relative Y, an eighth of a block under the outer top edge. */
    static final double LIFT_FLOOR_Y = CrucibleShape.LEDGE_Y - LIFT_DEPTH;
    /** The share of an item's sideways motion the field takes away each tick. */
    static final double FIELD_DAMPING = 0.5;
    /** Sideways speed the field adds per tick for each block of distance from the basin center. */
    static final double FIELD_GAIN = 0.15;
    /** The least upward speed an item below the rim is given, enough to clear the collar. */
    static final double LIFT_SPEED = 0.2;
    /** Sideways speed a lifted item is given for each block of distance from the basin center. */
    static final double LIFT_INWARD_GAIN = 0.15;
    /** Leeway below the rim within which an item reads as standing on it. */
    static final double SURFACE_TOLERANCE = 1.0 / 64.0;

    /** A change in motion smaller than this is left unsynced, so a still item sends nothing. */
    private static final double SYNC_THRESHOLD_SQR = 1e-10;

    /** The basin center in block-relative X and Z. */
    private static final double BASIN_CENTER = (CrucibleBasin.FOOTPRINT_MIN + CrucibleBasin.FOOTPRINT_MAX) / 2.0;

    private CrucibleItemDrift() {}

    /** How far out from the block, sideways, below and above, the aim assist looks for thrown items. */
    static final double AIM_RANGE = 3.0;

    /**
     * Takes each item in the kill box while the crucible is enabled and can heat,
     * steers each airborne item thrown roughly at the mouth into it, and moves each
     * other item in reach by the pull field and the lift.
     *
     * @param crucible the crucible block entity
     * @param level    the server level
     * @param pos      the crucible's position
     */
    static void driftItems(CrucibleBlockEntity crucible, Level level, BlockPos pos) {
        AABB reach = new AABB(pos).inflate(AIM_RANGE);
        float killTopY = CrucibleBasin.killBoxTopY(crucible.basinVolumes());
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, reach)) {
            Vec3 at = item.position().subtract(Vec3.atLowerCornerOf(pos));
            if (!CrucibleBasin.inKillBox(at.x, at.y, at.z, killTopY)) {
                applyMotion(item, steeredDelta(item, at));
            } else if (crucible.canHeat()) {
                CrucibleAbsorption.tryAbsorbItem(item, crucible);
            }
        }
    }

    /**
     * An item's motion outside the kill box: the aim assist's steer for an airborne
     * item thrown roughly at the mouth, else the pull field and the lift.
     *
     * @param item the item entity
     * @param at   the item's feet relative to the block
     * @return the item's motion
     */
    private static Vec3 steeredDelta(ItemEntity item, Vec3 at) {
        Vec3 aimed = item.onGround() ? null : CrucibleAimAssist.aimedDelta(at, item.getDeltaMovement());
        return aimed != null ? aimed : fieldDelta(item.getDeltaMovement(), at.x, at.y, at.z);
    }

    /**
     * Sets an item's motion, syncing it to clients only when it changed.
     *
     * @param item   the item entity
     * @param motion the motion to set
     */
    private static void applyMotion(ItemEntity item, Vec3 motion) {
        if (motion.distanceToSqr(item.getDeltaMovement()) <= SYNC_THRESHOLD_SQR) { return; }
        item.setDeltaMovement(motion);
        item.needsSync = true;
    }

    /**
     * An item's motion after the field: over the rim its sideways motion is damped
     * and drawn to the center, its fall kept; below the rim, outside the mouth, it is
     * lifted and sent inward; out of reach its motion is kept.
     *
     * @param delta the item's motion this tick
     * @param x     the item's X relative to the block
     * @param y     the item's feet Y relative to the block
     * @param z     the item's Z relative to the block
     * @return the item's motion
     */
    public static Vec3 fieldDelta(Vec3 delta, double x, double y, double z) {
        if (!inReach(x) || !inReach(z)) { return delta; }
        if (inPullField(y)) {
            double keep = 1.0 - FIELD_DAMPING;
            return new Vec3(delta.x * keep + (BASIN_CENTER - x) * FIELD_GAIN, delta.y,
                delta.z * keep + (BASIN_CENTER - z) * FIELD_GAIN);
        }
        if (inLiftRing(x, y, z)) {
            return new Vec3((BASIN_CENTER - x) * LIFT_INWARD_GAIN, Math.max(delta.y, LIFT_SPEED),
                (BASIN_CENTER - z) * LIFT_INWARD_GAIN);
        }
        return delta;
    }

    /**
     * @param y the item's feet Y relative to the block
     * @return true when the item is in the field, from the rim to its top
     */
    private static boolean inPullField(double y) {
        return y >= CrucibleBasin.RIM_Y - SURFACE_TOLERANCE && y <= CrucibleBasin.RIM_Y + FIELD_HEIGHT;
    }

    /**
     * @param x the item's X relative to the block
     * @param y the item's feet Y relative to the block
     * @param z the item's Z relative to the block
     * @return true when the item is below the rim and above the lift's floor, outside the mouth
     */
    private static boolean inLiftRing(double x, double y, double z) {
        boolean overMouth = withinMouth(x) && withinMouth(z);
        return !overMouth && y >= LIFT_FLOOR_Y
            && y < CrucibleBasin.RIM_Y - SURFACE_TOLERANCE;
    }

    /**
     * @param coordinate a block-relative X or Z of the item's center
     * @return true when an item centered there has its edge over the block or within the reach past its side
     */
    private static boolean inReach(double coordinate) {
        return coordinate >= -CENTER_REACH && coordinate <= 1.0 + CENTER_REACH;
    }

    /**
     * @param coordinate a block-relative X or Z of the item's center
     * @return true when it lies over the mouth
     */
    private static boolean withinMouth(double coordinate) {
        return coordinate >= CrucibleBasin.FOOTPRINT_MIN && coordinate <= CrucibleBasin.FOOTPRINT_MAX;
    }
}
