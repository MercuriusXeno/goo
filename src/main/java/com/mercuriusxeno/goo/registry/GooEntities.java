package com.mercuriusxeno.goo.registry;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.entity.CompressionSphere;
import com.mercuriusxeno.goo.entity.Meteor;
import com.mercuriusxeno.goo.entity.RollingGoo;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Entity type registry. World effects stand as blocks; an entity is
 * registered here only where a thing moves on its own: a goo rolling through
 * the air (decision orb-carries-a-swirling-nova), and the orb a black hole
 * leaves (decision black-hole-leaves-a-compression-sphere).
 */
public final class GooEntities {

    public static final DeferredRegister<EntityType<?>> ENTITIES =
        DeferredRegister.create(Registries.ENTITY_TYPE, Goo.MODID);

    /** The ball a rolling goo flies as, about half a block across. */
    private static final float ROLLING_GOO_SIZE = 0.5f;
    /** Chunks out to which clients track a rolling goo. */
    private static final int ROLLING_GOO_TRACKING_RANGE = 8;
    private static final float SPHERE_SIZE = 0.25f;
    private static final int SPHERE_TRACKING_CHUNKS = 6;
    private static final int SPHERE_UPDATE_TICKS = 20;
    private static final float METEOR_SIZE = 1.0f;
    private static final int METEOR_TRACKING_CHUNKS = 10;

    /**
     * A goo rolling through the air in a straight line, such as frost's Orb
     * (decision orb-carries-a-swirling-nova).
     */
    public static final DeferredHolder<EntityType<?>, EntityType<RollingGoo>> ROLLING_GOO =
        ENTITIES.register("rolling_goo", id -> EntityType.Builder.<RollingGoo>of(RollingGoo::new, MobCategory.MISC)
            .sized(ROLLING_GOO_SIZE, ROLLING_GOO_SIZE)
            .noLootTable()
            .clientTrackingRange(ROLLING_GOO_TRACKING_RANGE)
            .updateInterval(1)
            .build(ResourceKey.create(Registries.ENTITY_TYPE, id)));

    /**
     * The orb a black hole leaves holding what it pulled in
     * (decision black-hole-leaves-a-compression-sphere).
     */
    public static final DeferredHolder<EntityType<?>, EntityType<CompressionSphere>> COMPRESSION_SPHERE =
        ENTITIES.register("compression_sphere", id -> EntityType.Builder
            .<CompressionSphere>of(CompressionSphere::new, MobCategory.MISC)
            .sized(SPHERE_SIZE, SPHERE_SIZE)
            .clientTrackingRange(SPHERE_TRACKING_CHUNKS)
            .updateInterval(SPHERE_UPDATE_TICKS)
            .noLootTable()
            .build(ResourceKey.create(Registries.ENTITY_TYPE, id)));

    /**
     * Meteo's meteor, falling from high above its target to a crater
     * (decision meteo-needs-a-clear-sky).
     */
    public static final DeferredHolder<EntityType<?>, EntityType<Meteor>> METEOR =
        ENTITIES.register("meteor", id -> EntityType.Builder.<Meteor>of(Meteor::new, MobCategory.MISC)
            .sized(METEOR_SIZE, METEOR_SIZE)
            .clientTrackingRange(METEOR_TRACKING_CHUNKS)
            .updateInterval(1)
            .noLootTable()
            .build(ResourceKey.create(Registries.ENTITY_TYPE, id)));

    private GooEntities() {
    }
}
