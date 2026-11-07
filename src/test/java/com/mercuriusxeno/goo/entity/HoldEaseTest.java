package com.mercuriusxeno.goo.entity;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A held hunk eases toward its hold, closing part of the gap each moment, so
 * it glides in rather than jumping to the look
 * (decision rock-grabs-hunks-of-rock-goo-blocks-as-a-huckable-entity).
 */
class HoldEaseTest {

    private static final Vec3 FROM = Vec3.ZERO;
    private static final Vec3 HOLD = new Vec3(2, 0, 0);
    private static final double ONE_FRAME = 1.0 / 60;

    @Test
    void oneFrameClosesOnlyPartOfTheGap() {
        Vec3 eased = HoldEase.toward(FROM, HOLD, ONE_FRAME);
        assertTrue(eased.x > 0 && eased.x < HOLD.x * 0.25, "one frame eased to " + eased);
    }

    @Test
    void aSecondOfEasingAlmostReachesTheHold() {
        Vec3 at = FROM;
        for (int frame = 0; frame < 60; frame++) {
            at = HoldEase.toward(at, HOLD, ONE_FRAME);
        }
        assertEquals(HOLD.x, at.x, 0.01);
    }

    @Test
    void theEaseDoesNotHangOnTheFrameRate() {
        Vec3 slow = HoldEase.toward(FROM, HOLD, 1.0 / 30);
        Vec3 fast = HoldEase.toward(HoldEase.toward(FROM, HOLD, 1.0 / 60), HOLD, 1.0 / 60);
        assertEquals(slow.x, fast.x, 1e-9);
    }
}
