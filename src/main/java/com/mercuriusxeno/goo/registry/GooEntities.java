package com.mercuriusxeno.goo.registry;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.entity.RollingGoo;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Entity type registry: world effects use blocks, and the one entity is a
 * goo that rolls through the air (decision orb-carries-a-swirling-nova).
 */
public class GooEntities {

    public static final DeferredRegister<EntityType<?>> ENTITIES =
        DeferredRegister.create(Registries.ENTITY_TYPE, Goo.MODID);

    /** The ball a rolling goo flies as, about half a block across. */
    private static final float ROLLING_GOO_SIZE = 0.5f;
    /** Chunks out to which clients track a rolling goo. */
    private static final int ROLLING_GOO_TRACKING_RANGE = 8;

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
}
