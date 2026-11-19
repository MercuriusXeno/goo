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
     * Half the light side's width along x, in blocks: the light side runs from minus this to plus this.
     * decision traversal-to-the-moon-always-lands-on-the-light-side
     */
    public static final int LIGHT_SIDE_HALF_WIDTH = 3_750_000;

    private Lunar() {
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
