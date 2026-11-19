package com.mercuriusxeno.goo.ability.astral;

import com.mercuriusxeno.goo.lunar.Lunar;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.MoonPhase;
import net.minecraft.world.phys.Vec3;
import java.util.Optional;
import java.util.Set;

/**
 * What Lunar finds along a caster's look: the moon, from the overworld while it stands above the horizon on a lit
 * phase, or the blue planet, from lunar's light side, hanging fixed where the caster's x stands it, while you can see
 * it; anything else Lunar refuses.
 * decision blue-planet-hangs-fixed-with-its-height-set-by-x
 * decision gate-fixates-your-view-on-the-moon-until-it-takes-you
 * decision gate-on-the-blue-star-returns-you-exactly-where-you-left
 * decision blue-star-takes-the-moons-place-with-the-opposite-phase
 * decision lunar-refuses-the-return-while-the-blue-star-is-hidden
 * decision moon-trip-refuses-under-the-new-moon
 */
public final class LunarSight {

    /** How far off the moon's center a look may stray and still hold it, in degrees. */
    static final double LOOK_REACH_DEGREES = 8.0;
    private static final double LOOK_REACH_COSINE = Math.cos(Math.toRadians(LOOK_REACH_DEGREES));

    /** Vanilla's moon disc's half width, as the tangent of its angle from the view's center. */
    private static final double MOON_DISC_HALF_TANGENT = 0.05;
    private static final double PLANET_MOONS_WIDE = 4.0;
    /**
     * The blue planet's disc's half width, as the tangent of its angle from the view's center: four moons wide.
     * decision blue-star-is-a-planet-four-moons-wide-drawn-in-phases
     */
    public static final double PLANET_DISC_HALF_TANGENT = MOON_DISC_HALF_TANGENT * PLANET_MOONS_WIDE;
    /** The cosine of the angle from the planet's center to its disc's edge. */
    private static final double PLANET_DISC_COSINE = Math.cos(Math.atan(PLANET_DISC_HALF_TANGENT));
    /** A look anywhere on the planet's disc holds it, its disc wider than the moon's look reach. */
    private static final double PLANET_REACH_COSINE = Math.min(LOOK_REACH_COSINE,
            Math.cos(Math.atan(PLANET_DISC_HALF_TANGENT)));
    /** The moon's phases run a cycle of eight; the planet stands half of it on. */
    private static final int HALF_A_CYCLE = MoonPhase.COUNT / 2;

    /**
     * The tell a cast holding neither the moon nor the planet shows in the overworld, "That's no moon!", once a
     * press.
     * decision lunar-refusal-says-thats-no-moon
     */
    public static final String LUNAR_REFUSED = "goo.ability.astral.lunar.refused";
    /**
     * The tell Terran shows on the moon held anywhere but the showing planet, "That's not your world".
     * decision terran-refusal-says-thats-not-your-world
     */
    public static final String TERRAN_REFUSED = "goo.ability.astral.terran.refused";

    /**
     * The joke a cast held on the sun shows, "The big yellow one is the sun!". The sun's disc spans eight of its
     * sprite's thirty-two texels on a quad of half width 30, inside the look reach a moon is held within, so the sun
     * is held within that reach too.
     * decision pointing-at-the-sun-says-the-big-yellow-one-is-the-sun
     */
    public static final String SUN_REFUSED = "goo.ability.astral.sun.refused";
    /** The dimensions whose sky draws a sun Lunar can be held on. */
    private static final Set<ResourceKey<Level>> SUNLIT = Set.of(Level.OVERWORLD, Lunar.LEVEL);

    /** What Lunar fixates on. */
    public enum Sky {
        /** The overworld's moon, which carries the caster to lunar. */
        MOON,
        /** Lunar's blue planet, the overworld, which carries the caster home. */
        BLUE_PLANET
    }

    private LunarSight() {
    }

    /**
     * The refusal a cast holding neither moon nor planet shows, the sun first: a look holding the sun while it stands
     * above the horizon, in the overworld or anywhere on the moon, says "The big yellow one is the sun!", and any other
     * look shows its dimension's refusal. On the moon a look on the planet's disc holds the planet, whose disc, four
     * moons wide, hides the sun behind it through the planet's dark phase.
     * decision pointing-at-the-sun-says-the-big-yellow-one-is-the-sun
     * decision sun-follows-the-phase-and-lights-the-far-side-too
     *
     * @param dimension       the caster's dimension
     * @param x               the caster's x, in blocks, which sets the planet's direction in lunar
     * @param look            the caster's look, any length
     * @param sunAngleDegrees the sun angle where the caster stands, in degrees
     * @return the refusal's lang key
     */
    public static String refusalKey(ResourceKey<Level> dimension, double x, Vec3 look, float sunAngleDegrees) {
        Vec3 sun = moonDirection(sunAngleDegrees);
        boolean holdsTheSun = SUNLIT.contains(dimension) && sun.y() > 0.0 && holds(look, sun, LOOK_REACH_COSINE)
                && !holdsThePlanetsDisc(dimension, x, look);
        return holdsTheSun ? SUN_REFUSED : refusalKey(dimension);
    }

    private static boolean holdsThePlanetsDisc(ResourceKey<Level> dimension, double x, Vec3 look) {
        return Lunar.LEVEL.equals(dimension) && planetAboveTheHorizon(x)
                && holds(look, planetDirection(x), PLANET_REACH_COSINE);
    }

    /**
     * The refusal a cast holding neither moon nor planet shows: "That's no moon!" in the overworld and "That's not
     * your world" on the moon, where the hold reads as Terran and a hidden planet refuses alike.
     * decision lunar-refusal-says-thats-no-moon
     * decision terran-refusal-says-thats-not-your-world
     *
     * @param dimension the caster's dimension
     * @return the refusal's lang key
     */
    public static String refusalKey(ResourceKey<Level> dimension) {
        return Lunar.LEVEL.equals(dimension) ? TERRAN_REFUSED : LUNAR_REFUSED;
    }

    /**
     * The moon's direction from the camera at a moon angle, as vanilla's sky draws it: straight up turned about
     * the x axis by the angle, then a quarter turn about the y axis.
     *
     * @param moonAngleDegrees the dimension's moon angle attribute, in degrees
     * @return the unit direction
     */
    public static Vec3 moonDirection(float moonAngleDegrees) {
        return directionAt(Math.toRadians(moonAngleDegrees));
    }

    /**
     * The blue planet's direction over a lunar x, the same at every hour, at the angle Lunar.planetAngleDegrees reads.
     * decision blue-planet-hangs-fixed-with-its-height-set-by-x
     *
     * @param x the x, in blocks
     * @return the unit direction
     */
    public static Vec3 planetDirection(double x) {
        return directionAt(Math.toRadians(Lunar.planetAngleDegrees(x)));
    }

    /**
     * Where a dimension's sky stands what Lunar holds: the overworld's moon by its moon angle, lunar's planet by the
     * caster's x alone.
     * decision blue-planet-hangs-fixed-with-its-height-set-by-x
     *
     * @param dimension        the caster's dimension
     * @param x                the caster's x, in blocks
     * @param moonAngleDegrees the dimension's moon angle, in degrees, which lunar leaves unread
     * @return the unit direction
     */
    public static Vec3 skyDirection(ResourceKey<Level> dimension, double x, float moonAngleDegrees) {
        return Lunar.LEVEL.equals(dimension) ? planetDirection(x) : moonDirection(moonAngleDegrees);
    }

    /**
     * Whether the blue planet stands above the horizon over a lunar x: across the light side, never on the dark side.
     * decision blue-planet-hangs-fixed-with-its-height-set-by-x
     * decision dark-side-is-the-half-of-the-moon-past-the-light-side
     *
     * @param x the x, in blocks
     * @return true while the planet stands at or above the horizon there
     */
    public static boolean planetAboveTheHorizon(double x) {
        return planetDirection(x).y() > 0.0;
    }

    /**
     * Straight up turned about the x axis by an angle, toward negative x, as vanilla's sky turns its moon.
     *
     * @param radians the angle, in radians
     * @return the unit direction
     */
    private static Vec3 directionAt(double radians) {
        return new Vec3(-Math.sin(radians), Math.cos(radians), 0.0);
    }

    /**
     * The blue planet's phase, the overworld moon's turned half a cycle: full while the moon is new and dark while it
     * is full, so there are nights the planet does not show.
     * decision blue-star-takes-the-moons-place-with-the-opposite-phase
     *
     * @param moonPhase the overworld moon's phase
     * @return the planet's phase
     */
    public static MoonPhase planetPhase(MoonPhase moonPhase) {
        return MoonPhase.values()[(moonPhase.index() + HALF_A_CYCLE) % MoonPhase.COUNT];
    }

    /**
     * What a look finds in a dimension's sky: the moon while it stands above the horizon on a lit phase, the new moon
     * showing nothing to hold, and the blue planet along its fixed direction for the caster's x while it shows, so each
     * trip waits on its body showing, the way home at any hour you can see the planet.
     * decision lunar-refuses-the-return-while-the-blue-star-is-hidden
     * decision moon-trip-refuses-under-the-new-moon
     * decision blue-planet-hangs-fixed-with-its-height-set-by-x
     *
     * @param dimension        the caster's dimension
     * @param x                the caster's x, in blocks, which sets the planet's direction in lunar
     * @param look             the caster's look, any length
     * @param moonAngleDegrees the dimension's moon angle, in degrees, which lunar leaves unread
     * @param sunAngleDegrees  the sun angle where the caster stands, in degrees, which the overworld's moon leaves
     *                         unread
     * @param skyPhase         the phase of the body in the moon's place: the overworld moon's in the overworld, the
     *                         blue planet's in lunar
     * @return the moon or the blue planet the look holds, empty where it holds neither
     */
    public static Optional<Sky> skyAt(ResourceKey<Level> dimension, double x, Vec3 look, float moonAngleDegrees,
            float sunAngleDegrees, MoonPhase skyPhase) {
        Vec3 moon = moonDirection(moonAngleDegrees);
        if (Level.OVERWORLD.equals(dimension)) {
            return heldOrNone(Sky.MOON, moon.y() > 0.0 && skyPhase != MoonPhase.NEW_MOON
                    && holds(look, moon, LOOK_REACH_COSINE));
        }
        if (Lunar.LEVEL.equals(dimension)) {
            return heldOrNone(Sky.BLUE_PLANET, planetShows(x, skyPhase, sunAngleDegrees)
                    && holds(look, planetDirection(x), PLANET_REACH_COSINE));
        }
        return Optional.empty();
    }

    /**
     * Whether the blue planet shows over a lunar x, strictly whether you can see it: above the horizon there on a lit
     * phase, at every hour, and through its dark phase only while its disc stands in front of the sun, eclipsing it;
     * never on the dark side, so a caster there travels back to the light side to see it.
     * decision lunar-refuses-the-return-while-the-blue-star-is-hidden
     * decision dark-side-is-the-half-of-the-moon-past-the-light-side
     *
     * @param x               the x, in blocks
     * @param planetPhase     the planet's phase
     * @param sunAngleDegrees the sun angle over the x, in degrees
     * @return true while the planet shows there
     */
    public static boolean planetShows(double x, MoonPhase planetPhase, float sunAngleDegrees) {
        return planetAboveTheHorizon(x) && (planetPhase != MoonPhase.NEW_MOON || eclipsesTheSun(x, sunAngleDegrees));
    }

    /**
     * Whether the planet's disc stands in front of the sun's center over a lunar x.
     *
     * @param x               the x, in blocks
     * @param sunAngleDegrees the sun angle over the x, in degrees
     * @return true while the planet eclipses the sun
     */
    private static boolean eclipsesTheSun(double x, float sunAngleDegrees) {
        return moonDirection(sunAngleDegrees).dot(planetDirection(x)) >= PLANET_DISC_COSINE;
    }

    private static Optional<Sky> heldOrNone(Sky sky, boolean held) {
        return held ? Optional.of(sky) : Optional.empty();
    }

    private static boolean holds(Vec3 look, Vec3 target, double reachCosine) {
        return look.normalize().dot(target) >= reachCosine;
    }
}
