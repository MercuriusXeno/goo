package com.mercuriusxeno.goo.registry;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.lunar.CraterField;
import com.mercuriusxeno.goo.lunar.CrystalCaverns;
import com.mercuriusxeno.goo.lunar.GiantCraterField;
import com.mercuriusxeno.goo.lunar.LavaTubes;
import com.mercuriusxeno.goo.lunar.RilleField;
import com.mercuriusxeno.goo.lunar.RubbleCaves;
import com.mercuriusxeno.goo.lunar.SeaCavern;
import com.mercuriusxeno.goo.lunar.WarrenTunnels;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
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
    public static final DeferredHolder<MapCodec<? extends DensityFunction>, MapCodec<CraterField.Definition>> CRATERS =
            DENSITY_FUNCTION_TYPES.register("craters", () -> CraterField.DATA_CODEC);

    /**
     * {@code goo:rilles}, the channels lunar's noise settings cut through its basins.
     * decision lunar-surface-gains-giant-craters-mountains-rilles-and-boulder-fields
     */
    public static final DeferredHolder<MapCodec<? extends DensityFunction>, MapCodec<RilleField.Definition>> RILLES =
            DENSITY_FUNCTION_TYPES.register("rilles", () -> RilleField.DATA_CODEC);

    /**
     * {@code goo:giant_craters}, the rare craters hundreds of blocks wide lunar's noise settings sink into its surface.
     * decision lunar-surface-gains-giant-craters-mountains-rilles-and-boulder-fields
     */
    public static final DeferredHolder<MapCodec<? extends DensityFunction>, MapCodec<GiantCraterField.Definition>> GIANT_CRATERS =
            DENSITY_FUNCTION_TYPES.register("giant_craters", () -> GiantCraterField.DATA_CODEC);

    /**
     * {@code goo:sea_cavern}, the vast cavern lunar's noise settings open over its molten iron sea.
     * decision lunar-underground-changes-by-depth-down-to-a-molten-iron-sea
     */
    public static final DeferredHolder<MapCodec<? extends DensityFunction>, MapCodec<SeaCavern.Definition>> SEA_CAVERN =
            DENSITY_FUNCTION_TYPES.register("sea_cavern", () -> SeaCavern.DATA_CODEC);

    /**
     * {@code goo:lava_tubes}, the tunnels lunar's noise settings wind from its surface down to its molten iron sea.
     * decision lunar-underground-changes-by-depth-down-to-a-molten-iron-sea
     */
    public static final DeferredHolder<MapCodec<? extends DensityFunction>, MapCodec<LavaTubes.Definition>> LAVA_TUBES =
            DENSITY_FUNCTION_TYPES.register("lava_tubes", () -> LavaTubes.DATA_CODEC);

    /**
     * {@code goo:rubble_caves}, the low, wide caves lunar's noise settings open through its shallow zone's breccia.
     * decision lunar-underground-changes-by-depth-down-to-a-molten-iron-sea
     */
    public static final DeferredHolder<MapCodec<? extends DensityFunction>, MapCodec<RubbleCaves.Definition>> RUBBLE_CAVES =
            DENSITY_FUNCTION_TYPES.register("rubble_caves", () -> RubbleCaves.DATA_CODEC);

    /**
     * {@code goo:crystal_caverns}, the large caverns lunar's noise settings open through its middle zone.
     * decision lunar-underground-changes-by-depth-down-to-a-molten-iron-sea
     */
    public static final DeferredHolder<MapCodec<? extends DensityFunction>, MapCodec<CrystalCaverns.Definition>> CRYSTAL_CAVERNS =
            DENSITY_FUNCTION_TYPES.register("crystal_caverns", () -> CrystalCaverns.DATA_CODEC);

    /**
     * {@code goo:warrens}, the tight tunnels lunar's noise settings twist through its warren zone.
     * decision lunar-underground-changes-by-depth-down-to-a-molten-iron-sea
     */
    public static final DeferredHolder<MapCodec<? extends DensityFunction>, MapCodec<WarrenTunnels.Definition>> WARRENS =
            DENSITY_FUNCTION_TYPES.register("warrens", () -> WarrenTunnels.DATA_CODEC);

    private GooDensityFunctions() {
    }
}
