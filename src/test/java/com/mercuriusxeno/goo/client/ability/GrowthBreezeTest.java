package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.throwing.ConeShape;
import com.mercuriusxeno.goo.throwing.StreamCone;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Growth's breeze fills the cone it is handed, its sections cut from the shape's widths across and up and down
 * out to the reach (decisions growth-breeze-ticks-plants, spray-abilities-render-procedurally-bound-to-their-cone).
 */
class GrowthBreezeTest {

    private static final SprayCone FLAT = new SprayCone(new Vec3(0.4, -0.3, 0.2), new Vec3(0.3, -0.2, 1).normalize(),
            new ConeShape(60, 10, false), 7);
    /** The rim's widest point across stands within this share of the half width. */
    private static final double WIDEST_SHARE = 0.99;

    @Test
    void everyPointTheBreezeIsDrawnThroughStandsInsideAFlatConeAndItsReach() {
        List<Vec3> points = pointsOf(FLAT);
        assertFalse(points.isEmpty());
        for (Vec3 point : points) {
            assertTrue(StreamCone.contains(FLAT.apex(), FLAT.axis(), FLAT.reach(), FLAT.shape(), point),
                    "the breeze is drawn through " + point + ", outside the cone");
        }
    }

    @Test
    void everyPointTheBreezeIsDrawnThroughStandsInsideABand() {
        SprayCone band = new SprayCone(FLAT.apex(), FLAT.axis(), new ConeShape(60, 8, true), FLAT.reach());
        for (Vec3 point : pointsOf(band)) {
            assertTrue(StreamCone.contains(band.apex(), band.axis(), band.reach(), band.shape(), point),
                    "the breeze is drawn through " + point + ", outside the band");
        }
    }

    /** The rim reaches out across to the shape's half width, so the breeze fills the cone rather than its axis. */
    @Test
    void theBreezesRimReachesTheShapesWidthAcross() {
        Vec3 side = StreamCone.side(FLAT.axis());
        double widest = 0;
        for (Vec3 point : pointsOf(FLAT)) {
            Vec3 from = point.subtract(FLAT.apex());
            widest = Math.max(widest, Math.toDegrees(Math.atan2(from.dot(side), from.dot(FLAT.axis()))));
        }
        assertTrue(widest > FLAT.shape().sideDegrees() / 2 * WIDEST_SHARE, "the rim reaches only " + widest);
    }

    /** Every vertex the breeze's sections are drawn through, before the emitter rounds it to a float. */
    private static List<Vec3> pointsOf(SprayCone cone) {
        List<Vec3> points = new ArrayList<>();
        ConeSections.eachVertex(GrowthBreeze.breezeOf(cone), (at, color) -> points.add(at));
        return points;
    }
}
