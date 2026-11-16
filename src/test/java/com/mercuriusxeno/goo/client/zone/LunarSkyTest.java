package com.mercuriusxeno.goo.client.zone;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers lunar's sky: goo:lunar draws the void, a sea of dim stars with no sun or moon, and one bright blue star,
 * the overworld, at a fixed sky direction, larger and brighter than any star of the sea.
 * decision astral-sky-is-the-void-with-one-bright-blue-overworld-star
 */
class LunarSkyTest {

    private static final double TOLERANCE = 1e-9;
    /** The overworld star's named direction: high in the south east, well clear of the horizon. */
    private static final Vec3 NAMED_DIRECTION = new Vec3(0.45, 0.62, 0.64).normalize();

    @Nested
    class TheSkyLunarDraws {

        @Test
        void lunarDrawsTheVoidWithItsOverworldStar() {
            assertEquals(PocketStarSky.Sky.LUNAR_VOID,
                    PocketStarSky.skyOf(SkyResources.customSkyboxOf(SkyResources.LUNAR)));
        }
    }

    @Nested
    class TheOverworldStar {

        @Test
        void itStandsAtTheNamedFixedDirection() {
            Vec3 direction = LunarSky.OVERWORLD_STAR.direction();
            assertEquals(NAMED_DIRECTION.x(), direction.x(), TOLERANCE);
            assertEquals(NAMED_DIRECTION.y(), direction.y(), TOLERANCE);
            assertEquals(NAMED_DIRECTION.z(), direction.z(), TOLERANCE);
        }

        @Test
        void itDrawsBlue() {
            LunarSky.OverworldStar star = LunarSky.OVERWORLD_STAR;
            assertTrue(star.blue() > star.red() && star.blue() > star.green(), String.valueOf(star));
        }

        @Test
        void itStandsLargerAndBrighterThanEveryStarOfTheSea() {
            float brightestSea = PocketStarSky.stars().stream().map(PocketStarSky.Star::brightness)
                    .reduce(0F, Math::max);
            assertTrue(LunarSky.OVERWORLD_STAR.blue() > brightestSea);
            assertTrue(LunarSky.OVERWORLD_STAR.halfSize() > PocketStarSkyRenderer.BRIGHTEST_HALF_SIZE);
        }
    }
}
