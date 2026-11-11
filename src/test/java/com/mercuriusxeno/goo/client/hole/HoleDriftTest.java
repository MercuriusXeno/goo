package com.mercuriusxeno.goo.client.hole;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the drift a stored item swims on inside an open hole: every item lies wholly behind the mouth's
 * face and inside its footprint, moves smoothly from one moment to the next, sits at a depth of its own,
 * and keeps farther from every other than the first pass's single ring kept them.
 * decision hole-items-are-a-three-dimensional-soup-sunk-in-the-hole
 */
class HoleDriftTest {

    private static final int INDICES = HoleDrift.MOST_SHOWN;
    private static final float SPAN = 2400F;
    private static final float SAMPLE = 0.25F;
    /** The most an offset may move in one sample, a smooth swim rather than a jump. */
    private static final double STEP = 0.01;
    /** A 3x3 mouth's half span each way across its face. */
    private static final double THREE_WIDE = 1.5;
    /** A 1x1 mouth's half span each way across its face. */
    private static final double ONE_WIDE = 0.5;
    /**
     * The smallest distance between any two of twelve items under the first pass's drift, at its one-block
     * mouth: measured once by running HoleDrift.offset(index, time) as it stood at commit b4a968c over
     * indices 0 to 11 and times 0 to 2400 ticks in steps of 0.25, taking the least pairwise distance of
     * every sample, which read 0.04805407425277197.
     */
    private static final double FIRST_PASS_LEAST_APART = 0.04805407425277197;

    @Test
    void everyItemLiesWhollyBehindTheFaceAndInsideTheFootprint() {
        for (double half : new double[] {THREE_WIDE, ONE_WIDE}) {
            for (int index = 0; index < INDICES; index++) {
                for (float time = 0; time < SPAN; time += SAMPLE) {
                    Vec3 at = HoleDrift.offset(index, time, half, half);
                    double reach = HoleDrift.DRAWN_RADIUS;
                    String where = "index " + index + " at " + time + " in a mouth " + half * 2 + " wide sits at " + at;
                    assertTrue(at.y() + reach <= 0, where + ", out of the face");
                    assertTrue(at.y() - reach >= -HoleDrift.PIT_DEPTH, where + ", below the pit");
                    assertTrue(Math.abs(at.x()) + reach <= half && Math.abs(at.z()) + reach <= half,
                            where + ", outside the footprint");
                }
            }
        }
    }

    @Test
    void neighbouringSamplesDifferByLessThanAStep() {
        for (double half : new double[] {THREE_WIDE, ONE_WIDE}) {
            for (int index = 0; index < INDICES; index++) {
                for (float time = 0; time < SPAN; time += SAMPLE) {
                    double moved = HoleDrift.offset(index, time, half, half)
                            .distanceTo(HoleDrift.offset(index, time + SAMPLE, half, half));
                    assertTrue(moved < STEP, "index " + index + " jumps " + moved + " at " + time);
                }
            }
        }
    }

    @Test
    void theItemsSpreadThroughMoreThanAnItemsDepth() {
        for (double half : new double[] {THREE_WIDE, ONE_WIDE}) {
            for (float time = 0; time < SPAN; time += SAMPLE) {
                double shallowest = -Double.MAX_VALUE;
                double deepest = Double.MAX_VALUE;
                for (int index = 0; index < INDICES; index++) {
                    double depth = HoleDrift.offset(index, time, half, half).y();
                    shallowest = Math.max(shallowest, depth);
                    deepest = Math.min(deepest, depth);
                }
                assertTrue(shallowest - deepest > HoleDrift.ITEM_SIZE, "depths span " + (shallowest - deepest)
                        + " at " + time + " in a mouth " + half * 2 + " wide");
            }
        }
    }

    /**
     * The mouth's open share moves no item and resizes none, through the opening span and the closing one.
     * decision hole-seals-over-items-of-fixed-scale
     */
    @Test
    void theOpenShareChangesNoItemsPlaceOrSize() {
        float moment = 137.5F;
        for (float open : new float[] {0.1F, 0.5F, 1F}) {
            for (int index = 0; index < INDICES; index++) {
                HoleDrift.Placement placement = HoleDrift.placementAt(index, moment, THREE_WIDE, THREE_WIDE, open);
                String where = "index " + index + " at open share " + open;
                assertEquals(HoleDrift.offset(index, moment, THREE_WIDE, THREE_WIDE), placement.offset(), where);
                assertEquals(HoleDrift.ITEM_SIZE, placement.size(), where);
            }
        }
        assertEquals(0, HoleDrift.placementAt(0, moment, THREE_WIDE, THREE_WIDE, 0F).size(), "a shut mouth's item");
    }

    @Test
    void itemsKeepFartherApartThanTheFirstPassRing() {
        double least = Double.MAX_VALUE;
        for (float time = 0; time < SPAN; time += SAMPLE) {
            for (int first = 0; first < INDICES; first++) {
                for (int second = first + 1; second < INDICES; second++) {
                    least = Math.min(least, HoleDrift.offset(first, time, THREE_WIDE, THREE_WIDE)
                            .distanceTo(HoleDrift.offset(second, time, THREE_WIDE, THREE_WIDE)));
                }
            }
        }
        assertTrue(least > FIRST_PASS_LEAST_APART, "items come within " + least + " of each other");
    }
}
