package com.mercuriusxeno.goo.client.particle;

import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A spray drip flies one steady path from the glove to the end of its coat
 * ray, whatever its random source answers, and splats there.
 * goop-and-slick-spray-throws-goo-drips-that-splat
 * goop-and-slick-spray-follows-the-coat
 */
class SprayDripTest {

    private static final Vec3 APEX = new Vec3(0.3, 1.3, 0.2);
    private static final Vec3 COAT_END = new Vec3(2.0, 0.0, 6.0);
    /** A launch at a tenth of the gap per tick, as the stream's handler aims a coat mote. */
    private static final Vec3 LAUNCH = COAT_END.subtract(APEX).scale(0.1);
    private static final double WITHIN = 0.1;
    /** A bound on the steps a drip may fly, so a drip that never lands fails rather than hangs. */
    private static final int MAX_STEPS = 200;

    /**
     * One drip's flight, stepped from launch to its splat.
     *
     * @param path    where the drip stood after each step
     * @param splatAt where the step that splatted it left it, null where none did
     */
    private record Flown(List<Vec3> path, Vec3 splatAt) {
    }

    private static Flown fly(Vec3 launch, long seed) {
        SprayDrip drip = SprayDrip.launch(APEX, launch, BlendPalette.GOOP_SLIME_GREENS, RandomSource.create(seed));
        List<Vec3> path = new ArrayList<>();
        Vec3 position = APEX;
        Vec3 velocity = drip.velocity();
        for (int flown = 0; flown < MAX_STEPS; flown++) {
            SprayDrip.Step step = drip.step(position, velocity, flown);
            position = step.position();
            velocity = step.velocity();
            path.add(position);
            if (step.splats()) {
                return new Flown(path, position);
            }
        }
        return new Flown(path, null);
    }

    @Test
    void aDripFliesTheSamePathWhateverItsRandomSourceAnswers() {
        assertEquals(fly(LAUNCH, 1L).path(), fly(LAUNCH, 987_654_321L).path());
    }

    @Test
    void aDripSplatsOnTheEndOfItsCoatRayAndIsRemoved() {
        Flown flown = fly(LAUNCH, 1L);

        assertTrue(flown.splatAt() != null, "the drip should splat within " + MAX_STEPS + " steps");
        assertTrue(flown.splatAt().distanceTo(COAT_END) <= WITHIN,
                "the drip should splat within a tenth of a block of " + COAT_END + ", splatted at " + flown.splatAt());
        assertEquals(flown.splatAt(), flown.path().getLast(), "the splatting step should be the drip's last");
    }

    @Test
    void aDripStaysOnItsWayWithoutDartingBackOrStalling() {
        List<Vec3> path = fly(LAUNCH, 1L).path();
        Vec3 heading = COAT_END.subtract(APEX).multiply(1, 0, 1);

        Vec3 before = APEX;
        for (Vec3 position : path) {
            assertTrue(position.subtract(before).multiply(1, 0, 1).dot(heading) > 0,
                    "each step should carry the drip on toward its end, " + before + " to " + position);
            before = position;
        }
    }

    @Test
    void aDripCarriesItsCastersTravel() {
        Vec3 travel = new Vec3(0.2, 0.0, -0.1);

        Flown flown = fly(LAUNCH.add(travel), 1L);

        Vec3 carried = COAT_END.add(travel.scale(10));
        assertTrue(flown.splatAt().distanceTo(carried) <= WITHIN,
                "a caster's travel should carry the drip's end on to " + carried + ", splatted at " + flown.splatAt());
    }

    @Test
    void aDripsColorLiesBetweenItsPalettesEnds() {
        BlendPalette blues = BlendPalette.SLICK_SLIME_BLUES;
        for (long seed = 0; seed < 16; seed++) {
            int rgb = SprayDrip.launch(APEX, LAUNCH, blues, RandomSource.create(seed)).rgb();
            assertBetween(blues.darkRed(), blues.lightRed(), (rgb >> 16 & 0xFF) / 255f, "red");
            assertBetween(blues.darkGreen(), blues.lightGreen(), (rgb >> 8 & 0xFF) / 255f, "green");
            assertBetween(blues.darkBlue(), blues.lightBlue(), (rgb & 0xFF) / 255f, "blue");
        }
    }

    private static void assertBetween(float one, float other, float channel, String name) {
        float slack = 1f / 255f;
        assertTrue(channel >= Math.min(one, other) - slack && channel <= Math.max(one, other) + slack,
                name + " " + channel + " should lie between " + one + " and " + other);
    }
}
