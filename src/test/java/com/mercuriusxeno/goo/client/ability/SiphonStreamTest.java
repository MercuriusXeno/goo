package com.mercuriusxeno.goo.client.ability;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A block's goo streams into the soup in a vortex that reaches the ball by
 * half way and leaves the block after
 * (decision unmake-waves-dissolve-by-crucible-cost).
 */
class SiphonStreamTest {

    private static final double DELTA = 1e-9;
    private static final Vec3 FROM = new Vec3(4, 2, 3);
    private static final Vec3 TO = new Vec3(1, 2, 3);
    private static final Vec3 SIDE = new Vec3(0, 0, 1);
    private static final Vec3 UP = new Vec3(0, 1, 0);

    @Test
    void theStrandsReachTheBallByHalfWayThenLeaveTheBlock() {
        assertEquals(0, SiphonStream.spanAt(0).head(), DELTA);
        assertEquals(1, SiphonStream.spanAt(0.5).head(), DELTA);
        assertEquals(0, SiphonStream.spanAt(0.5).tail(), DELTA);
        assertEquals(1, SiphonStream.spanAt(1).tail(), DELTA);
    }

    @Test
    void aStrandSwingsAboutTheLineAtTheBlockAndClosesOnTheBall() {
        Vec3 atBlock = SiphonStream.strandAt(FROM, TO, SIDE, UP, 0, 0, 0);
        Vec3 atBall = SiphonStream.strandAt(FROM, TO, SIDE, UP, 1, 0, 0);

        assertEquals(SiphonStream.SWING, atBlock.distanceTo(FROM), DELTA);
        assertEquals(0, atBall.distanceTo(TO), DELTA);
    }

    @Test
    void theVortexSpins() {
        Vec3 now = SiphonStream.strandAt(FROM, TO, SIDE, UP, 0.3, 0, 0);
        Vec3 later = SiphonStream.strandAt(FROM, TO, SIDE, UP, 0.3, 2, 0);

        assertTrue(now.distanceTo(later) > 0.01);
    }
}
