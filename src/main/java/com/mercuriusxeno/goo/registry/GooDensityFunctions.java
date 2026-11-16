package com.mercuriusxeno.goo.registry;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.lunar.CraterField;
import com.mercuriusxeno.goo.lunar.GiantCraterField;
import com.mercuriusxeno.goo.lunar.RilleField;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registers the density function types goo's worldgen JSON names.
 */
public final class GooDensityFunctions {

    /**
     * Deferred register for density function types.
     */
    public static final DeferredRegister<MapCodec<? extends DensityFunction>> DENSITY_FUNCTION_TYPES =
            DeferredRegister.create(Registries.DENSITY_FUNCTION_TYPE, Goo.MODID);

    /**
     * {@code goo:craters}, the craters lunar's noise settings sink into its surface.
     * decision lunar-generates-from-its-own-noise-and-surface-rules
     */
    public static final DeferredHolder<MapCodec<? extends DensityFunction>, MapCodec<CraterField>> CRATERS =
            DENSITY_FUNCTION_TYPES.register("craters", () -> CraterField.DATA_CODEC);

    /**
     * {@code goo:rilles}, the channels lunar's noise settings cut through its basins.
     * decision lunar-surface-gains-giant-craters-mountains-rilles-and-boulder-fields
     */
    public static final DeferredHolder<MapCodec<? extends DensityFunction>, MapCodec<RilleField>> RILLES =
            DENSITY_FUNCTION_TYPES.register("rilles", () -> RilleField.DATA_CODEC);

    /**
     * {@code goo:giant_craters}, the rare craters hundreds of blocks wide lunar's noise settings sink into its surface.
     * decision lunar-surface-gains-giant-craters-mountains-rilles-and-boulder-fields
     */
    public static final DeferredHolder<MapCodec<? extends DensityFunction>, MapCodec<GiantCraterField>> GIANT_CRATERS =
            DENSITY_FUNCTION_TYPES.register("giant_craters", () -> GiantCraterField.DATA_CODEC);

    private GooDensityFunctions() {
    }
}
