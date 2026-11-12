package com.mercuriusxeno.goo.client.outsider;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the rim on the conjured door's torn edge: a thin band whose inner
 * edge lies on every segment of both sides of the rip and whose outer edge
 * stands one fixed width outside it at every size, empty at size nothing
 * (decision rip-wears-a-bright-rim-on-its-torn-edge).
 */
class DoorRimTest {

    private static final double EXACT = 1.0E-9;

    @Test
    void atSizeNothingTheBandIsEmpty() {
        assertTrue(DoorRim.band(DoorCrack.outline(0)).isEmpty(), "no band on no door");
    }

    @Test
    void atEverySizeTheBandHugsEachSegmentOneWidthOutside() {
        for (double size : new double[] {0.1, 0.5, 1}) {
            List<RipRow> outline = DoorCrack.outline(size);
            List<DoorRim.RimQuad> band = DoorRim.band(outline);
            List<double[]> segments = sideSegments(outline);

            assertEquals(segments.size(), band.size(), "one band quad for each side segment at size " + size);
            for (double[] segment : segments) {
                DoorRim.RimQuad quad = band.stream().filter(candidate -> sitsOn(candidate, segment)).findFirst()
                        .orElseThrow(() -> new AssertionError("no band quad on a segment at size " + size));
                assertOneWidthOutside(quad.outerBelow(), segment, "outer below at size " + size);
                assertOneWidthOutside(quad.outerAbove(), segment, "outer above at size " + size);
            }
        }
    }

    /**
     * Each side's segments as {fromAcross, fromUp, toAcross, toUp, outwardSign},
     * the sign negative on the left side, positive on the right; a corner's
     * distance outside a segment is signed so the rip's side of it reads negative.
     */
    private static List<double[]> sideSegments(List<RipRow> outline) {
        List<double[]> segments = new ArrayList<>();
        for (int index = 1; index < outline.size(); index++) {
            RipRow below = outline.get(index - 1);
            RipRow above = outline.get(index);
            segments.add(new double[] {below.leftAcross(), below.up(), above.leftAcross(), above.up(), -1});
            segments.add(new double[] {below.rightAcross(), below.up(), above.rightAcross(), above.up(), 1});
        }
        return segments;
    }

    private static boolean sitsOn(DoorRim.RimQuad quad, double[] segment) {
        return near(quad.innerBelow(), segment[0], segment[1]) && near(quad.innerAbove(), segment[2], segment[3])
                && Math.signum(quad.outerBelow().across() - quad.innerBelow().across()) == segment[4];
    }

    private static boolean near(DoorRim.RimCorner corner, double across, double up) {
        return Math.abs(corner.across() - across) < EXACT && Math.abs(corner.up() - up) < EXACT;
    }

    private static void assertOneWidthOutside(DoorRim.RimCorner corner, double[] segment, String which) {
        double alongAcross = segment[2] - segment[0];
        double alongUp = segment[3] - segment[1];
        double length = Math.hypot(alongAcross, alongUp);
        double offAcross = corner.across() - segment[0];
        double offUp = corner.up() - segment[1];
        double outsideBy = segment[4] * (alongUp * offAcross - alongAcross * offUp) / length;
        assertEquals(DoorRim.WIDTH, outsideBy, EXACT, which + " stands one rim width outside its segment");
    }
}
