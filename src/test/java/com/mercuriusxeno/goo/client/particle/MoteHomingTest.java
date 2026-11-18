package com.mercuriusxeno.goo.client.particle;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A loose void mote flies fast and straight from where it starts in Vacuum's
 * cone into the blob, homing on the blob where it stands each tick, so it
 * lands on it even while the caster moves
 * (decision vacuum-motes-fly-loose-from-the-cone-to-the-blob).
 */
class MoteHomingTest {

    private static final double CLOSE = 1e-9;
    /** Vacuum's reach: a mote at the cone's far end starts this far from the blob. */
    private static final double RANGE = 9;
    private static final Vec3 BLOB = new Vec3(0, 65, 0);
    private static final Vec3 FARTHEST = BLOB.add(new Vec3(3, 1, 7).normalize().scale(RANGE));

    @Test
    void aMoteFromTheConesFarthestPointFliesStraightOntoTheBlobWithinItsFlight() {
        int flight = MoteHoming.flightTicks(FARTHEST, BLOB);
        assertTrue(flight >= 1 && flight <= MoteHoming.MOTE_FLIGHT_TICKS, "the mote flies " + flight + " ticks");
        Vec3 at = FARTHEST;
        for (int left = flight; left >= 1; left--) {
            Vec3 step = MoteHoming.stepToward(at, BLOB, left);
            assertEquals(1, step.normalize().dot(BLOB.subtract(at).normalize()), CLOSE,
                    "with " + left + " ticks left the step should point at the blob");
            at = at.add(step);
        }

        assertEquals(0, at.distanceTo(BLOB), CLOSE, "the mote should end on the blob, " + at);
    }

    @Test
    void aMoteNearTheBlobLandsSoonerAtTheSamePace() {
        Vec3 near = BLOB.add(1, 0, 1);
        assertTrue(MoteHoming.flightTicks(near, BLOB) < MoteHoming.flightTicks(FARTHEST, BLOB),
                "a near mote should not crawl over the whole flight");
        assertTrue(MoteHoming.flightTicks(near, BLOB) >= 1);
    }

    @Test
    void aMoteLandsOnTheBlobWhereverTheBlobHasMoved() {
        Vec3 at = FARTHEST;
        Vec3 blob = BLOB;
        Vec3 walking = new Vec3(-0.2, 0, 0.1);
        int flight = MoteHoming.flightTicks(FARTHEST, BLOB);
        assertTrue(flight >= 1 && flight <= MoteHoming.MOTE_FLIGHT_TICKS, "the mote flies " + flight + " ticks");
        for (int left = flight; left >= 1; left--) {
            blob = blob.add(walking);
            at = at.add(MoteHoming.stepToward(at, blob, left));
        }

        assertEquals(0, at.distanceTo(blob), CLOSE, "the mote should end on the blob, " + at + " against " + blob);
    }
}
