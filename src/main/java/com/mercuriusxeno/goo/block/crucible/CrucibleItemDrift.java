package com.mercuriusxeno.goo.block.crucible;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Draws item entities off the crucible's rim walls and down its mouth into the
 * cavity (decision rim-and-mouth-items-slide-inward). An item on the outer
 * ledge, below the wall tops, is left where it lands.
 */
public final class CrucibleItemDrift {

    /** Horizontal speed added per tick for each block of distance from the basin center. */
    static final double NUDGE_GAIN = 0.1;
    /** How far above the rim the air column over the mouth reaches. */
    static final double MOUTH_COLUMN_HEIGHT = 0.5;
    /** An item entity's half-width, a quarter block wide. */
    static final double ITEM_HALF_WIDTH = 0.125;
    /** Leeway around the rim height within which an item reads as resting on a wall top. */
    static final double WALL_TOP_TOLERANCE = 1.0 / 64.0;

    /** The basin center in block-relative X and Z. */
    private static final double BASIN_CENTER = (CrucibleBasin.FOOTPRINT_MIN + CrucibleBasin.FOOTPRINT_MAX) / 2.0;

    private CrucibleItemDrift() {}

    /**
     * Adds the inward nudge to each item entity on the wall tops or over the mouth,
     * keeping the rest of its motion so a thrown item holds its arc.
     *
     * @param level the server level
     * @param pos   the crucible's position
     */
    static void nudgeItems(Level level, BlockPos pos) {
        AABB top = new AABB(pos.getX(), pos.getY() + CrucibleBasin.RIM_Y - WALL_TOP_TOLERANCE, pos.getZ(),
            pos.getX() + 1.0, pos.getY() + CrucibleBasin.RIM_Y + MOUTH_COLUMN_HEIGHT, pos.getZ() + 1.0);
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, top)) {
            Vec3 nudge = inwardNudge(item.getX() - pos.getX(), item.getY() - pos.getY(), item.getZ() - pos.getZ());
            if (nudge.equals(Vec3.ZERO)) { continue; }
            item.setDeltaMovement(nudgedDelta(item.getDeltaMovement(), nudge));
            item.needsSync = true;
        }
    }

    /**
     * @param delta the entity's motion this tick
     * @param nudge the inward nudge
     * @return the motion with the nudge added, the rest kept
     */
    static Vec3 nudgedDelta(Vec3 delta, Vec3 nudge) {
        return delta.add(nudge);
    }

    /**
     * The horizontal velocity toward the basin center for an item on a rim wall top
     * or in the air column over the mouth, zero anywhere else.
     *
     * @param x the item's X relative to the block
     * @param y the item's feet Y relative to the block
     * @param z the item's Z relative to the block
     * @return the nudge to add to the item's motion this tick
     */
    public static Vec3 inwardNudge(double x, double y, double z) {
        if (!onWallTop(x, y, z) && !overMouth(x, y, z)) { return Vec3.ZERO; }
        return new Vec3((BASIN_CENTER - x) * NUDGE_GAIN, 0.0, (BASIN_CENTER - z) * NUDGE_GAIN);
    }

    /**
     * @param x the item's X relative to the block
     * @param y the item's feet Y relative to the block
     * @param z the item's Z relative to the block
     * @return true when the item rests at rim height with its footprint over the collar
     */
    private static boolean onWallTop(double x, double y, double z) {
        return Math.abs(y - CrucibleBasin.RIM_Y) <= WALL_TOP_TOLERANCE
            && overCollar(x) && overCollar(z);
    }

    /**
     * @param x the item's X relative to the block
     * @param y the item's feet Y relative to the block
     * @param z the item's Z relative to the block
     * @return true when the item hangs in the air column over the mouth, above the rim
     */
    private static boolean overMouth(double x, double y, double z) {
        return y >= CrucibleBasin.RIM_Y && y <= CrucibleBasin.RIM_Y + MOUTH_COLUMN_HEIGHT
            && withinMouth(x) && withinMouth(z);
    }

    /**
     * @param coordinate a block-relative X or Z of the item's center
     * @return true when an item centered there overlaps the collar
     */
    private static boolean overCollar(double coordinate) {
        return coordinate > CrucibleShape.COLLAR_MIN - ITEM_HALF_WIDTH
            && coordinate < CrucibleShape.COLLAR_MAX + ITEM_HALF_WIDTH;
    }

    /**
     * @param coordinate a block-relative X or Z of the item's center
     * @return true when it lies over the mouth
     */
    private static boolean withinMouth(double coordinate) {
        return coordinate >= CrucibleBasin.FOOTPRINT_MIN && coordinate <= CrucibleBasin.FOOTPRINT_MAX;
    }
}
