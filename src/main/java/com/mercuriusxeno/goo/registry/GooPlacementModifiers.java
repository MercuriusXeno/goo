package com.mercuriusxeno.goo.registry;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.lunar.CraterFloorFilter;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registers the placement modifier types goo's placed features name.
 */
public final class GooPlacementModifiers {

    /**
     * Deferred register for placement modifier types.
     */
    public static final DeferredRegister<PlacementModifierType<?>> PLACEMENT_MODIFIER_TYPES =
            DeferredRegister.create(Registries.PLACEMENT_MODIFIER_TYPE, Goo.MODID);

    /**
     * {@code goo:crater_floor}, which lands a lunar feature on a crater's floor.
     * decision lunar-ground-gains-meteoric-iron-impact-glass-breccia-and-crater-ice
     */
    public static final DeferredHolder<PlacementModifierType<?>, PlacementModifierType<CraterFloorFilter>> CRATER_FLOOR =
            PLACEMENT_MODIFIER_TYPES.register("crater_floor", () -> () -> CraterFloorFilter.CODEC);

    private GooPlacementModifiers() {
    }
}
