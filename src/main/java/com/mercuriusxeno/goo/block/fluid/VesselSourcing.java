package com.mercuriusxeno.goo.block.fluid;

import com.mercuriusxeno.goo.block.IGooReceptacle;
import com.mercuriusxeno.goo.item.GooContents;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.IntUnaryOperator;
import java.util.function.ToIntFunction;

/**
 * Pours, once a tick, more of the goo a vessel holds beside its source goo:
 * a type whose JSON names a vessel_source_per_mb rate adds that rate times
 * its held mB of one other held type, picked at random. Infinity is the
 * type the mod ships with a rate.
 * decision infinity-sources-whatever-shares-its-vessel
 */
public final class VesselSourcing {

    private VesselSourcing() {
    }

    /**
     * One tick's pour.
     *
     * @param type   the held type that grows
     * @param amount the mB offered to the vessel, which keeps what fits
     */
    public record Pour(ResourceKey<GooTypeDefinition> type, int amount) {
    }

    /**
     * Runs one tick's pour into a vessel, which keeps only what its room allows.
     *
     * @param level    the vessel's level, for the type registry and the random pick
     * @param contents what the vessel holds
     * @param vessel   where the pour goes
     */
    public static void sourceTick(Level level, GooContents contents, IGooReceptacle vessel) {
        Pour pour = pickPour(contents, type -> sourcePerMb(level.registryAccess(), type), level.getRandom()::nextInt);
        if (pour != null) {
            vessel.insertGoo(pour.type(), pour.amount());
        }
    }

    /**
     * Picks this tick's pour: the source types' rates times their mB, into one
     * held non-source type chosen at random.
     *
     * @param contents    what the vessel holds
     * @param sourcePerMb each type's rate, 0 for a type that sources nothing
     * @param randomBelow answers an index below its bound
     * @return the pour, or null when the vessel holds no source or nothing else to grow
     */
    static @Nullable Pour pickPour(GooContents contents,
                                   ToIntFunction<ResourceKey<GooTypeDefinition>> sourcePerMb,
                                   IntUnaryOperator randomBelow) {
        long amount = 0;
        List<ResourceKey<GooTypeDefinition>> grown = new ArrayList<>();
        for (Map.Entry<ResourceKey<GooTypeDefinition>, Integer> held : contents.getAll().entrySet()) {
            int rate = sourcePerMb.applyAsInt(held.getKey());
            if (rate > 0) {
                amount += (long) rate * held.getValue();
            } else {
                grown.add(held.getKey());
            }
        }
        if (amount <= 0 || grown.isEmpty()) {
            return null;
        }
        grown.sort(GooTypes.ORDER);
        return new Pour(grown.get(randomBelow.applyAsInt(grown.size())), (int) Math.min(amount, Integer.MAX_VALUE));
    }

    /**
     * @param registries the registry access of the level in hand
     * @param type       a goo type
     * @return the type's vessel_source_per_mb rate, 0 for a type the registry lacks
     */
    private static int sourcePerMb(HolderLookup.Provider registries, ResourceKey<GooTypeDefinition> type) {
        return GooTypes.exists(registries, type) ? GooTypes.definition(registries, type).vesselSourcePerMb() : 0;
    }
}
