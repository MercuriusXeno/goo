package com.mercuriusxeno.goo.client.ability;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A block becomes its stream as a box of liquid where it stood, its edges
 * rounding into a sphere and the box shrinking smoothly about its middle as
 * it drains, with a funnel narrowing from its width to the stream's in front
 * of it, nothing jumping at the start (decision unmake-waves-dissolve-by-crucible-cost).
 */
class DrinkBodyTest {

    private static final double DELTA = 1e-6;
    private static final long SEED = 42;
    private static final double NOW = 100;
    private static final double STREAM = 0.1;
    private static final double HALFWAY = 0.5;
    private static final double STEP = 0.02;
    private static final Vec3 CENTER = new Vec3(7.5, 2.5, 3.5);

    @Test
    void theBoxIsTheWholeCubeAtTheStartAndGoneAtTheEndShrinkingSmoothly() {
        assertEquals(1, DrinkBody.sizeAt(0), DELTA);
        assertEquals(0, DrinkBody.sizeAt(1), DELTA);
        assertTrue(DrinkBody.sizeAt(STEP) > 1 - STEP, "no jump at the start");
        double before = 1;
        for (double progress = 0; progress <= 1; progress += STEP) {
            double size = DrinkBody.sizeAt(progress);
            assertTrue(size <= before + DELTA, "shrinks only");
            before = size;
        }
    }

    @Test
    void theCubeRoundsIntoASphereOverTheFirstPartOfTheDrain() {
        DrinkBody.Box cube = DrinkBody.boxAt(CENTER, 0);
        DrinkBody.Box sphere = DrinkBody.boxAt(CENTER, DrinkBody.LIQUEFY);

        assertEquals(DrinkBody.MOUTH, cube.half(), DELTA);
        assertEquals(0, cube.rounding(), DELTA);
        assertEquals(sphere.half(), sphere.rounding(), DELTA);
        assertEquals(DrinkBody.MOUTH * DrinkBody.sizeAt(DrinkBody.LIQUEFY), sphere.half(), DELTA);
        assertEquals(0, DrinkBody.liquidityAt(0), DELTA);
        assertEquals(1, DrinkBody.liquidityAt(1), DELTA);
    }

    @Test
    void theBoxStandsWhereTheBlockStoodAndShrinksAboutItsMiddle() {
        DrinkBody.Box half = DrinkBody.boxAt(CENTER, HALFWAY);

        assertEquals(CENTER, half.center());
        assertEquals(DrinkBody.MOUTH * DrinkBody.sizeAt(HALFWAY), half.half(), DELTA);
        assertEquals(0, DrinkBody.boxAt(CENTER, 1).half(), DELTA);
    }

    @Test
    void theFunnelNarrowsFromTheBoxsWidthToTheStreamsInFrontOfIt() {
        double past = DrinkBody.CENTER + DrinkBody.FUNNEL;

        assertEquals(0, DrinkBody.funnelAt(0, 0, STREAM), DELTA);
        assertEquals(DrinkBody.MOUTH, DrinkBody.funnelAt(DrinkBody.CENTER, 0, STREAM), DELTA);
        assertEquals(STREAM, DrinkBody.funnelAt(past, 0, STREAM), DELTA);
        assertEquals(STREAM, DrinkBody.widthAt(past + 1, 0, STREAM, SEED, NOW), DELTA);
        assertEquals(STREAM, DrinkBody.widthAt(past + 1, 1, STREAM, SEED, NOW), DELTA);
        double between = DrinkBody.funnelAt(DrinkBody.CENTER + DrinkBody.FUNNEL / 2, 0, STREAM);
        assertTrue(between < DrinkBody.MOUTH && between > STREAM);
        assertEquals(DrinkBody.MOUTH, DrinkBody.widthAt(DrinkBody.CENTER, 0, STREAM, SEED, NOW), DELTA);
    }

    @Test
    void theLiquidWobblesWithinItsBoundWhereItIsStillBlob() {
        double still = DrinkBody.widthAt(DrinkBody.CENTER, 0, STREAM, SEED, NOW);
        double liquid = DrinkBody.widthAt(DrinkBody.CENTER, DrinkBody.LIQUEFY, STREAM, SEED, NOW);
        double expected = DrinkBody.MOUTH * DrinkBody.sizeAt(DrinkBody.LIQUEFY);

        assertEquals(DrinkBody.MOUTH, still, DELTA);
        assertTrue(Math.abs(liquid - expected) <= expected * DrinkBody.WOBBLE + DELTA);
    }
}
