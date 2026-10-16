package com.mercuriusxeno.goo.client.ability;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A block becomes its stream as a box of liquid where it stood, its edges
 * rounding into a sphere and the box shrinking smoothly about its middle as
 * it drains, nothing jumping at the start (decision unmake-waves-dissolve-by-crucible-cost).
 */
class DrinkBodyTest {

    private static final double DELTA = 1e-6;
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
}
