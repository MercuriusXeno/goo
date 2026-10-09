package com.mercuriusxeno.goo.client.ability;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The held mote cloud Shroom and Pulse draw keeps every mote within its fuzzy shell, leaves no
 * stretch of the sphere bare, moves its motes on their own, and twinkles them
 * between dim and full (decision held-visual-ghosts-the-landing-in-two-passes).
 */
class MoteCloudGhostTest {

    private static final double EPSILON = 1e-9;
    /** No direction over the sphere lies farther than this from a mote, in radians. */
    private static final double LARGEST_GAP = 0.25;
    private static final int PROBE_STEPS = 24;

    private static double length(double[] v) {
        return Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
    }

    private static double distance(double[] a, double[] b) {
        return length(new double[] {a[0] - b[0], a[1] - b[1], a[2] - b[2]});
    }

    @Test
    void everyMoteSitsWithinTheFuzzyShell() {
        for (double seconds : new double[] {0, 1.7, 42.3}) {
            for (int i = 0; i < MoteCloudGhost.MOTES; i++) {
                double reach = length(MoteCloudGhost.moteAt(i, seconds));
                assertTrue(reach >= MoteCloudGhost.INNER_SHARE - MoteCloudGhost.WOBBLE_SHARE - EPSILON
                        && reach <= MoteCloudGhost.OUTER_SHARE + MoteCloudGhost.WOBBLE_SHARE + EPSILON,
                        i + " at " + seconds + ": " + reach);
            }
        }
    }

    @Test
    void noStretchOfTheSphereIsBare() {
        for (int a = 0; a < PROBE_STEPS; a++) {
            for (int b = 1; b < PROBE_STEPS; b++) {
                double around = 2 * Math.PI * a / PROBE_STEPS;
                double down = Math.PI * b / PROBE_STEPS;
                double[] probe = {Math.sin(down) * Math.cos(around), Math.cos(down), Math.sin(down) * Math.sin(around)};
                double nearest = Math.PI;
                for (int i = 0; i < MoteCloudGhost.MOTES; i++) {
                    double[] mote = MoteCloudGhost.moteAt(i, 5.0);
                    double cos = (mote[0] * probe[0] + mote[1] * probe[1] + mote[2] * probe[2]) / length(mote);
                    nearest = Math.min(nearest, Math.acos(Math.max(-1, Math.min(1, cos))));
                }
                assertTrue(nearest < LARGEST_GAP, "bare near " + around + ", " + down + ": " + nearest);
            }
        }
    }

    @Test
    void motesMoveOnTheirOwn() {
        double firstMoved = distance(MoteCloudGhost.moteAt(0, 0), MoteCloudGhost.moteAt(0, 10));
        double secondMoved = distance(MoteCloudGhost.moteAt(1, 0), MoteCloudGhost.moteAt(1, 10));
        assertTrue(firstMoved > 0.01 && secondMoved > 0.01 && Math.abs(firstMoved - secondMoved) > 1e-3,
                firstMoved + " " + secondMoved);
    }

    @Test
    void motesTwinkleBetweenDimAndFull() {
        for (int i = 0; i < MoteCloudGhost.MOTES; i++) {
            float share = MoteCloudGhost.twinkleAt(i, 3.3);
            assertTrue(share >= MoteCloudGhost.DIMMEST - 1e-6 && share <= 1f + 1e-6, String.valueOf(share));
        }
    }
}
