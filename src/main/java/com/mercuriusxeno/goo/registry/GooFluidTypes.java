package com.mercuriusxeno.goo.registry;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.fluid.GooFluidType;
import com.mercuriusxeno.goo.fluid.MoltenIronFluidType;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Registers the one goo {@link FluidType} (decision generic-goo-fluids).
 * Its density, viscosity, temperature and extinguishing come from the type
 * stamped at a position; the properties here are the positionless defaults.
 * Molten iron's fluid type registers beside it.
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

    /** Lava's density, viscosity and glow on the water = 1000 scale; iron melts near 1800 K. */
    private static final int MOLTEN_IRON_DENSITY = 3000;
    private static final int MOLTEN_IRON_VISCOSITY = 6000;
    private static final int MOLTEN_IRON_TEMPERATURE = 1800;
    private static final int MOLTEN_IRON_GLOW = 15;

    /**
     * Molten iron, the liquid of lunar's core: heavy, slow and glowing as lava, nothing a player swims or drowns in.
     * decision lunar-matter-is-anorthosite-basalt-regolith-and-an-iron-core
     */
    public static final DeferredHolder<FluidType, FluidType> MOLTEN_IRON =
            FLUID_TYPES.register(GooFluids.MOLTEN_IRON_PATH, () -> new MoltenIronFluidType(FluidType.Properties.create()
                    .density(MOLTEN_IRON_DENSITY)
                    .viscosity(MOLTEN_IRON_VISCOSITY)
                    .temperature(MOLTEN_IRON_TEMPERATURE)
                    .lightLevel(MOLTEN_IRON_GLOW)
                    .canSwim(false)
                    .canDrown(false)));

    private GooFluidTypes() {
    }
}
