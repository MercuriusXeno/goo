package com.mercuriusxeno.goo.registry;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.entity.BlockHunk;
import com.mercuriusxeno.goo.entity.CompressionSphere;
import com.mercuriusxeno.goo.entity.EldritchMonstrosity;
import com.mercuriusxeno.goo.entity.FeedPile;
import com.mercuriusxeno.goo.entity.Meteor;
import com.mercuriusxeno.goo.entity.RollingGoo;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Entity type registry. World effects stand as blocks; an entity is
 * registered here only where a thing moves or acts on its own: a goo rolling through
 * the air (decision orb-carries-a-swirling-nova), the orb a black hole
 * leaves (decision black-hole-leaves-a-compression-sphere), the feed
 * Jelly's Feed lays (decision feed-blob-feeds-and-draws-mobs), the Eldritch
 * Monstrosity living out of phase (decision monstrosities-harvest-under-quantum),
 * and a hunk of blocks moving as one (decision spire-entity-izes-its-blocks-and-raises-them-smoothly).
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
    private static final float METEOR_SIZE = 1.0f;
    private static final float HUNK_CELL_SIZE = 1.0f;
    private static final int HUNK_TRACKING_CHUNKS = 10;
    private static final int METEOR_TRACKING_CHUNKS = 32;
    private static final float MONSTROSITY_WIDTH = 0.8f;
    private static final float MONSTROSITY_HEIGHT = 2.4f;
    private static final int MONSTROSITY_TRACKING_CHUNKS = 8;

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
     * The feed Jelly's Feed lays on the ground, drawing the mobs about it
     * (decision feed-blob-feeds-and-draws-mobs).
     */
    public static final DeferredHolder<EntityType<?>, EntityType<FeedPile>> FEED_PILE =
        ENTITIES.register("feed_pile", id -> EntityType.Builder
            .<FeedPile>of(FeedPile::new, MobCategory.MISC)
            .sized(SPHERE_SIZE, SPHERE_SIZE)
            .clientTrackingRange(SPHERE_TRACKING_CHUNKS)
            .updateInterval(SPHERE_UPDATE_TICKS)
            .noLootTable()
            .build(ResourceKey.create(Registries.ENTITY_TYPE, id)));

    /**
     * A hunk of blocks lifted out of the world and carried as one, such as
     * Spire's rising stone (decision spire-entity-izes-its-blocks-and-raises-them-smoothly).
     */
    public static final DeferredHolder<EntityType<?>, EntityType<BlockHunk>> BLOCK_HUNK =
        ENTITIES.register("block_hunk", id -> EntityType.Builder.<BlockHunk>of(BlockHunk::new, MobCategory.MISC)
            .sized(HUNK_CELL_SIZE, HUNK_CELL_SIZE)
            .clientTrackingRange(HUNK_TRACKING_CHUNKS)
            .updateInterval(1)
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

    /**
     * The Eldritch Monstrosity, out of phase between dimensions, harvested for
     * xeno goo (decision monstrosities-harvest-under-quantum).
     */
    public static final DeferredHolder<EntityType<?>, EntityType<EldritchMonstrosity>> ELDRITCH_MONSTROSITY =
        ENTITIES.register("eldritch_monstrosity", id -> EntityType.Builder
            .<EldritchMonstrosity>of(EldritchMonstrosity::new, MobCategory.MONSTER)
            .sized(MONSTROSITY_WIDTH, MONSTROSITY_HEIGHT)
            .clientTrackingRange(MONSTROSITY_TRACKING_CHUNKS)
            .build(ResourceKey.create(Registries.ENTITY_TYPE, id)));

    private GooEntities() {
    }

    /**
     * Lets a monstrosity spawn on the ground only where it is dark and deep.
     *
     * @param event the spawn placement registration event
     */
    @SubscribeEvent
    public static void onRegisterSpawnPlacements(RegisterSpawnPlacementsEvent event) {
        event.register(ELDRITCH_MONSTROSITY.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                EldritchMonstrosity::checkSpawnRules, RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }

    /**
     * Gives the monstrosity a monster's attributes.
     *
     * @param event the attribute creation event
     */
    @SubscribeEvent
    public static void onAttributeCreation(EntityAttributeCreationEvent event) {
        event.put(ELDRITCH_MONSTROSITY.get(), EldritchMonstrosity.createAttributes().build());
    }
}