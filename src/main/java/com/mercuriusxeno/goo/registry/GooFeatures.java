package com.mercuriusxeno.goo.registry;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.lunar.AirGeyserFeature;
import com.mercuriusxeno.goo.lunar.BoulderFeature;
import com.mercuriusxeno.goo.lunar.CraterPoolsFeature;
import com.mercuriusxeno.goo.lunar.CrystalClusters;
import com.mercuriusxeno.goo.lunar.GooPocketFeature;
import com.mercuriusxeno.goo.lunar.NativeIronLodeFeature;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registers the feature types goo's features name.
 */
public final class GooFeatures {

    /**
     * Deferred register for feature types.
     */
    public static final DeferredRegister<MapCodec<? extends Feature>> FEATURES =
            DeferredRegister.create(Registries.FEATURE_TYPE, Goo.MODID);

    /**
     * {@code goo:boulder}, one big lumpy boulder a crater threw.
     * decision lunar-surface-gains-giant-craters-mountains-rilles-and-boulder-fields
     */
    public static final DeferredHolder<MapCodec<? extends Feature>, MapCodec<BoulderFeature>> BOULDER = FEATURES.register("boulder",
            () -> BoulderFeature.CODEC);

    /**
     * {@code goo:crater_pools}, the frozen pools in the lows of lunar's shadowed basins and its giant crater floors.
     * decision lunar-ground-gains-meteoric-iron-impact-glass-breccia-and-crater-ice
     */
    public static final DeferredHolder<MapCodec<? extends Feature>, MapCodec<CraterPoolsFeature>> CRATER_POOLS = FEATURES.register("crater_pools",
            () -> CraterPoolsFeature.CODEC);

    /**
     * {@code goo:astral_crystal_cluster}, a small cluster of astral crystal on the rock of lunar's middle caverns.
     * decision astral-crystal-is-lunars-scarce-rich-block
     */
    public static final DeferredHolder<MapCodec<? extends Feature>, MapCodec<CrystalClusters>> ASTRAL_CRYSTAL_CLUSTER =
            FEATURES.register("astral_crystal_cluster", CrystalClusters.ASTRAL::codec);

    /**
     * {@code goo:void_crystal_cluster}, a small cluster of void crystal on the dark side's surface.
     * decision void-crystals-spawn-on-the-moon
     */
    public static final DeferredHolder<MapCodec<? extends Feature>, MapCodec<CrystalClusters>> VOID_CRYSTAL_CLUSTER =
            FEATURES.register("void_crystal_cluster", CrystalClusters.VOID::codec);

    /**
     * {@code goo:native_iron_lode}, a native iron lode set into the face of a rubble cave.
     * decision native-iron-lodes-are-worth-whole-iron-blocks
     */
    public static final DeferredHolder<MapCodec<? extends Feature>, MapCodec<NativeIronLodeFeature>> NATIVE_IRON_LODE =
            FEATURES.register("native_iron_lode", () -> NativeIronLodeFeature.CODEC);

    /**
     * {@code goo:air_geyser}, a hole in lunar's ground holding Updraft goo, with trace Updraft around it.
     * decision air-geysers-of-updraft-goo-dot-lunar
     */
    public static final DeferredHolder<MapCodec<? extends Feature>, MapCodec<AirGeyserFeature>> AIR_GEYSER =
            FEATURES.register("air_geyser", () -> AirGeyserFeature.CODEC);

    /**
     * {@code goo:goo_pocket}, a few cells of the world goo lunar's biome rule rolls, laid on open ground.
     * decision frost-astral-and-xeno-goo-spawn-periodically-on-lunar
     */
    public static final DeferredHolder<MapCodec<? extends Feature>, MapCodec<GooPocketFeature>> GOO_POCKET =
            FEATURES.register("goo_pocket", () -> GooPocketFeature.CODEC);

    private GooFeatures() {
    }
}
