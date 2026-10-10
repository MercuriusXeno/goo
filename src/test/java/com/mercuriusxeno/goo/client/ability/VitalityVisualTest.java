package com.mercuriusxeno.goo.client.ability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/** Vitality's fog fills a cone from the glove along the aim whose rim undulates in a wave rolling outward (decision vitality-waves-regenerate-and-court). */
class VitalityVisualTest {

    private static final double DELTA = 1e-9;
    private static final Vec3 APEX = new Vec3(1, 2, 3);
    private static final Vec3 EAST = new Vec3(1, 0, 0);
    private static final double RANGE = 6;
    private static final double CONE = 50;

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

    @Test
    void theUndulationSwellsAndShrinksTheRimWithinItsShare() {
        for (int tick = 0; tick < 40; tick++) {
            double scale = VitalityVisual.undulation(0.3, tick);
            assertTrue(scale >= 1 - VitalityVisual.UNDULATION - DELTA && scale <= 1 + VitalityVisual.UNDULATION + DELTA);
        }
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

    /** A first-person caster's own stars rise before them, clear of the camera, waist to over the head. */
    @Test
    void aFirstPersonCastersStarsRiseInFrontOfTheirEyesWhereTheyCanSeeThem() {
        Vec3 feet = new Vec3(10, 64, 10);
        double height = 1.8;
        float yaw = 30f;
        Vec3 facing = Vec3.directionFromRotation(0f, yaw);
        RandomSource random = RandomSource.create(7L);
        for (int star = 0; star < 200; star++) {
            Vec3 at = VitalityVisual.starBeforeTheEyes(feet, height, yaw, random);
            Vec3 flat = new Vec3(at.x - feet.x, 0, at.z - feet.z);
            assertTrue(flat.length() >= VitalityVisual.STAR_REACH_MIN - DELTA, "a star sits inside the near clip");
            assertTrue(flat.normalize().dot(facing) > Math.cos(VitalityVisual.STAR_ARC_HALF) - 1e-6,
                    "a star sits outside the arc before the eyes");
            assertTrue(at.y >= feet.y + height * VitalityVisual.STAR_LOW_SHARE - DELTA
                    && at.y <= feet.y + height * VitalityVisual.STAR_HIGH_SHARE + DELTA, "a star sits off the band");
        }
    }
}
