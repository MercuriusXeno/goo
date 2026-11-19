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
     * How far the light side runs either way of x = 0: half the moon, its border standing at 7.5 million.
     * decision dark-side-is-the-half-of-the-moon-past-the-light-side
     */
    public static final double LIGHT_SIDE_HALF_WIDTH = 3_750_000;

    private Lunar() {
    }

    /**
     * Whether a lunar x stands on the dark side, every x past the light side either way, where it never shines and
     * the blue planet never shows.
     * decision dark-side-is-the-half-of-the-moon-past-the-light-side
     *
     * @param x the x, in blocks
     * @return true past the light side
     */
    public static boolean isDarkSide(double x) {
        return Math.abs(x) > LIGHT_SIDE_HALF_WIDTH;
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
