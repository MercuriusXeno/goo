package com.mercuriusxeno.goo.ability.astral;

import com.mercuriusxeno.goo.lunar.Lunar;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.MoonPhase;
import net.minecraft.world.phys.Vec3;
import java.util.Optional;
import java.util.Set;

/**
 * What Lunar finds along a caster's look: the moon, from the overworld while it stands above the horizon, or the blue
 * planet, from lunar's light side, standing in the moon's place, while it stands above the horizon on a lit phase;
 * anything else Lunar refuses.
 * decision gate-fixates-your-view-on-the-moon-until-it-takes-you
 * decision gate-on-the-blue-star-returns-you-exactly-where-you-left
 * decision blue-star-takes-the-moons-place-with-the-opposite-phase
 * decision lunar-refuses-the-return-while-the-blue-star-is-hidden
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
     * above the horizon, in the overworld or on the moon's light side, says "The big yellow one is the sun!", and any
     * other look shows its dimension's refusal. The dark side draws no sun to hold.
     * decision pointing-at-the-sun-says-the-big-yellow-one-is-the-sun
     * decision dark-side-is-the-half-of-the-moon-past-the-light-side
     *
     * @param dimension       the caster's dimension
     * @param x               the caster's x, in blocks, which the dark side reads in lunar
     * @param look            the caster's look, any length
     * @param sunAngleDegrees the dimension's sun angle, in degrees
     * @return the refusal's lang key
     */
    public static String refusalKey(ResourceKey<Level> dimension, double x, Vec3 look, float sunAngleDegrees) {
        Vec3 sun = moonDirection(sunAngleDegrees);
        boolean holdsTheSun = sunShows(dimension, x) && sun.y() > 0.0 && holds(look, sun, LOOK_REACH_COSINE);
        return holdsTheSun ? SUN_REFUSED : refusalKey(dimension);
    }

    private static boolean sunShows(ResourceKey<Level> dimension, double x) {
        return SUNLIT.contains(dimension) && !(Lunar.LEVEL.equals(dimension) && Lunar.isDarkSide(x));
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
     * the x axis by the angle, then a quarter turn about the y axis. In lunar the blue planet stands there.
     *
     * @param moonAngleDegrees the dimension's moon angle attribute, in degrees
     * @return the unit direction
     */
    public static Vec3 moonDirection(float moonAngleDegrees) {
        double angle = Math.toRadians(moonAngleDegrees);
        return new Vec3(-Math.sin(angle), Math.cos(angle), 0.0);
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
     * What a look finds in a dimension's sky: the moon while it stands above the horizon, and the blue planet while it
     * shows, so the way home waits on the planet showing.
     * decision lunar-refuses-the-return-while-the-blue-star-is-hidden
     *
     * @param dimension        the caster's dimension
     * @param x                the caster's x, in blocks, which the dark side reads in lunar
     * @param look             the caster's look, any length
     * @param moonAngleDegrees the dimension's moon angle, in degrees
     * @param planetPhase      the blue planet's phase, which the overworld's moon leaves unread
     * @return the moon or the blue planet the look holds, empty where it holds neither
     */
    public static Optional<Sky> skyAt(ResourceKey<Level> dimension, double x, Vec3 look, float moonAngleDegrees,
            MoonPhase planetPhase) {
        Vec3 moon = moonDirection(moonAngleDegrees);
        if (Level.OVERWORLD.equals(dimension)) {
            return heldOrNone(Sky.MOON, moon.y() > 0.0 && holds(look, moon, LOOK_REACH_COSINE));
        }
        if (Lunar.LEVEL.equals(dimension)) {
            return heldOrNone(Sky.BLUE_PLANET, planetShows(x, moonAngleDegrees, planetPhase)
                    && holds(look, moon, PLANET_REACH_COSINE));
        }
        return Optional.empty();
    }

    /**
     * Whether the blue planet shows over a lunar x now: above the horizon on a lit phase, and never on the dark side,
     * so a caster there travels back to the light side to see it.
     * decision lunar-refuses-the-return-while-the-blue-star-is-hidden
     * decision dark-side-is-the-half-of-the-moon-past-the-light-side
     *
     * @param x                the x, in blocks
     * @param moonAngleDegrees lunar's moon angle, in degrees, where the planet stands
     * @param planetPhase      the planet's phase
     * @return true while the planet shows there
     */
    public static boolean planetShows(double x, float moonAngleDegrees, MoonPhase planetPhase) {
        return !Lunar.isDarkSide(x) && moonDirection(moonAngleDegrees).y() > 0.0 && planetPhase != MoonPhase.NEW_MOON;
    }

    private static Optional<Sky> heldOrNone(Sky sky, boolean held) {
        return held ? Optional.of(sky) : Optional.empty();
    }

    private static boolean holds(Vec3 look, Vec3 target, double reachCosine) {
        return look.normalize().dot(target) >= reachCosine;
    }
}
