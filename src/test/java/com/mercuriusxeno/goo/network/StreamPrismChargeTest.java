package com.mercuriusxeno.goo.network;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A charging prism's motes fly from the glove along the line to the prism's
 * middle (decision channeled-hosts-convert-prisms-over-the-hold).
 */
class StreamPrismChargeTest {

    private static final double EPSILON = 1e-9;

    @Test
    void theMotesAimFromTheApexAtThePrismsMiddle() {
        Vec3 apex = new Vec3(0.2, 2.4, 0.7);
        BlockPos prism = new BlockPos(4, 1, -3);

        Vec3 axis = StreamPrismCharge.moteAxis(apex, prism);

        Vec3 toMiddle = Vec3.atCenterOf(prism).subtract(apex).normalize();
        assertEquals(toMiddle.x, axis.x, EPSILON);
        assertEquals(toMiddle.y, axis.y, EPSILON);
        assertEquals(toMiddle.z, axis.z, EPSILON);
    }

    @Test
    void anApexOnThePrismsMiddleAimsStraightUp() {
        BlockPos prism = new BlockPos(4, 1, -3);

        assertEquals(new Vec3(0, 1, 0), StreamPrismCharge.moteAxis(Vec3.atCenterOf(prism), prism));
    }
}
