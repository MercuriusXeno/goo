package com.mercuriusxeno.goo.client.hole;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the drift a stored item swims on inside an open hole's mouth: every offset stays inside the
 * mouth, moves smoothly from one moment to the next, and keeps clear of every other item's.
 * decision hole-is-an-in-world-portable-hole-for-items
 */
class HoleDriftTest {

    private static final int INDICES = HoleDrift.MOST_SHOWN;
    private static final float SPAN = 2400F;
    private static final float SAMPLE = 0.25F;
    /** The most an offset may move in one sample, a smooth swim rather than a jump. */
    private static final double STEP = 0.01;
    /** The least two items stand apart at any moment. */
    private static final double APART = 1.0E-3;

    @Test
    void everyOffsetStaysInsideTheMouth() {
        for (int index = 0; index < INDICES; index++) {
            for (float time = 0; time < SPAN; time += SAMPLE) {
                Vec3 at = HoleDrift.offset(index, time);
                double across = Math.hypot(at.x(), at.z());
                assertTrue(across <= HoleDrift.REACH, "index " + index + " at " + time + " strays " + across);
                assertTrue(at.y() >= 0 && at.y() <= HoleDrift.MOST_LIFT, "index " + index + " at " + time
                        + " lifts " + at.y());
            }
        }
    }

    @Test
    void neighbouringSamplesDifferByLessThanAStep() {
        for (int index = 0; index < INDICES; index++) {
            for (float time = 0; time < SPAN; time += SAMPLE) {
                double moved = HoleDrift.offset(index, time).distanceTo(HoleDrift.offset(index, time + SAMPLE));
                assertTrue(moved < STEP, "index " + index + " jumps " + moved + " at " + time);
            }
        }
    }

    @Test
    void twoIndicesNeverCoincide() {
        for (float time = 0; time < SPAN; time += SAMPLE) {
            for (int first = 0; first < INDICES; first++) {
                for (int second = first + 1; second < INDICES; second++) {
                    double apart = HoleDrift.offset(first, time).distanceTo(HoleDrift.offset(second, time));
                    assertTrue(apart > APART, first + " and " + second + " meet at " + time);
                }
            }
        }
    }
}
