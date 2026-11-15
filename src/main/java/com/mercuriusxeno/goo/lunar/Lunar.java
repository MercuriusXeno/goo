package com.mercuriusxeno.goo.lunar;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Astral's lunar dimension: its level key, and the airlessness a player meets there.
 */
public final class Lunar {

    /** goo:lunar's level key. */
    public static final ResourceKey<Level> LEVEL = ResourceKey.create(Registries.DIMENSION,
            Identifier.fromNamespaceAndPath(Goo.MODID, "lunar"));

    /** The blue overworld star's direction before it is made unit: high in the south east. */
    private static final double TOWARD_EAST = 0.45;
    private static final double TOWARD_UP = 0.62;
    private static final double TOWARD_SOUTH = 0.64;

    /**
     * The unit direction lunar's sky shows the overworld along, a bright blue star fixed in the void, which Astral's
     * gate home looks at.
     * decision astral-sky-is-the-void-with-one-bright-blue-overworld-star
     */
    public static final Vec3 OVERWORLD_STAR_DIRECTION = new Vec3(TOWARD_EAST, TOWARD_UP, TOWARD_SOUTH).normalize();

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
