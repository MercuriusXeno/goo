package com.mercuriusxeno.goo.registry;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.entity.CompressionSphere;
import com.mercuriusxeno.goo.entity.Meteor;
import com.mercuriusxeno.goo.entity.RollingGoo;
import com.mercuriusxeno.goo.entity.SwapBody;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Entity type registry. World effects stand as blocks; an entity is
 * registered here only where a thing moves on its own: a goo rolling through
 * the air (decision orb-carries-a-swirling-nova), the orb a black hole
 * leaves (decision black-hole-leaves-a-compression-sphere), and a quantum
 * swap body (decision swap-body-stands-until-reverted).
 */
@EventBusSubscriber(modid = Goo.MODID)
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
    private static final float PLAYER_WIDTH = 0.6f;
    private static final float PLAYER_HEIGHT = 1.8f;
    private static final float PLAYER_EYE_HEIGHT = 1.62f;
    private static final int SWAP_BODY_TRACKING_CHUNKS = 10;
    private static final int SWAP_BODY_UPDATE_TICKS = 2;
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
     * A quantum swap body: an inert figure wearing its owner's skin
     * (decision swap-body-stands-until-reverted), sized as a player.
     */
    public static final DeferredHolder<EntityType<?>, EntityType<SwapBody>> SWAP_BODY =
        ENTITIES.register("swap_body", id -> EntityType.Builder.<SwapBody>of(SwapBody::build, MobCategory.MISC)
            .sized(PLAYER_WIDTH, PLAYER_HEIGHT)
            .eyeHeight(PLAYER_EYE_HEIGHT)
            .noLootTable()
            .clientTrackingRange(SWAP_BODY_TRACKING_CHUNKS)
            .updateInterval(SWAP_BODY_UPDATE_TICKS)
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

    /**
     * Gives the swap body the attributes every living thing carries, without
     * which it cannot stand.
     *
     * @param event the attribute creation event
     */
    @SubscribeEvent
    public static void onAttributeCreation(EntityAttributeCreationEvent event) {
        event.put(SWAP_BODY.get(), LivingEntity.createLivingAttributes().build());
    }
}