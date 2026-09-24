package com.mercuriusxeno.goo.client.hud;

import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.item.GooContents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import java.util.Set;

/**
 * Immutable snapshot of crucible state for HUD rendering.
 *
 * @param reservoir the crucible's own goo contents
 * @param pool      the melt pool's goo contents, added to the reservoir per type as a long
 * @param types     distinct goo types present across reservoir and pool
 * @param fuelRod   the fuel rod item in the crucible slot
 * @param hasGoo    whether any goo is present
 * @param hasFuel   whether a fuel rod is inserted
 */
public record CrucibleSnapshot(
        GooContents reservoir,
        GooContents pool,
        Set<ResourceKey<GooTypeDefinition>> types,
        ItemStack fuelRod,
        boolean hasGoo,
        boolean hasFuel
) {
}
