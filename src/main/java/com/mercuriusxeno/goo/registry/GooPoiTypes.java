package com.mercuriusxeno.goo.registry;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import java.util.Set;

/**
 * Registers goo's point-of-interest types, which let the server find goo's entityless blocks near a place without
 * scanning every block around it.
 */
public final class GooPoiTypes {

    /** Deferred register for point-of-interest types. */
    public static final DeferredRegister<PoiType> POI_TYPES =
            DeferredRegister.create(Registries.POINT_OF_INTEREST_TYPE, Goo.MODID);

    /**
     * {@code goo:resonator}, every state of the resonator, so a vibration finds the resonators in hearing range.
     * decision resonator-echoes-a-vibration-as-a-sound-relay
     */
    public static final DeferredHolder<PoiType, PoiType> RESONATOR = POI_TYPES.register("resonator",
            () -> new PoiType(Set.copyOf(GooBlocks.RESONATOR.get().getStateDefinition().getPossibleStates()), 0, 1));

    private GooPoiTypes() {
    }
}
