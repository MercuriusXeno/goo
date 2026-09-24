package com.mercuriusxeno.goo.block.tap;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The spigot nozzle's box in block pixels, kept apart from {@link TapBlock}
 * so the drip's geometry reads without a Minecraft bootstrap. The box sits
 * centered on the block's vertical axis, so every facing rotates it onto
 * itself and one underside point serves all four.
 */
final class TapSpigot {

    /** Low X and Z edge of the spigot, in pixels. */
    static final double MIN_XZ_PX = 6;
    /** High X and Z edge of the spigot, in pixels. */
    static final double MAX_XZ_PX = 10;
    /** Underside of the spigot, in pixels. */
    static final double BOTTOM_PX = 2;
    /** Top of the spigot, in pixels. */
    static final double TOP_PX = 4;
    /** Pixels per block. */
    private static final double PIXELS_PER_BLOCK = 16;

    private TapSpigot() {
    }

    /**
     * The spigot box in world coordinates for the tap at a position.
     *
     * @param pos the tap's block position
     * @return the spigot box
     */
    static AABB box(BlockPos pos) {
        return new AABB(MIN_XZ_PX / PIXELS_PER_BLOCK, BOTTOM_PX / PIXELS_PER_BLOCK, MIN_XZ_PX / PIXELS_PER_BLOCK,
                MAX_XZ_PX / PIXELS_PER_BLOCK, TOP_PX / PIXELS_PER_BLOCK, MAX_XZ_PX / PIXELS_PER_BLOCK).move(pos);
    }

    /**
     * The center of the spigot's underside, where a drip leaves the tap.
     *
     * @param pos the tap's block position
     * @return the drip's starting point
     */
    static Vec3 underside(BlockPos pos) {
        AABB spigot = box(pos);
        return new Vec3(spigot.getCenter().x, spigot.minY, spigot.getCenter().z);
    }
}
