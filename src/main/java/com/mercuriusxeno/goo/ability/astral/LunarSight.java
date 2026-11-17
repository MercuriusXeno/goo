package com.mercuriusxeno.goo.ability.astral;

import com.mercuriusxeno.goo.lunar.Lunar;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.MoonPhase;
import net.minecraft.world.phys.Vec3;
import java.util.Optional;

/**
 * What Lunar finds along a caster's look: the moon, from the overworld while it stands above the horizon,
 * or the blue planet, from lunar, standing in the moon's place; anything else Lunar refuses.
 * decision gate-fixates-your-view-on-the-moon-until-it-takes-you
 * decision gate-on-the-blue-star-returns-you-exactly-where-you-left
 * decision blue-star-takes-the-moons-place-with-the-opposite-phase
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
     * What a look finds in a dimension's sky.
     *
     * @param dimension        the caster's dimension
     * @param look             the caster's look, any length
     * @param moonAngleDegrees the dimension's moon angle, in degrees
     * @return the moon or the blue planet the look holds, empty where it holds neither
     */
    public static Optional<Sky> skyAt(ResourceKey<Level> dimension, Vec3 look, float moonAngleDegrees) {
        Vec3 moon = moonDirection(moonAngleDegrees);
        if (Level.OVERWORLD.equals(dimension)) {
            return moon.y() > 0.0 && holds(look, moon, LOOK_REACH_COSINE) ? Optional.of(Sky.MOON) : Optional.empty();
        }
        if (Lunar.LEVEL.equals(dimension) && holds(look, moon, PLANET_REACH_COSINE)) {
            return Optional.of(Sky.BLUE_PLANET);
        }
        return Optional.empty();
    }

    private static boolean holds(Vec3 look, Vec3 target, double reachCosine) {
        return look.normalize().dot(target) >= reachCosine;
    }
}
