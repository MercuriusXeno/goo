package com.mercuriusxeno.goo.client.particle;

import com.mercuriusxeno.goo.network.FallingDripPayload;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The falling drip a client launches from a fall's payload: it leaves the
 * start point from rest, falling under the drip's gravity as the server's
 * fall reckons, and lives out the fall's ticks, so it reaches the landing
 * on the fall's last tick and splats there (decision goo-moving-downward-falls-as-drips).
 */
class FallingDripLaunchTest {

    private static final FallingDripPayload FALL = new FallingDripPayload(new Vec3(12.5, 70.25, -3.5), 66.0667, 23,
            0x51A03E, 0xC9FECE);

    @Test
    void theDripStartsAtTheFallsStartPoint() {
        assertEquals(FALL.start(), FallingDripLaunch.of(FALL).start());
    }

    @Test
    void theDripLeavesFromRest() {
        assertEquals(Vec3.ZERO, FallingDripLaunch.of(FALL).velocity());
    }

    @Test
    void theDripLivesUntilItSplatsOnTheFallsLastTick() {
        assertEquals(FALL.ticks() + FallingDripLaunch.SPLAT_TICK, FallingDripLaunch.of(FALL).lifetime());
    }
}
