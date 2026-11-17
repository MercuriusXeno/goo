package com.mercuriusxeno.goo.client.zone;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.astral.LunarSight;
import com.mercuriusxeno.goo.lunar.PlanetFace;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.MoonPhase;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;

/**
 * Lunar's sky: the void, PocketStarSky's sea of dim stars, a sun crossing it on the overworld's clock, and the blue
 * planet, the overworld, four moons wide, standing where the overworld's sky would stand its moon and lit by the
 * phase opposite the moon's. Astral's gate home looks at the planet.
 * decision astral-sky-is-the-void-with-one-bright-blue-overworld-star
 * decision lunar-has-a-day-and-night-and-a-sun-on-the-overworlds-clock
 * decision blue-star-is-a-planet-four-moons-wide-drawn-in-phases
 * decision blue-star-takes-the-moons-place-with-the-opposite-phase
 */
public final class LunarSky {

    /** The custom skybox goo:lunar's dimension type names, under NeoForge's custom_skybox attribute. */
    public static final Identifier ID = Identifier.fromNamespaceAndPath(Goo.MODID, "lunar_void");

    /** Vanilla's moon quad's half width at the sky's distance, SkyRenderer's moon size. */
    public static final float VANILLA_MOON_HALF_WIDTH = 20F;
    /** The share of vanilla's moon sprite's width its disc spans: eight of its thirty-two texels. */
    public static final double MOON_DISC_SHARE = 8.0 / 32.0;
    /**
     * The blue planet's half width at the sky's distance, four of vanilla's moon discs wide; its disc fills its quad.
     * decision blue-star-is-a-planet-four-moons-wide-drawn-in-phases
     */
    public static final float PLANET_HALF_SIZE = (float) (LunarSight.PLANET_DISC_HALF_TANGENT
            * PocketStarSkyRenderer.SKY_DISTANCE);
    /** The planet's face is a square of pixels this many wide, its disc inscribed in it, the server's face map's. */
    public static final int FACE_SIZE = PlanetFace.SIZE;

    /** A celestial quad before its turn: a unit square lying flat, as vanilla builds its sun and moon quads. */
    public static final List<Vector3f> FLAT_QUAD = List.of(new Vector3f(-1F, 0F, -1F), new Vector3f(1F, 0F, -1F),
            new Vector3f(1F, 0F, 1F), new Vector3f(-1F, 0F, 1F));

    /** How bright the disc's rim draws against its middle, as vanilla's moon darkens toward its edge. */
    private static final float RIM_BRIGHTNESS = 0.65F;
    /** How far out a face pixel stands, as a share of the disc's radius squared, before it reads as the rim. */
    private static final double RIM_FROM = 0.8;
    /** A pixel's center stands half a pixel in from its edge. */
    private static final double HALF_A_PIXEL = 0.5;
    /** Vanilla turns its whole sky a quarter turn about the vertical before it turns its sun and moon. */
    private static final float SKY_QUARTER_TURN = (float) Math.toRadians(-90.0);
    /** The share of a turn each moon phase stands on from the full, eight to a cycle. */
    private static final double PHASE_TURN = Math.PI * 2 / MoonPhase.COUNT;
    /** The phases from the full up to the new light the face's left; the rest light its right. */
    private static final int LAST_LIT_LEFT = MoonPhase.NEW_MOON.index();

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
     * The planet quad's corners where lunar's sky draws them at a moon angle: centered along the moon's direction at
     * the sky's distance, at the planet's half width.
     * decision blue-star-takes-the-moons-place-with-the-opposite-phase
     *
     * @param moonAngleDegrees lunar's moon angle, in degrees
     * @return the four corners, in the flat quad's order
     */
    public static List<Vector3f> planetCorners(float moonAngleDegrees) {
        Matrix4f pose = celestialPose(LunarSight.moonDirection(moonAngleDegrees), PocketStarSkyRenderer.SKY_DISTANCE,
                PLANET_HALF_SIZE);
        return FLAT_QUAD.stream().map(corner -> pose.transformPosition(corner, new Vector3f())).toList();
    }

    /**
     * The face's pixels the planet's disc covers, the disc inscribed in the face's square, row by row from the top.
     *
     * @return the covered pixels, each at its row times the face's size plus its column
     */
    public static BitSet facePixels() {
        BitSet face = new BitSet(FACE_SIZE * FACE_SIZE);
        for (int pixel = 0; pixel < FACE_SIZE * FACE_SIZE; pixel++) {
            face.set(pixel, radiusSquared(pixel) <= 1.0);
        }
        return face;
    }

    /**
     * The face's pixels a phase lights, as vanilla's moon sprites light theirs: the full lights the whole disc, the
     * waning phases light its left behind a terminator stepping pixel by pixel, the waxing its right, and the new
     * lights none.
     * decision blue-star-is-a-planet-four-moons-wide-drawn-in-phases
     *
     * @param planetPhase the planet's phase
     * @return the lit pixels, indexed as facePixels indexes them
     */
    public static BitSet litPixels(MoonPhase planetPhase) {
        double terminator = Math.cos(planetPhase.index() * PHASE_TURN);
        boolean litLeft = planetPhase.index() <= LAST_LIT_LEFT;
        BitSet face = facePixels();
        BitSet lit = new BitSet(FACE_SIZE * FACE_SIZE);
        face.stream().forEach(pixel -> {
            double across = across(pixel % FACE_SIZE);
            double rowHalfWidth = Math.sqrt(1.0 - square(across(pixel / FACE_SIZE)));
            lit.set(pixel, litLeft ? across < rowHalfWidth * terminator : across > -rowHalfWidth * terminator);
        });
        return lit;
    }

    /**
     * Whether lunar's sky draws the planet in a phase: the dark phase draws no disc.
     *
     * @param planetPhase the planet's phase
     * @return true where the phase lights any of the face
     */
    public static boolean planetDraws(MoonPhase planetPhase) {
        return !litPixels(planetPhase).isEmpty();
    }

    /**
     * The planet's plain face, the one it shows before the server's face map arrives: sea on every pixel of the disc.
     *
     * @return the face's colors, ARGB, indexed as facePixels indexes them
     */
    public static int[] plainFace() {
        int[] sea = new int[FACE_SIZE * FACE_SIZE];
        Arrays.fill(sea, PlanetFace.SEA);
        return shadedFace(sea);
    }

    /**
     * A face map as the disc shows it: each pixel of the disc in its color, darkening toward the rim, and nothing off
     * the disc.
     * decision planet-face-is-procgen-from-the-overworlds-biomes
     *
     * @param colors the face map's colors, ARGB, indexed as facePixels indexes them
     * @return the disc's colors, ARGB, clear off the disc
     */
    public static int[] shadedFace(int[] colors) {
        int[] face = new int[FACE_SIZE * FACE_SIZE];
        facePixels().stream().forEach(pixel -> face[pixel] = radiusSquared(pixel) < RIM_FROM ? colors[pixel]
                : ARGB.scaleRGB(colors[pixel], RIM_BRIGHTNESS));
        return face;
    }

    /**
     * A face as a phase shows it: each lit pixel in its own color, every other pixel clear.
     *
     * @param face        the face's colors, ARGB, indexed as facePixels indexes them
     * @param planetPhase the planet's phase
     * @return the shown colors, ARGB, clear where unlit
     */
    public static int[] litFace(int[] face, MoonPhase planetPhase) {
        int[] shown = new int[face.length];
        litPixels(planetPhase).stream().forEach(pixel -> shown[pixel] = face[pixel]);
        return shown;
    }

    private static double radiusSquared(int pixel) {
        return square(across(pixel % FACE_SIZE)) + square(across(pixel / FACE_SIZE));
    }

    /**
     * A pixel's center across the face, from minus one at its first edge to one at its last.
     *
     * @param pixel the pixel's column or row
     * @return its center, minus one to one
     */
    private static double across(int pixel) {
        return (pixel + HALF_A_PIXEL) / (FACE_SIZE * HALF_A_PIXEL) - 1.0;
    }

    private static double square(double value) {
        return value * value;
    }
}
