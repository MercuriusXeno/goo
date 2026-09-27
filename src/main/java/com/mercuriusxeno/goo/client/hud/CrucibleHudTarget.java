package com.mercuriusxeno.goo.client.hud;

import com.mercuriusxeno.goo.block.crucible.CrucibleBasin;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Decides whether a crosshair hit on a crucible targets its basin, the part the
 * hud reports on, rather than the fuel-rod area on the body's outer sides.
 */
final class CrucibleHudTarget {

    /** Y threshold in block-local coords: below this is the fuel rod area, not the basin. */
    private static final double BASIN_MIN_Y = 10.0 / 16.0;

    private CrucibleHudTarget() {}

    /**
     * Returns the crucible position a hit on a crucible targets, or null when the
     * hit lands on the fuel-rod area below the basin. A hit inside the drawn
     * cavity, its floor included, targets the crucible (decision diagnose-then-fix-basin-floor-hud).
     *
     * @param hit the crosshair hit on a crucible block
     * @return the hit block's position, or null
     */
    static @Nullable BlockPos basinTarget(BlockHitResult hit) {
        BlockPos pos = hit.getBlockPos();
        Vec3 location = hit.getLocation();
        double localX = location.x - pos.getX();
        double localY = location.y - pos.getY();
        double localZ = location.z - pos.getZ();
        boolean inBasin = localY >= BASIN_MIN_Y || CrucibleBasin.holdsPoint(localX, localY, localZ);
        return inBasin ? pos : null;
    }
}
