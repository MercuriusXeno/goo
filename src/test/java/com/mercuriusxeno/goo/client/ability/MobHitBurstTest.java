package com.mercuriusxeno.goo.client.ability;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.SplittableRandom;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A mob hit's splash throws goo-colored drips off the struck point, each
 * flung away from the mob's center.
 */
class MobHitBurstTest {

    private static final Vec3 MOB_CENTER = new Vec3(0.5, 1, 0.5);
    private static final int BLAZE_RGB = 0xFF7A10;
    private static final int OPAQUE = 0xFF000000;

    private static List<MobHitBurst.Drip> splashAt(Vec3 hitPoint, long seed) {
        return MobHitBurst.splash(hitPoint, MOB_CENTER, BLAZE_RGB, new SplittableRandom(seed));
    }

    @Nested
    class Splash {

        @Test
        void throwsDripsOffTheStruckPoint() {
            Vec3 hitPoint = new Vec3(1, 1.2, 0.5);

            List<MobHitBurst.Drip> drips = splashAt(hitPoint, 1);

            assertFalse(drips.isEmpty());
            drips.forEach(drip -> assertEquals(hitPoint, drip.position()));
        }

        @Test
        void everyDripTakesTheGooTypesOpaqueColor() {
            splashAt(new Vec3(1, 1.2, 0.5), 2)
                    .forEach(drip -> assertEquals(BLAZE_RGB | OPAQUE, drip.argb()));
        }

        /** A drip flies outward at 0.15 a tick and its scatter kick stays under 0.1, so every drip leaves the mob. */
        @Test
        void aDripFliesOutwardAtFifteenHundredthsAndScattersUnderATenth() {
            assertEquals(0.15, MobHitBurst.OUTWARD_SPEED);
            assertEquals(0.1, MobHitBurst.SCATTER_SPEED);
        }

        @Test
        void hitOnTheCenterSplashesUpward() {
            splashAt(MOB_CENTER, 3).forEach(drip -> assertTrue(drip.velocity().y > 0));
        }
    }
}
