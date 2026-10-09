package com.mercuriusxeno.goo.registry;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.entity.CompressionSphere;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Entity type registry. World effects stand as blocks; an entity is
 * registered here only where a thing moves on its own.
 */
public final class GooEntities {

    public static final DeferredRegister<EntityType<?>> ENTITIES =
        DeferredRegister.create(Registries.ENTITY_TYPE, Goo.MODID);

    private static final float SPHERE_SIZE = 0.25f;
    private static final int SPHERE_TRACKING_CHUNKS = 6;
    private static final int SPHERE_UPDATE_TICKS = 20;

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

    private GooEntities() {
    }
}
