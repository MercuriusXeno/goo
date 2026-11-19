package com.mercuriusxeno.goo.registry;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.lunar.CraterFloorFilter;
import com.mercuriusxeno.goo.lunar.DarkSideCount;
import com.mercuriusxeno.goo.lunar.GiantCraterBoulders;
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

    /**
     * {@code goo:giant_crater_boulders}, which lands a boulder on each spot a giant crater threw one to.
     * decision lunar-surface-gains-giant-craters-mountains-rilles-and-boulder-fields
     */
    public static final DeferredHolder<PlacementModifierType<?>, PlacementModifierType<GiantCraterBoulders>>
            GIANT_CRATER_BOULDERS = PLACEMENT_MODIFIER_TYPES.register("giant_crater_boulders",
            () -> () -> GiantCraterBoulders.CODEC);

    /**
     * {@code goo:dark_side_count}, a feature's attempts for each chunk at a count for each side of the moon.
     * decision dark-side-is-an-endless-night-of-eldritch-rich-in-void-and-xeno
     */
    public static final DeferredHolder<PlacementModifierType<?>, PlacementModifierType<DarkSideCount>>
            DARK_SIDE_COUNT = PLACEMENT_MODIFIER_TYPES.register("dark_side_count", () -> () -> DarkSideCount.CODEC);

    private GooPlacementModifiers() {
    }
}
