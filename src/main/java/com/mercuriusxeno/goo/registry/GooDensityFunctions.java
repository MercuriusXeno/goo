package com.mercuriusxeno.goo.registry;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.lunar.CraterField;
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

    private GooDensityFunctions() {
    }
}
