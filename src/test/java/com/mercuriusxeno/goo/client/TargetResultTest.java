package com.mercuriusxeno.goo.client;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Every aim target carries the exact point aimed at, which the aim line ends
 * on, while a block's bullseye keeps its face's center (decisions
 * aim-point-follows-the-cursor, aim-line-snaps-with-the-tile-highlight).
 */
class TargetResultTest {

    private static final BlockPos POS = new BlockPos(2, 64, 2);

    @Test
    void aFaceHitAimsWhereTheRayMetTheFaceNotItsCenter() {
        Vec3 met = new Vec3(2.15, 64.8, 2.0);
        TargetResult target = TargetResult.block(POS, Direction.NORTH, met);

        assertEquals(met, target.point());
        assertNotEquals(met, target.resolveEndpoint());
    }

    @Test
    void anEntityHitAimsWhereTheRayMetTheEntity() {
        Vec3 met = new Vec3(7.0, 65.3, 1.2);

        assertEquals(met, new TargetResult.EntityTarget(null, met).point());
    }

    @Test
    void aFreeMissAimsTheRaysEnd() {
        Vec3 reach = new Vec3(40, 90, -30);

        assertEquals(reach, TargetResult.pointInAir(reach).point());
    }

    @Test
    void aLobAimsItsTopFacesCenter() {
        assertEquals(new Vec3(2.5, 65.0, 2.5), TargetResult.grannyArc(POS).point());
    }

    @Test
    void nothingAimedNamesNoPoint() {
        assertNull(TargetResult.NONE.point());
    }
}
