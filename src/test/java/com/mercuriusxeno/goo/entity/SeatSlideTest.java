package com.mercuriusxeno.goo.entity;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A let-go chunk come to rest beside its seat slides over to it and falls
 * in, a step at a time, rather than jumping there
 * (decision terrakinesis-release-drops-the-hunk-and-places-it-on-the-grid).
 */
class SeatSlideTest {

    private static final double EPSILON = 1e-9;
    /** A seat in a hole a block under the rim. */
    private static final Vec3 SEAT = new Vec3(10.5, 63, 10.5);
    /** Resting on the rim, a hair less than half a block off the seat's column. */
    private static final Vec3 ON_THE_RIM = new Vec3(10.06, 64, 10.5);

    @Test
    void offTheSeatsColumnItSlidesOverWithoutDropping() {
        Vec3 next = SeatSlide.step(ON_THE_RIM, SEAT, 0.5);
        assertEquals(ON_THE_RIM.y, next.y, EPSILON);
        assertTrue(next.x > ON_THE_RIM.x && next.x - ON_THE_RIM.x <= SeatSlide.SLIDE_STEP + EPSILON,
                "slid " + (next.x - ON_THE_RIM.x));
    }

    @Test
    void overTheSeatItFallsByItsFallSpeed() {
        Vec3 over = new Vec3(SEAT.x, 64, SEAT.z);
        assertEquals(new Vec3(SEAT.x, 63.75, SEAT.z), SeatSlide.step(over, SEAT, 0.25));
    }

    @Test
    void itNeverFallsPastTheSeat() {
        Vec3 over = new Vec3(SEAT.x, 63.1, SEAT.z);
        assertEquals(SEAT, SeatSlide.step(over, SEAT, 0.5));
    }

    @Test
    void aSeatAboveItRisesToBeforeSliding() {
        Vec3 under = new Vec3(9.5, 60, 10.5);
        Vec3 next = SeatSlide.step(under, SEAT, 0.5);
        assertEquals(under.x, next.x, EPSILON);
        assertTrue(next.y > under.y, "rose " + (next.y - under.y));
    }

    @Test
    void itReachesItsSeatExactly() {
        Vec3 at = ON_THE_RIM;
        for (int tick = 0; tick < 40 && !at.equals(SEAT); tick++) {
            at = SeatSlide.step(at, SEAT, 0.5);
        }
        assertEquals(SEAT, at);
    }
}
