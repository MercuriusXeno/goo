package com.mercuriusxeno.goo.client.hud;

import com.mercuriusxeno.goo.block.crucible.CrucibleBasin;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Covers which crosshair hits on a crucible target its hud: the basin, never the fuel-rod area. */
class CrucibleHudTargetTest {

    private static final BlockPos POS = new BlockPos(10, 64, -3);
    private static final double CENTER = 0.5;
    /** A height on the body's outer side below the ledge, where the fuel rods show. */
    private static final double FUEL_ROD_Y = 5.0 / 16.0;

    private static BlockHitResult hitAt(double x, double y, double z, Direction face) {
        Vec3 location = new Vec3(POS.getX() + x, POS.getY() + y, POS.getZ() + z);
        return new BlockHitResult(location, face, POS, false);
    }

    @Test
    void basinFloorHitFromAboveTargetsTheCrucible() {
        BlockHitResult hit = hitAt(CENTER, CrucibleBasin.FLOOR_Y, CENTER, Direction.UP);
        assertEquals(POS, CrucibleHudTarget.basinTarget(hit));
    }

    @Test
    void outerSideHitBelowTheBasinTargetsNothing() {
        BlockHitResult hit = hitAt(0.0, FUEL_ROD_Y, CENTER, Direction.WEST);
        assertNull(CrucibleHudTarget.basinTarget(hit));
    }
}
