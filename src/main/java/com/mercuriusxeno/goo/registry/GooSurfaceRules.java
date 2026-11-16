package com.mercuriusxeno.goo.registry;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.lunar.CraterBandCondition;
import com.mercuriusxeno.goo.lunar.CraterRaysCondition;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.SurfaceRules;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registers the surface rule condition types goo's noise settings name.
 */
public final class GooSurfaceRules {

    /**
     * Deferred register for surface rule condition types.
     */
    public static final DeferredRegister<MapCodec<? extends SurfaceRules.ConditionSource>> CONDITION_TYPES =
            DeferredRegister.create(Registries.MATERIAL_CONDITION, Goo.MODID);

    /**
     * {@code goo:crater_band}, which lays a lunar surface block within a band of a crater's radii.
     * decision lunar-ground-gains-meteoric-iron-impact-glass-breccia-and-crater-ice
     */
    public static final DeferredHolder<MapCodec<? extends SurfaceRules.ConditionSource>, MapCodec<CraterBandCondition>>
            CRATER_BAND = CONDITION_TYPES.register("crater_band", () -> CraterBandCondition.DATA_CODEC);

    /**
     * {@code goo:crater_rays}, which exposes lunar's stone along the large craters' ejecta rays.
     * decision lunar-surface-gains-giant-craters-mountains-rilles-and-boulder-fields
     */
    public static final DeferredHolder<MapCodec<? extends SurfaceRules.ConditionSource>, MapCodec<CraterRaysCondition>>
            CRATER_RAYS = CONDITION_TYPES.register("crater_rays", () -> CraterRaysCondition.DATA_CODEC);

    private GooSurfaceRules() {
    }
}
