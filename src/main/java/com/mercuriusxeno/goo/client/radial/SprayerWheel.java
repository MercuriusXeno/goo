package com.mercuriusxeno.goo.client.radial;

import com.mercuriusxeno.goo.item.CanisterFluidContent;
import com.mercuriusxeno.goo.item.SprayerFlow;
import com.mercuriusxeno.goo.item.SprayerNozzle;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.resources.ResourceKey;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/**
 * The sprayer wheel's model: one petal for each goo type of the fitted canister that has a residual, most held
 * first, the flow steps from low to high, fanned out from each type petal, and one petal for each nozzle.
 * decision sprayer-wheel-picks-nozzle-flow-and-goo-type
 * decision spray-item-sprays-more-or-less-per-tick
 * decision spray-item-takes-different-nozzles-doing-different-things
 *
 * @param types   the sprayable goo types, most held first
 * @param flows   the flow steps, low to high
 * @param nozzles the nozzles
 */
public record SprayerWheel(List<ResourceKey<GooTypeDefinition>> types, List<SprayerFlow> flows,
                           List<SprayerNozzle> nozzles) {

    /**
     * The wheel over a canister's content.
     *
     * @param content     the fitted canister's content
     * @param hasResidual whether a goo type has a residual
     * @return the wheel's model
     */
    public static SprayerWheel of(CanisterFluidContent content, Predicate<ResourceKey<GooTypeDefinition>> hasResidual) {
        return new SprayerWheel(content.gooVolumesDominantFirst().keySet().stream().filter(hasResidual).toList(),
                Arrays.stream(SprayerFlow.values()).sorted(Comparator.comparingDouble(SprayerFlow::rate)).toList(),
                List.of(SprayerNozzle.values()));
    }
}
