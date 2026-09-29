package com.mercuriusxeno.goo.client.hud;

import com.mercuriusxeno.goo.block.ShapeHitCheck;
import com.mercuriusxeno.goo.block.crystallizer.CrystallizerShapes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * Decides whether a crosshair hit on a crystallizer targets its crystal, the part the
 * crystal HUD reports on, rather than the body or the dial (decision crystal-hud-shows-on-crystal-look).
 */
final class CrystallizerHudTarget {

    private CrystallizerHudTarget() {
    }

    /**
     * @param hit          the crosshair hit on a crystallizer block
     * @param facing       the face the crystallizer's dial sits on
     * @param crystallized the crystallized volume, in mB
     * @return the hit crystallizer's position when the hit lands inside its crystal, else null
     */
    static @Nullable BlockPos crystalTarget(BlockHitResult hit, Direction facing, long crystallized) {
        VoxelShape crystal = CrystallizerShapes.crystalShape(facing, crystallized);
        if (crystal.isEmpty() || !ShapeHitCheck.hitInsideShape(hit, hit.getBlockPos(), crystal)) {
            return null;
        }
        return hit.getBlockPos();
    }
}
