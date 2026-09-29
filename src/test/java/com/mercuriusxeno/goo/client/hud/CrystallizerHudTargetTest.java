package com.mercuriusxeno.goo.client.hud;

import com.mercuriusxeno.goo.block.crystallizer.CrystallizerShapes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Covers which crosshair hits on a crystallizer target its crystal HUD: the crystal, never the body or the dial. */
class CrystallizerHudTargetTest {

    private static final BlockPos POS = new BlockPos(10, 64, -3);
    private static final Direction FACING = Direction.NORTH;
    private static final long GROWING = 1000;
    /** The dial's center on the north face, in block-local coordinates. */
    private static final Vec3 DIAL = new Vec3(0.5, 7.5 / 16.0, 0.0);
    /** A point on the body's east side, below the crystal. */
    private static final Vec3 BODY_SIDE = new Vec3(15.0 / 16.0, 0.5, 0.5);

    private static BlockHitResult hitAt(Vec3 local, Direction face) {
        return new BlockHitResult(local.add(Vec3.atLowerCornerOf(POS)), face, POS, false);
    }

    private static Vec3 crystalCenter() {
        AABB crystal = CrystallizerShapes.crystalShape(FACING, GROWING).bounds();
        return crystal.getCenter();
    }

    @Test
    void hitOnTheCrystalTargetsTheCrystallizer() {
        assertEquals(POS, CrystallizerHudTarget.crystalTarget(hitAt(crystalCenter(), Direction.UP), FACING, GROWING));
    }

    @Test
    void hitOnTheBodySideTargetsNothing() {
        assertNull(CrystallizerHudTarget.crystalTarget(hitAt(BODY_SIDE, Direction.EAST), FACING, GROWING));
    }

    @Test
    void hitOnTheDialTargetsNothing() {
        assertNull(CrystallizerHudTarget.crystalTarget(hitAt(DIAL, Direction.NORTH), FACING, GROWING));
    }

    /** The hit the dev client logged on the crystal's north face, which rounded outside the box on subtraction. */
    @Test
    void hitOnTheCrystalFaceTargetsTheCrystallizer() {
        BlockPos pos = new BlockPos(7, -60, 3);
        Vec3 location = new Vec3(7.495647874417606, -58.93500404420586, 3.2480794270833333);
        BlockHitResult hit = new BlockHitResult(location, Direction.NORTH, pos, false);

        assertEquals(pos, CrystallizerHudTarget.crystalTarget(hit, FACING, 9200));
    }

    @Test
    void emptyCrystallizerTargetsNothing() {
        assertNull(CrystallizerHudTarget.crystalTarget(hitAt(crystalCenter(), Direction.UP), FACING, 0));
    }
}
