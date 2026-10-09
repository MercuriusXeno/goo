package com.mercuriusxeno.goo.client.ability;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A block's cube turns into its stream over the drain: each point stands
 * where it was until its turn comes, the near face's first and the far
 * side's last, and lies on the stream's skin once turned
 * (decision unmake-waves-dissolve-by-crucible-cost).
 */
class DrinkMorphTest {

    private static final double DELTA = 1e-6;
    private static final Vec3 CENTER = new Vec3(7.5, 2.5, 3.5);
    private static final Vec3 GLOVE = new Vec3(1.5, 2.2, 3.1);
    private static final long SEED = 42;
    private static final double NOW = 100;
    private static final DrinkStream.Path PATH = DrinkMorph.pathOf(CENTER, GLOVE, SEED);
    private static final DrinkStream.Span DRAINING = new DrinkStream.Span(0, 1);
    private static final Vec3 UP = new Vec3(0, 1, 0);
    /** A point of the block's top face, block-local. */
    private static final Vec3 ON_TOP = new Vec3(0.3, 1, 0.8);
    private static final double HALFWAY = 0.5;
    private static final double STEP = 0.05;

    @Test
    void theWayStartsHalfASpanBehindTheMiddleAwayFromTheGlove() {
        Vec3 toGlove = GLOVE.subtract(CENTER);

        assertEquals(DrinkStream.BLOCK_SPAN / 2, PATH.from().distanceTo(CENTER), DELTA);
        assertTrue(CENTER.subtract(PATH.from()).dot(toGlove) > 0);
        assertEquals(GLOVE, PATH.to());
    }

    @Test
    void theNearFaceIsShallowAndTheFarSideDeep() {
        Vec3 along = GLOVE.subtract(CENTER).normalize();

        assertEquals(0, DrinkMorph.depthOf(CENTER.add(along.scale(DrinkStream.BLOCK_SPAN / 2)), PATH), DELTA);
        assertEquals(HALFWAY, DrinkMorph.depthOf(CENTER, PATH), DELTA);
        assertEquals(1, DrinkMorph.depthOf(PATH.from(), PATH), DELTA);
    }

    @Test
    void aPointStandsWhereItWasBeforeItsTurnComes() {
        Vec3 point = CENTER.add(ON_TOP).subtract(HALFWAY, HALFWAY, HALFWAY);

        assertEquals(0, DrinkMorph.turned(ON_TOP, 1, 0, SEED), DELTA);
        DrinkMorph.Place place = DrinkMorph.placeOf(point, UP, PATH, DRAINING, 0, NOW);
        assertEquals(0, place.point().distanceTo(point), DELTA);
        assertEquals(0, place.normal().distanceTo(UP), DELTA);
    }

    @Test
    void aPointLiesOnTheStreamsSkinOnceTurned() {
        Vec3 point = CENTER.add(ON_TOP).subtract(HALFWAY, HALFWAY, HALFWAY);
        Vec3 along = GLOVE.subtract(PATH.from()).normalize();
        double distance = point.subtract(PATH.from()).dot(along);
        DrinkStream.Ring ring = DrinkStream.ring(PATH, Math.clamp(distance, 0, DrinkStream.BLOCK_SPAN)
                / PATH.length(), DRAINING, NOW);

        DrinkMorph.Place place = DrinkMorph.placeOf(point, UP, PATH, DRAINING, 1, NOW);

        assertEquals(ring.radius(), place.point().distanceTo(ring.center()), DELTA);
        assertEquals(1, place.normal().length(), DELTA);
    }

    @Test
    void theNearFaceTurnsBeforeTheFarSideAndBothAreTurnedByTheEnd() {
        assertEquals(1, DrinkMorph.turned(ON_TOP, 0, HALFWAY, SEED), DELTA);
        assertEquals(0, DrinkMorph.turned(ON_TOP, 1, HALFWAY, SEED), DELTA);
        assertEquals(1, DrinkMorph.turned(ON_TOP, 1, 1, SEED), DELTA);
    }

    @Test
    void aPointTurnsSmoothlyOverItsTurn() {
        double before = 0;
        boolean between = false;
        for (double progress = 0; progress <= 1; progress += STEP) {
            double turned = DrinkMorph.turned(ON_TOP, HALFWAY, progress, SEED);
            assertTrue(turned >= before, "turning never goes back");
            between |= turned > 0 && turned < 1;
            before = turned;
        }

        assertTrue(between, "the turn passes through its middle");
    }
}
