package com.mercuriusxeno.goo.client.ability;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bore's drill is a cone from the glove to Bore's reach on the eye line it
 * drills, widest at the glove and coming to a point at the tip
 * (decision bore-shows-a-big-drill-from-the-glove).
 */
class BoreDrillTest {

    /** rock_bore.json's delivery range. */
    private static final double REACH = 4;
    private static final double SLACK = 1e-9;
    private static final Vec3 EYE = Vec3.ZERO;
    /** Yaw 0 looks along +Z. */
    private static final float YAW = 0f;
    private static final Vec3 LOOK = new Vec3(0, 0, 1);
    /** A first-person glove: right of the eye (-X when facing +Z), below it and ahead of it. */
    private static final Vec3 HAND = new Vec3(-0.35, -0.3, 0.5);
    private static final int SAMPLES = 64;

    private static ConeSections.Volume drill() {
        return BoreDrill.volume(EYE, HAND, LOOK, YAW, REACH);
    }

    private static Vec3 pointAlong(ConeSections.Volume volume, double distance) {
        return volume.apex().add(volume.axis().scale(distance));
    }

    @Nested
    class Ends {

        @Test
        void theBaseStandsAtTheGlove() {
            ConeSections.Volume drill = drill();
            assertEquals(0, pointAlong(drill, drill.near()).distanceTo(HAND), SLACK);
        }

        @Test
        void theTipStandsOnTheEyeLineAtTheReach() {
            ConeSections.Volume drill = drill();
            assertEquals(0, pointAlong(drill, drill.range()).distanceTo(EYE.add(LOOK.scale(REACH))), SLACK);
        }
    }

    @Nested
    class Radius {

        @Test
        void theRadiusIsGreatestAtTheGloveAndNeverGrowsAlongTheBit() {
            ConeSections.Volume drill = drill();
            double atTheGlove = drill.radiusAt().applyAsDouble(drill.near());
            assertTrue(atTheGlove > 0, "the bit has no width at the glove");
            double previous = atTheGlove;
            for (int sample = 1; sample <= SAMPLES; sample++) {
                double distance = drill.near() + (drill.range() - drill.near()) * sample / SAMPLES;
                double radius = drill.radiusAt().applyAsDouble(distance);
                assertTrue(radius <= previous, "the bit grows to " + radius + " at " + distance);
                previous = radius;
            }
        }

        @Test
        void theRadiusComesToAPointAtTheTip() {
            ConeSections.Volume drill = drill();
            assertEquals(0, drill.radiusAt().applyAsDouble(drill.range()), SLACK);
        }
    }
}
