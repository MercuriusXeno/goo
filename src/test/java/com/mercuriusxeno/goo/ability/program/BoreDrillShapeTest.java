package com.mercuriusxeno.goo.ability.program;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The drill's cross section: the ellipse where the cone, as wide at the blob
 * as its width, as long as its length and tapering to a point, held at its
 * angle off the look with its past length beyond the plane, crosses the
 * plane square to the look, its long axis toward the blob's side of the
 * sight line (decisions bore-cross-section-is-where-the-angled-drill-meets-the-look-plane,
 * bore-drill-dimensions-drive-the-visuals-and-the-break).
 */
class BoreDrillShapeTest {

    private static final double MEASURE_SLACK = 0.005;
    private static final Vec3 LOOK_SOUTH = new Vec3(0, 0, 1);

    @Test
    void theShippedDrillsCrossSectionMeasuresPointThreeSixByPointThreeFive() {
        assertEquals(0.363, 2 * BoreDrillShape.SHIPPED.longHalf(), MEASURE_SLACK);
        assertEquals(0.350, 2 * BoreDrillShape.SHIPPED.shortHalf(), MEASURE_SLACK);
    }

    @Test
    void aPastLengthOfHalfABlockMeasuresPointOneEightByPointOneSevenFive() {
        BoreDrillShape shape = new BoreDrillShape(0.7, 2, 15, 0.5);
        assertEquals(0.182, 2 * shape.longHalf(), MEASURE_SLACK);
        assertEquals(0.175, 2 * shape.shortHalf(), MEASURE_SLACK);
    }

    @Test
    void theLongAxisLiesTowardTheBlobsSideForAGloveInEitherHand() {
        // looking south, a player's right is west, -x; the blob rests to the glove's side and below
        Vec3 right = BoreDrillShape.towardTheBlob(LOOK_SOUTH, true);
        Vec3 left = BoreDrillShape.towardTheBlob(LOOK_SOUTH, false);
        assertTrue(right.x < 0 && right.y < 0, "a right hand's blob side reads " + right);
        assertTrue(left.x > 0 && left.y < 0, "a left hand's blob side reads " + left);
        assertEquals(0, right.dot(LOOK_SOUTH), 1e-9);
    }
}
