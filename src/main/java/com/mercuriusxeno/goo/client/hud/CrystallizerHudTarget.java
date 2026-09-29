package com.mercuriusxeno.goo.client.hud;

import com.mercuriusxeno.goo.block.ShapeHitCheck;
import com.mercuriusxeno.goo.block.crystallizer.CrystallizerShapes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * Decides whether a crosshair hit on a crystallizer targets its crystal, the part the
 * crystal HUD reports on, rather than the body or the dial (decision crystal-hud-shows-on-crystal-look).
 */
final class CrystallizerHudTarget {

    /** Far above the rounding a block-local subtraction leaves, far below a pixel. */
    private static final double FACE_TOLERANCE = 1e-7;

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
        if (crystal.isEmpty()) {
            return null;
        }
        // A crosshair hit lies on the crystal's face, and subtracting the block position can round it just outside.
        VoxelShape faceTolerant = Shapes.create(crystal.bounds().inflate(FACE_TOLERANCE));
        return ShapeHitCheck.hitInsideShape(hit, hit.getBlockPos(), faceTolerant) ? hit.getBlockPos() : null;
    }
}
