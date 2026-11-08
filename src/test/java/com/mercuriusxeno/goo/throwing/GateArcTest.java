package com.mercuriusxeno.goo.throwing;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A throw's arc past a gate splits there into two legs, from the hand to the
 * gate's face and from the partner to the target, one curve over their
 * summed length, so the far leg carries on the near leg's curve
 * (decision aim-and-shots-carry-through-linked-gates).
 */
class GateArcTest {

    private static final Vec3 HAND = new Vec3(0.5, 1.5, -6.0);
    private static final Vec3 FACE = new Vec3(0.5, 1.5, 0.0);
    private static final Vec3 PARTNER = new Vec3(20.5, 1.5, 3.0);
    private static final Vec3 TARGET = new Vec3(20.5, 1.5, 9.0);
    private static final GateArc.Hop HOP = new GateArc.Hop(FACE, PARTNER);
    private static final double PEAK = 1.5;
    private static final int SEGMENTS = 24;
    private static final double CLOSE = 1.0E-6;

    @Test
    void theLegsRunHandToFaceAndPartnerToTarget() {
        List<Vec3[]> legs = GateArc.legs(HAND, HOP, TARGET, 0, SEGMENTS);
        assertEquals(2, legs.size(), "A target past a gate should draw two legs");
        assertClose(HAND, legs.get(0)[0], "the near leg's start");
        assertClose(FACE, legs.get(0)[legs.get(0).length - 1], "the near leg's end, on the gate's face");
        assertClose(PARTNER, legs.get(1)[0], "the far leg's start, out of the partner");
        assertClose(TARGET, legs.get(1)[legs.get(1).length - 1], "the far leg's end, at the target");
    }

    @Test
    void theFarLegCarriesOnTheNearLegsCurve() {
        List<Vec3[]> legs = GateArc.legs(HAND, HOP, TARGET, PEAK, SEGMENTS);
        Vec3[] near = legs.get(0);
        Vec3[] far = legs.get(1);
        double nearEndsAbove = near[near.length - 1].y() - FACE.y();
        double farStartsAbove = far[0].y() - PARTNER.y();
        assertTrue(nearEndsAbove > CLOSE, "The arc should stand above its chord where it meets the gate, stands "
                + nearEndsAbove);
        assertEquals(nearEndsAbove, farStartsAbove, CLOSE, "The far leg should start as high above its chord as "
                + "the near leg ends");
    }

    @Test
    void aBeamDrawsTwoStraightLegs() {
        List<Vec3[]> legs = GateArc.legs(HAND, HOP, TARGET, 0, SEGMENTS);
        for (Vec3 point : legs.get(0)) {
            assertEquals(0, distanceOffChord(point, HAND, FACE), CLOSE, "The near leg of a beam should be straight");
        }
        for (Vec3 point : legs.get(1)) {
            assertEquals(0, distanceOffChord(point, PARTNER, TARGET), CLOSE, "The far leg of a beam should be "
                    + "straight");
        }
    }

    @Test
    void aTargetWithNoGateDrawsOneLeg() {
        List<Vec3[]> legs = GateArc.legs(HAND, null, TARGET, PEAK, SEGMENTS);
        assertEquals(1, legs.size(), "A near-side target should draw one leg");
        assertClose(HAND, legs.get(0)[0], "the leg's start");
        assertClose(TARGET, legs.get(0)[legs.get(0).length - 1], "the leg's end");
    }

    private static double distanceOffChord(Vec3 point, Vec3 from, Vec3 to) {
        Vec3 along = to.subtract(from).normalize();
        Vec3 offset = point.subtract(from);
        return offset.subtract(along.scale(offset.dot(along))).length();
    }

    private static void assertClose(Vec3 expected, Vec3 actual, String what) {
        assertTrue(expected.distanceTo(actual) < CLOSE, what + " should stand at " + expected + ", stands at "
                + actual);
    }
}
