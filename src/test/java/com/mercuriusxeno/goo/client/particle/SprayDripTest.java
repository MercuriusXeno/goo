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
 * ray, whatever its random source answers, and splats there, for a caster
 * standing, walking, strafing, turning, running or jumping.
 * goop-and-slick-spray-throws-goo-drips-that-splat
 * goop-and-slick-spray-emanates-from-the-glove
 * goop-and-slick-spray-follows-the-coat
 */
class SprayDripTest {

    private static final Vec3 APEX = new Vec3(0.3, 1.3, 0.2);
    private static final Vec3 COAT_END = new Vec3(2.0, 0.0, 6.0);
    private static final Vec3 RUNNING = new Vec3(0.2, 0.0, -0.1);
    /** A jump's opening rise, blocks per tick. */
    private static final Vec3 JUMPING = new Vec3(0.0, 0.42, 0.0);
    private static final Vec3 WALKING = new Vec3(0.05, 0.0, 0.15);
    private static final Vec3 STRAFING = new Vec3(-0.2, 0.0, 0.0);
    /** Where a glove that swung round the eye with a turned look stands when its drip leaves. */
    private static final Vec3 TURNED_GLOVE = new Vec3(0.55, 1.3, 0.05);
    private static final double WITHIN = 0.1;
    private static final double DELTA = 1e-9;
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

    private static SprayDrip throwDrip(Vec3 glove, Vec3 travel, long seed) {
        return SprayDrip.launch(glove, COAT_END, travel, BlendPalette.GOOP_SLIME_GREENS, RandomSource.create(seed));
    }

    private static Flown fly(Vec3 glove, Vec3 travel, long seed) {
        SprayDrip drip = throwDrip(glove, travel, seed);
        List<Vec3> path = new ArrayList<>();
        Vec3 position = glove;
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

    private static void assertSplatsOnTheCoatEnd(Vec3 glove, Vec3 travel) {
        Flown flown = fly(glove, travel, 1L);

        assertTrue(flown.splatAt() != null, "the drip should splat within " + MAX_STEPS + " steps");
        assertTrue(flown.splatAt().distanceTo(COAT_END) <= WITHIN, "a drip thrown from " + glove + " with travel "
                + travel + " should splat within a tenth of a block of " + COAT_END + ", splatted at "
                + flown.splatAt());
        assertEquals(flown.splatAt(), flown.path().getLast(), "the splatting step should be the drip's last");
    }

    @Test
    void aDripFliesTheSamePathWhateverItsRandomSourceAnswers() {
        assertEquals(fly(APEX, RUNNING, 1L).path(), fly(APEX, RUNNING, 987_654_321L).path());
    }

    @Test
    void aStandingCastersDripSplatsOnTheEndOfItsCoatRayAndIsRemoved() {
        assertSplatsOnTheCoatEnd(APEX, Vec3.ZERO);
    }

    @Test
    void aWalkingCastersDripSplatsOnTheEndOfItsCoatRay() {
        assertSplatsOnTheCoatEnd(APEX.add(WALKING.scale(1.5)), WALKING);
    }

    @Test
    void aStrafingCastersDripSplatsOnTheEndOfItsCoatRay() {
        assertSplatsOnTheCoatEnd(APEX.add(STRAFING.scale(1.5)), STRAFING);
    }

    @Test
    void aTurningCastersDripSplatsOnTheEndOfItsCoatRay() {
        assertSplatsOnTheCoatEnd(TURNED_GLOVE, Vec3.ZERO);
    }

    @Test
    void aRunningCastersDripStillSplatsOnTheEndOfItsCoatRay() {
        assertSplatsOnTheCoatEnd(APEX, RUNNING);
    }

    @Test
    void aJumpingCastersDripStillSplatsOnTheEndOfItsCoatRay() {
        assertSplatsOnTheCoatEnd(APEX, JUMPING);
    }

    @Test
    void aDripStaysOnItsWayWithoutDartingBackOrStalling() {
        List<Vec3> path = fly(APEX, Vec3.ZERO, 1L).path();
        Vec3 heading = COAT_END.subtract(APEX).multiply(1, 0, 1);

        Vec3 before = APEX;
        for (Vec3 position : path) {
            assertTrue(position.subtract(before).multiply(1, 0, 1).dot(heading) > 0,
                    "each step should carry the drip on toward its end, " + before + " to " + position);
            before = position;
        }
    }

    @Test
    void aDripLeavesTheGloveCarryingItsCastersTravel() {
        Vec3 carried = throwDrip(APEX, RUNNING, 1L).velocity().subtract(throwDrip(APEX, Vec3.ZERO, 1L).velocity());

        assertEquals(RUNNING.x, carried.x, DELTA, "x");
        assertEquals(RUNNING.y, carried.y, DELTA, "y");
        assertEquals(RUNNING.z, carried.z, DELTA, "z");
    }
    @Test
    void aDripsColorLiesBetweenItsPalettesEnds() {
        BlendPalette blues = BlendPalette.SLICK_SLIME_BLUES;
        for (long seed = 0; seed < 16; seed++) {
            int rgb = SprayDrip.launch(APEX, COAT_END, Vec3.ZERO, blues, RandomSource.create(seed)).rgb();
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
