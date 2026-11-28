package com.mercuriusxeno.goo.client.zone;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.astral.LunarSight;
import com.mercuriusxeno.goo.lunar.LunarSun;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.MoonPhase;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.util.List;
import java.util.Optional;

/**
 * Lunar's sky: the void, PocketStarSky's sea of dim stars, a sun crossing it once a phase cycle over both halves of
 * the moon, and the blue planet, the overworld, four moons wide, hanging fixed where the camera's x stands it and lit
 * by the phase opposite the moon's, its face hand-drawn. Lunar's trip home looks at the planet.
 * decision sun-follows-the-phase-and-lights-the-far-side-too
 * decision blue-planet-hangs-fixed-with-its-height-set-by-x
 * decision astral-sky-is-the-void-with-one-bright-blue-overworld-star
 * decision lunar-has-a-day-and-night-and-a-sun-on-the-overworlds-clock
 * decision blue-star-is-a-planet-four-moons-wide-drawn-in-phases
 * decision blue-star-takes-the-moons-place-with-the-opposite-phase
 * decision planet-face-is-eight-hand-drawn-phase-sprites
 */
public final class LunarSky {

    /** The custom skybox goo:lunar's dimension type names, under NeoForge's custom_skybox attribute. */
    public static final Identifier ID = Identifier.fromNamespaceAndPath(Goo.MODID, "lunar_void");

    /** Vanilla's moon quad's half width at the sky's distance, SkyRenderer's moon size. */
    public static final float VANILLA_MOON_HALF_WIDTH = 20F;
    /** The share of vanilla's moon sprite's width its disc spans: eight of its thirty-two texels. */
    public static final double MOON_DISC_SHARE = 8.0 / 32.0;
    /**
     * The blue planet's half width at the sky's distance, four of vanilla's moon discs wide. Its disc fills its
     * 32-texel sprite, so a planet texel spans the sky units a moon texel spans: the moon's 8-texel disc is one moon
     * wide (MOON_DISC_SHARE of the quad VANILLA_MOON_HALF_WIDTH gives), the planet's 32-texel disc four moons wide.
     * decision blue-star-is-a-planet-four-moons-wide-drawn-in-phases
     * decision planet-face-is-eight-hand-drawn-phase-sprites
     * decision overworld-phases-are-32x32-at-the-moons-lod-ratio
     */
    public static final float PLANET_HALF_SIZE = (float) (LunarSight.PLANET_DISC_HALF_TANGENT
            * PocketStarSkyRenderer.SKY_DISTANCE);

    /** A celestial quad before its turn: a unit square lying flat, as vanilla builds its sun and moon quads. */
    public static final List<Vector3f> FLAT_QUAD = List.of(new Vector3f(-1F, 0F, -1F), new Vector3f(1F, 0F, -1F),
            new Vector3f(1F, 0F, 1F), new Vector3f(-1F, 0F, 1F));

    /** Vanilla turns its whole sky a quarter turn about the vertical before it turns its sun and moon. */
    private static final float SKY_QUARTER_TURN = (float) Math.toRadians(-90.0);
    /** Where the planet's hand-drawn phase sprites stand, one a phase. */
    private static final String PLANET_SPRITES = "textures/environment/blue_planet/";
    private static final String PNG = ".png";
    /** The full and the new phase's sprites, named for the planet where vanilla names its moon's for the moon. */
    private static final String FULL_SPRITE = "full";
    private static final String NEW_SPRITE = "new";

    private LunarSky() {
    }

    /**
     * The hand-drawn sprite the planet draws in a phase, named as vanilla names its moon's: the waning phases lit on
     * the left, the waxing on the right, the full and the new by their own names. Each holds its ice inland and darkens
     * toward every lit edge as vanilla's moon darkens toward its rim.
     * decision planet-face-is-eight-hand-drawn-phase-sprites
     * decision overworld-ice-moves-inland-off-the-edge
     * decision overworld-rim-darkens-like-the-moons
     *
     * @param planetPhase the planet's phase
     * @return the sprite's texture
     */
    public static Identifier planetSprite(MoonPhase planetPhase) {
        String name = switch (planetPhase) {
            case FULL_MOON -> FULL_SPRITE;
            case NEW_MOON -> NEW_SPRITE;
            default -> planetPhase.getSerializedName();
        };
        return Identifier.fromNamespaceAndPath(Goo.MODID, PLANET_SPRITES + name + PNG);
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

    /**
     * The turn that carries the flat quad to a celestial body along a direction, as vanilla's sky turns its moon: a
     * quarter turn about the vertical, the body's angle about the east-west axis, then out to its distance at its
     * half width.
     *
     * @param direction the body's unit direction, one a moon angle gives
     * @param distance  how far off it stands
     * @param halfWidth its half width there
     * @return the turn, from the flat quad to the body
     */
    public static Matrix4f celestialPose(Vec3 direction, float distance, float halfWidth) {
        float angle = (float) Math.atan2(-direction.x(), direction.y());
        return new Matrix4f().rotateY(SKY_QUARTER_TURN).rotateX(angle).translate(0F, distance, 0F)
                .scale(halfWidth, 1F, halfWidth);
    }

    /**
     * The sun's direction over a camera's x while it stands above the horizon there, on either half of the moon: the
     * sun follows the phase cycle, so it rises and sets over every x.
     * decision sun-follows-the-phase-and-lights-the-far-side-too
     *
     * @param cameraX            the camera's x, in blocks
     * @param overworldClockTime the overworld clock's total ticks
     * @return the unit direction, empty while the sun stands below the horizon
     */
    public static Optional<Vec3> sunOver(double cameraX, long overworldClockTime) {
        Vec3 sun = sunDirection((float) LunarSun.sunAngleDegrees(cameraX, overworldClockTime));
        return sun.y() > 0.0 ? Optional.of(sun) : Optional.empty();
    }

    /**
     * The turn that carries the flat quad to the planet over a camera's x: along the planet's fixed direction there,
     * at the sky's distance and the planet's half width.
     * decision blue-planet-hangs-fixed-with-its-height-set-by-x
     *
     * @param cameraX the camera's x, in blocks
     * @return the turn, from the flat quad to the planet
     */
    public static Matrix4f planetPose(double cameraX) {
        return celestialPose(LunarSight.planetDirection(cameraX), PocketStarSkyRenderer.SKY_DISTANCE,
                PLANET_HALF_SIZE);
    }

    /**
     * The planet quad's corners where lunar's sky draws them over a camera's x.
     * decision blue-planet-hangs-fixed-with-its-height-set-by-x
     *
     * @param cameraX the camera's x, in blocks
     * @return the four corners, in the flat quad's order
     */
    public static List<Vector3f> planetCorners(double cameraX) {
        Matrix4f pose = planetPose(cameraX);
        return FLAT_QUAD.stream().map(corner -> pose.transformPosition(corner, new Vector3f())).toList();
    }

    /**
     * Whether lunar's sky draws the planet over a camera's x: while it stands above the horizon there, across the
     * light side, its dark phase drawn too; the dark side draws none.
     * decision blue-planet-hangs-fixed-with-its-height-set-by-x
     * decision dark-side-is-the-half-of-the-moon-past-the-light-side
     *
     * @param cameraX the camera's x, in blocks
     * @return true where the planet draws
     */
    public static boolean drawsPlanetOver(double cameraX) {
        return LunarSight.planetAboveTheHorizon(cameraX);
    }
}
