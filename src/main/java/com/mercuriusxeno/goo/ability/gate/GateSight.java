package com.mercuriusxeno.goo.ability.gate;

import com.mercuriusxeno.goo.lunar.Lunar;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import java.util.Optional;

/**
 * What Astral's gate finds along a caster's look: the moon, from the overworld while it stands above the horizon,
 * or the blue overworld star, from lunar; anything else the gate refuses.
 * decision gate-fixates-your-view-on-the-moon-until-it-takes-you
 * decision gate-on-the-blue-star-returns-you-exactly-where-you-left
 */
public final class GateSight {

    /** How far off the moon's or the star's center a look may stray and still hold it, in degrees. */
    static final double LOOK_REACH_DEGREES = 8.0;
    private static final double LOOK_REACH_COSINE = Math.cos(Math.toRadians(LOOK_REACH_DEGREES));

    /** What the gate fixates on. */
    public enum Sky {
        /** The overworld's moon, which carries the caster to lunar. */
        MOON,
        /** Lunar's blue overworld star, which carries the caster home. */
        OVERWORLD_STAR
    }

    private GateSight() {
    }

    /**
     * The moon's direction from the camera at a moon angle, as vanilla's sky draws it: straight up turned about
     * the x axis by the angle, then a quarter turn about the y axis.
     *
     * @param moonAngleDegrees the overworld's moon angle attribute, in degrees
     * @return the unit direction
     */
    public static Vec3 moonDirection(float moonAngleDegrees) {
        double angle = Math.toRadians(moonAngleDegrees);
        return new Vec3(-Math.sin(angle), Math.cos(angle), 0.0);
    }

    /**
     * What a look finds in a dimension's sky.
     *
     * @param dimension        the caster's dimension
     * @param look             the caster's look, any length
     * @param moonAngleDegrees the dimension's moon angle, in degrees
     * @return the moon or the overworld star the look holds, empty where it holds neither
     */
    public static Optional<Sky> skyAt(ResourceKey<Level> dimension, Vec3 look, float moonAngleDegrees) {
        if (Level.OVERWORLD.equals(dimension)) {
            Vec3 moon = moonDirection(moonAngleDegrees);
            return moon.y() > 0.0 && holds(look, moon) ? Optional.of(Sky.MOON) : Optional.empty();
        }
        if (Lunar.LEVEL.equals(dimension) && holds(look, Lunar.OVERWORLD_STAR_DIRECTION)) {
            return Optional.of(Sky.OVERWORLD_STAR);
        }
        return Optional.empty();
    }

    /**
     * The direction of what a look finds.
     *
     * @param sky              the moon or the overworld star
     * @param moonAngleDegrees the moon angle the moon stands at
     * @return its unit direction
     */
    public static Vec3 directionOf(Sky sky, float moonAngleDegrees) {
        return sky == Sky.MOON ? moonDirection(moonAngleDegrees) : Lunar.OVERWORLD_STAR_DIRECTION;
    }

    private static boolean holds(Vec3 look, Vec3 target) {
        return look.normalize().dot(target) >= LOOK_REACH_COSINE;
    }
}
