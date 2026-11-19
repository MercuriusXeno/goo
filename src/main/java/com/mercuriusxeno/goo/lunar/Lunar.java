package com.mercuriusxeno.goo.lunar;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Astral's lunar dimension: its level key, and the airlessness a player meets there.
 */
public final class Lunar {

    /** goo:lunar's level key. */
    public static final ResourceKey<Level> LEVEL = ResourceKey.create(Registries.DIMENSION,
            Identifier.fromNamespaceAndPath(Goo.MODID, "lunar"));

    /**
     * Half the light side's width along x, in blocks: the light side runs from minus this to plus this, half the
     * moon, and the dark side is every x past it.
     * decision dark-side-is-the-half-of-the-moon-past-the-light-side
     * decision traversal-to-the-moon-always-lands-on-the-light-side
     */
    public static final int LIGHT_SIDE_HALF_WIDTH = 3_750_000;
    /**
     * The moon's way around along x, in blocks: the light side and the dark side, each half of it, the seam at plus
     * and minus half.
     */
    private static final double WAY_AROUND = 4.0 * LIGHT_SIDE_HALF_WIDTH;
    private static final double FULL_TURN_DEGREES = 360.0;

    private Lunar() {
    }

    /**
     * Whether a lunar x stands on the dark side, every x past the light side either way, where the blue planet never
     * shows.
     * decision dark-side-is-the-half-of-the-moon-past-the-light-side
     *
     * @param x the x, in blocks
     * @return true past the light side
     */
    public static boolean isDarkSide(double x) {
        return Math.abs(x) > LIGHT_SIDE_HALF_WIDTH;
    }

    /**
     * The blue planet's angle over a lunar x, as a moon angle reads, the same at every hour: the moon keeps one face to
     * the planet, so the planet stands straight up at x 0 and leans back toward x 0 as x runs out, one full circle over
     * the moon's whole way around, on the horizon at the light side's edges and underfoot at the seam.
     * decision blue-planet-hangs-fixed-with-its-height-set-by-x
     *
     * @param x the x, in blocks
     * @return the angle, in degrees, 0 overhead
     */
    public static double planetAngleDegrees(double x) {
        return x / WAY_AROUND * FULL_TURN_DEGREES;
    }

    /**
     * Whether an entity breathes no air where it stands: a player in lunar, whose air drains as underwater
     * through vanilla's own path, so water breathing, a turtle helmet and Pneuma hold it as they do underwater.
     * decision astral-air-drains-as-underwater
     *
     * @param entity the entity
     * @return true for a player in lunar
     */
    public static boolean isAirless(Entity entity) {
        return entity instanceof Player && entity.level().dimension() == LEVEL;
    }
}
