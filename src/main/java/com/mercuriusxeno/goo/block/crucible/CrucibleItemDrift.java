package com.mercuriusxeno.goo.block.crucible;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Pulls item entities into the crucible's mouth and takes them at the kill box
 * (decision rim-and-mouth-items-slide-inward). A pull field a quarter block deep
 * rests over the block, from the rim up: it damps a thrown item's sideways speed
 * and draws it toward the mouth, so a throw drops in rather than sailing over. An
 * item grounded on the ledge against the collar is lifted up and over the wall. An
 * item beside the block is left alone.
 */
public final class CrucibleItemDrift {

    /** How far above the rim the pull field reaches. */
    static final double FIELD_HEIGHT = 0.25;
    /** The share of an item's sideways motion the field takes away each tick. */
    static final double FIELD_DAMPING = 0.5;
    /** Sideways speed the field adds per tick for each block of distance from the basin center. */
    static final double FIELD_GAIN = 0.15;
    /** The upward speed a grounded ledge item is given, enough to clear the collar and no more. */
    static final double LIFT_SPEED = 0.2;
    /** Sideways speed a lifted ledge item is given for each block of distance from the basin center. */
    static final double LIFT_INWARD_GAIN = 0.15;
    /** Leeway below the rim and the ledge within which an item reads as standing on them. */
    static final double SURFACE_TOLERANCE = 1.0 / 64.0;

    /** A change in motion smaller than this is left unsynced, so a still item sends nothing. */
    private static final double SYNC_THRESHOLD_SQR = 1e-10;

    /** The basin center in block-relative X and Z. */
    private static final double BASIN_CENTER = (CrucibleBasin.FOOTPRINT_MIN + CrucibleBasin.FOOTPRINT_MAX) / 2.0;

    private CrucibleItemDrift() {}

    /**
     * Takes each item in the kill box while the crucible is enabled and can heat,
     * and moves each other item over the block by the pull field and the ledge lift.
     *
     * @param crucible the crucible block entity
     * @param level    the server level
     * @param pos      the crucible's position
     */
    static void driftItems(CrucibleBlockEntity crucible, Level level, BlockPos pos) {
        AABB reach = new AABB(pos.getX(), pos.getY() + CrucibleBasin.FLOOR_Y, pos.getZ(),
            pos.getX() + 1.0, pos.getY() + CrucibleBasin.RIM_Y + FIELD_HEIGHT, pos.getZ() + 1.0);
        float killTopY = CrucibleBasin.killBoxTopY(crucible.basinVolumes());
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, reach)) {
            double x = item.getX() - pos.getX();
            double y = item.getY() - pos.getY();
            double z = item.getZ() - pos.getZ();
            if (CrucibleBasin.inKillBox(x, y, z, killTopY)) {
                if (crucible.isEnabled() && crucible.canHeat()) {
                    CrucibleAbsorption.tryAbsorbItem(item, crucible);
                }
                continue;
            }
            applyMotion(item, fieldDelta(item.getDeltaMovement(), x, y, z, item.onGround()));
        }
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
     * An item's motion after the pull field and the ledge lift: in the field its
     * sideways motion is damped and drawn to the center, its fall kept; grounded on
     * the ledge it is lifted and sent inward; anywhere else its motion is kept.
     *
     * @param delta    the item's motion this tick
     * @param x        the item's X relative to the block
     * @param y        the item's feet Y relative to the block
     * @param z        the item's Z relative to the block
     * @param grounded whether the item stands on something
     * @return the item's motion
     */
    public static Vec3 fieldDelta(Vec3 delta, double x, double y, double z, boolean grounded) {
        if (!overBlock(x) || !overBlock(z)) { return delta; }
        if (inPullField(y)) {
            double keep = 1.0 - FIELD_DAMPING;
            return new Vec3(delta.x * keep + (BASIN_CENTER - x) * FIELD_GAIN, delta.y,
                delta.z * keep + (BASIN_CENTER - z) * FIELD_GAIN);
        }
        if (grounded && onLedge(x, y, z)) {
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
     * @return true when the item stands on the ledge, outside the mouth and below the rim
     */
    private static boolean onLedge(double x, double y, double z) {
        boolean overMouth = withinMouth(x) && withinMouth(z);
        return !overMouth && y >= CrucibleShape.LEDGE_Y - SURFACE_TOLERANCE
            && y < CrucibleBasin.RIM_Y - SURFACE_TOLERANCE;
    }

    /**
     * @param coordinate a block-relative X or Z of the item's center
     * @return true when it lies over the block
     */
    private static boolean overBlock(double coordinate) {
        return coordinate >= 0.0 && coordinate <= 1.0;
    }

    /**
     * @param coordinate a block-relative X or Z of the item's center
     * @return true when it lies over the mouth
     */
    private static boolean withinMouth(double coordinate) {
        return coordinate >= CrucibleBasin.FOOTPRINT_MIN && coordinate <= CrucibleBasin.FOOTPRINT_MAX;
    }
}
