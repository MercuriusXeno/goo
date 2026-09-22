package com.mercuriusxeno.goo.registry;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.fluid.GooFluidType;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Registers the one goo {@link FluidType} (decision generic-goo-fluids).
 * Its density, viscosity, temperature and extinguishing come from the type
 * stamped at a position; the properties here are the positionless defaults.
 */
public final class GooFluidTypes {

    /**
     * Deferred register for NeoForge fluid types.
     */
    public static final DeferredRegister<FluidType> FLUID_TYPES =
            DeferredRegister.create(NeoForgeRegistries.FLUID_TYPES, Goo.MODID);

    /**
     * Water-like default on the water = 1000 scale, answered where no
     * position names a type.
     */
    private static final int DEFAULT_DENSITY = 1000;
    private static final int DEFAULT_VISCOSITY = 1000;
    private static final int DEFAULT_TEMPERATURE = 300;

    /**
     * The one goo fluid type.
     */
    public static final DeferredHolder<FluidType, GooFluidType> GOO =
            FLUID_TYPES.register(GooFluids.GOO_PATH, () -> new GooFluidType(FluidType.Properties.create()
                    .density(DEFAULT_DENSITY)
                    .viscosity(DEFAULT_VISCOSITY)
                    .temperature(DEFAULT_TEMPERATURE)));

    private GooFluidTypes() {
    }
}
