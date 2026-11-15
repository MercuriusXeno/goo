package com.mercuriusxeno.goo.client.zone;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/**
 * Lunar's sky: the void, PocketStarSky's sea of dim stars with no sun or moon, and one bright blue star standing
 * fixed high in the south east, larger and brighter than any star of the sea, as Venus stands in our sky but bigger.
 * That star is the overworld, and Astral's gate home looks at it.
 * decision astral-sky-is-the-void-with-one-bright-blue-overworld-star
 */
public final class LunarSky {

    /** The custom skybox goo:lunar's dimension type names, under NeoForge's custom_skybox attribute. */
    public static final Identifier ID = Identifier.fromNamespaceAndPath(Goo.MODID, "lunar_void");

    /** The overworld star's direction from the camera, before it is made unit. */
    private static final double TOWARD_EAST = 0.45;
    private static final double TOWARD_UP = 0.62;
    private static final double TOWARD_SOUTH = 0.64;
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
            new Vec3(TOWARD_EAST, TOWARD_UP, TOWARD_SOUTH).normalize(), RED, GREEN, BLUE, HALF_SIZE);

    private LunarSky() {
    }
}
