package com.mercuriusxeno.goo.client.ability;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A splat's quad covers its shape on screen: square to the camera's line
 * through the shape's middle, every ray from the camera grazing the shape's
 * bounding sphere meets the quad's plane inside the quad, and a camera inside
 * the shape gets no quad (decision unmake-drink-draws-as-a-screen-space-fluid).
 */
class DrinkFluidPassTest {

    private static final DrinkSplats.Coat COAT = new DrinkSplats.Coat(0, 0, 0, 0);
    private static final Vec3 CENTER = new Vec3(0.7, -0.4, 1.3);
    private static final double RADIUS = 0.5;
    private static final double HALF_WIDTH = 0.4;
    private static final double ROUNDING = 0.1;
    private static final int AROUND = 32;
    private static final double SLACK = 1e-6;
    private static final double DELTA = 1e-9;

    private static void assertCovers(DrinkSplats.Splat splat) {
        Vec3[] corners = DrinkFluidPass.quadOf(splat);
        assertEquals(4, corners.length);
        Vec3 toward = splat.center().normalize();
        Vec3 right = corners[1].subtract(corners[0]).normalize();
        Vec3 up = corners[3].subtract(corners[0]).normalize();
        double reach = corners[1].subtract(corners[0]).length() / 2;
        for (Vec3 corner : corners) {
            assertEquals(0, corner.subtract(splat.center()).dot(toward), DELTA, "the quad is square to the line");
        }
        double distance = splat.center().length();
        double grazing = Math.asin(splat.bound() / distance);
        for (int step = 0; step < AROUND; step++) {
            double turn = 2 * Math.PI * step / AROUND;
            Vec3 side = right.scale(Math.cos(turn)).add(up.scale(Math.sin(turn)));
            Vec3 ray = toward.scale(Math.cos(grazing)).add(side.scale(Math.sin(grazing)));
            Vec3 met = ray.scale(distance / ray.dot(toward)).subtract(splat.center());
            assertTrue(Math.abs(met.dot(right)) <= reach + SLACK && Math.abs(met.dot(up)) <= reach + SLACK,
                    "a grazing ray meets the quad's plane inside it");
        }
    }

    @Test
    void aSpheresQuadCoversEveryRayGrazingIt() {
        assertCovers(new DrinkSplats.Splat(CENTER, RADIUS, RADIUS, 0, COAT));
    }

    @Test
    void aCubesQuadCoversEveryRayGrazingItsCorners() {
        DrinkSplats.Splat cube = new DrinkSplats.Splat(CENTER.scale(2), HALF_WIDTH, ROUNDING, 0, COAT);

        assertCovers(cube);
        assertEquals((HALF_WIDTH - ROUNDING) * Math.sqrt(3) + ROUNDING, cube.bound(), DELTA);
    }

    @Test
    void aCameraInsideTheShapeGetsNoQuad() {
        DrinkSplats.Splat around = new DrinkSplats.Splat(new Vec3(0.1, 0, 0), RADIUS, RADIUS, 0, COAT);

        assertEquals(0, DrinkFluidPass.quadOf(around).length);
    }
}
