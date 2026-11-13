package com.mercuriusxeno.goo.client.ability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/** Vitality's fog fills a cone from the glove along the aim whose rim undulates in a wave rolling outward (decision vitality-waves-regenerate-and-court). */
class VitalityVisualTest {

    private static final double DELTA = 1e-9;
    private static final Vec3 APEX = new Vec3(1, 2, 3);
    private static final Vec3 EAST = new Vec3(1, 0, 0);
    private static final double RANGE = 6;
    private static final double CONE = 50;
    private static final Vec3 FEET = new Vec3(10, 64, 10);
    private static final double STAR_HEIGHT = 1.8;
    private static final float STAR_YAW = 60f;

    @Test
    void fogAtTheStartOfTheConeSitsOnTheGlove() {
        assertEquals(APEX, VitalityVisual.fogPoint(APEX, EAST, RANGE, CONE, 0, 1, 1.0, 0));
    }

    @Test
    void fogOnTheAxisRunsAlongTheAim() {
        Vec3 at = VitalityVisual.fogPoint(APEX, EAST, RANGE, CONE, 0.5, 0, 0, 0);
        assertEquals(new Vec3(4, 2, 3), at);
    }

    @Test
    void fogAtTheRimSitsAtTheConesAngleScaledByTheUndulation() {
        double time = 3;
        Vec3 at = VitalityVisual.fogPoint(APEX, EAST, RANGE, CONE, 1, 1, 0, time);
        double expected = RANGE * Math.tan(Math.toRadians(CONE / 2)) * VitalityVisual.undulation(1, time);
        assertEquals(expected, at.subtract(APEX.add(EAST.scale(RANGE))).length(), DELTA);
    }

    /** At its crest the undulation swells the rim by a quarter, at its trough it shrinks it by a quarter. */
    @Test
    void theUndulationSwellsTheRimByAQuarterAtItsCrestAndShrinksItByAQuarterAtItsTrough() {
        assertEquals(1.25, VitalityVisual.undulation(1.0 / 6, 0), DELTA);
        assertEquals(0.75, VitalityVisual.undulation(0.5, 0), DELTA);
    }

    @Test
    void theUndulationRollsOutwardOverTime() {
        double along = 0.4;
        double later = along + VitalityVisual.ROLL_PER_TICK / (2 * Math.PI * VitalityVisual.WAVES_ALONG);
        assertEquals(VitalityVisual.undulation(along, 0), VitalityVisual.undulation(later, 1), DELTA);
    }

    @Test
    void anAimStraightUpStillOpensACone() {
        Vec3 at = VitalityVisual.fogPoint(APEX, new Vec3(0, 1, 0), RANGE, CONE, 1, 1, 0, 0);
        assertTrue(at.subtract(APEX.add(0, RANGE, 0)).length() > 0);
    }

    /**
     * A first-person caster facing yaw 60 sees a star on the lowest rolls 60 degrees to one side of their facing,
     * at yaw 0, 0.7 out past the near clip and at the waist, half their 1.8 height.
     */
    @Test
    void aFirstPersonCastersLowestRollStarRisesAtTheArcsEdgeNearAndAtTheWaist() {
        Vec3 at = VitalityVisual.starAt(FEET, STAR_HEIGHT, STAR_YAW, 0, 0, 0);
        assertEquals(FEET.x, at.x, DELTA);
        assertEquals(FEET.y + 0.9, at.y, DELTA);
        assertEquals(FEET.z + 0.7, at.z, DELTA);
    }

    /**
     * A first-person caster facing yaw 60 sees a star on the highest rolls 60 degrees to the other side, at yaw 120,
     * 1.2 out and just over the head, 1.15 of their 1.8 height.
     */
    @Test
    void aFirstPersonCastersHighestRollStarRisesAtTheArcsOtherEdgeFarAndOverTheHead() {
        Vec3 at = VitalityVisual.starAt(FEET, STAR_HEIGHT, STAR_YAW, 1, 1, 1);
        assertEquals(FEET.x - 0.6 * Math.sqrt(3), at.x, DELTA);
        assertEquals(FEET.y + 2.07, at.y, DELTA);
        assertEquals(FEET.z - 0.6, at.z, DELTA);
    }
}
