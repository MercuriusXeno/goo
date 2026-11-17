package com.mercuriusxeno.goo.client.zone;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.astral.LunarSight;
import com.mercuriusxeno.goo.lunar.Lunar;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/**
 * Lunar's sky: the void, PocketStarSky's sea of dim stars, a sun crossing it on the overworld's clock, and one bright
 * blue star standing fixed high in the south east, larger and brighter than any star of the sea, as Venus stands in
 * our sky but bigger. That star is the overworld, and Astral's gate home looks at it.
 * decision astral-sky-is-the-void-with-one-bright-blue-overworld-star
 * decision lunar-has-a-day-and-night-and-a-sun-on-the-overworlds-clock
 */
public final class LunarSky {

    /** The custom skybox goo:lunar's dimension type names, under NeoForge's custom_skybox attribute. */
    public static final Identifier ID = Identifier.fromNamespaceAndPath(Goo.MODID, "lunar_void");

    /** A pale bright blue, its blue past the brightest star of the sea. */
    private static final float RED = 0.45F;
    private static final float GREEN = 0.7F;
    private static final float BLUE = 1.0F;
    /** Its half width at the sky's distance, near four times the brightest star of the sea. */
    private static final float HALF_SIZE = 0.9F;

    /**
     * The overworld as lunar's sky shows it.
     *
     * @param direction the unit direction it stands along from the camera
     * @param red       its red, zero to one
     * @param green     its green, zero to one
     * @param blue      its blue, zero to one
     * @param halfSize  its core's half width at the sky's distance
     */
    public record OverworldStar(Vec3 direction, float red, float green, float blue, float halfSize) {
    }

    /** The one overworld star. */
    public static final OverworldStar OVERWORLD_STAR = new OverworldStar(
            Lunar.OVERWORLD_STAR_DIRECTION, RED, GREEN, BLUE, HALF_SIZE);

    private LunarSky() {
    }

    /**
     * The sun's direction from the camera at a sun angle, as vanilla's sky turns it: overhead at noon's angle, on the
     * eastern horizon at sunrise's, on the western at sunset's and underfoot at midnight's. Vanilla turns its sun and
     * its moon the same way, each by its own angle.
     * decision lunar-has-a-day-and-night-and-a-sun-on-the-overworlds-clock
     *
     * @param sunAngleDegrees the sun angle attribute, in degrees
     * @return the unit direction
     */
    public static Vec3 sunDirection(float sunAngleDegrees) {
        return LunarSight.moonDirection(sunAngleDegrees);
    }
}
