package com.mercuriusxeno.goo.registry;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.lunar.AstralCrystalClusterFeature;
import com.mercuriusxeno.goo.lunar.BoulderFeature;
import com.mercuriusxeno.goo.lunar.CraterPoolsFeature;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registers the feature types goo's configured features name.
 */
public final class GooFeatures {

    /**
     * Deferred register for feature types.
     */
    public static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(Registries.FEATURE, Goo.MODID);

    /**
     * {@code goo:boulder}, one big lumpy boulder a crater threw.
     * decision lunar-surface-gains-giant-craters-mountains-rilles-and-boulder-fields
     */
    public static final DeferredHolder<Feature<?>, BoulderFeature> BOULDER = FEATURES.register("boulder",
            BoulderFeature::new);

    /**
     * {@code goo:crater_pools}, the frozen pools in the low ground of lunar's deep and giant crater floors.
     * decision lunar-ground-gains-meteoric-iron-impact-glass-breccia-and-crater-ice
     */
    public static final DeferredHolder<Feature<?>, CraterPoolsFeature> CRATER_POOLS = FEATURES.register("crater_pools",
            CraterPoolsFeature::new);

    /**
     * {@code goo:astral_crystal_cluster}, a small cluster of astral crystal on the rock of lunar's middle caverns.
     * decision astral-crystal-is-lunars-scarce-rich-block
     */
    public static final DeferredHolder<Feature<?>, AstralCrystalClusterFeature> ASTRAL_CRYSTAL_CLUSTER =
            FEATURES.register("astral_crystal_cluster", AstralCrystalClusterFeature::new);

    private GooFeatures() {
    }
}
