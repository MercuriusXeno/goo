package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * The goo types something melts into and each one's share, mingled: a patch
 * of its melt is one type, picked by the patch's own random share weighted by
 * how much of each type it holds, so the types mix as they do in the crucible.
 * decision unmake-waves-dissolve-by-crucible-cost
 *
 * @param types      the types, largest first
 * @param cumulative each type's share added to those before it, ending at 1
 */
public record MingledGoo(List<ResourceKey<GooTypeDefinition>> types, List<Float> cumulative) {

    /** No goo at all. */
    public static final MingledGoo NONE = new MingledGoo(List.of(), List.of());

    /**
     * The mingled goo of an amount of each type.
     *
     * @param amounts each type's amount
     * @return the mingled goo
     */
    public static MingledGoo of(Map<ResourceKey<GooTypeDefinition>, Integer> amounts) {
        long total = amounts.values().stream().mapToLong(Integer::longValue).filter(amount -> amount > 0).sum();
        if (total <= 0) {
            return NONE;
        }
        List<Map.Entry<ResourceKey<GooTypeDefinition>, Integer>> largestFirst = amounts.entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .sorted(Map.Entry.<ResourceKey<GooTypeDefinition>, Integer>comparingByValue(Comparator.reverseOrder()))
                .toList();
        List<ResourceKey<GooTypeDefinition>> types = new ArrayList<>();
        List<Float> cumulative = new ArrayList<>();
        long running = 0;
        for (Map.Entry<ResourceKey<GooTypeDefinition>, Integer> entry : largestFirst) {
            running += entry.getValue();
            types.add(entry.getKey());
            cumulative.add((float) running / total);
        }
        return new MingledGoo(types, cumulative);
    }

    /**
     * The type a patch is, picked by its own random share.
     *
     * @param share the patch's random share, 0 to 1
     * @return the type, or null for no goo at all
     */
    public @Nullable ResourceKey<GooTypeDefinition> pick(double share) {
        for (int index = 0; index < types.size(); index++) {
            if (share < cumulative.get(index)) {
                return types.get(index);
            }
        }
        return types.isEmpty() ? null : types.getLast();
    }

    /**
     * @param index a type's index, largest first
     * @return that type's share of the whole, 0 to 1
     */
    public float share(int index) {
        return cumulative.get(index) - (index == 0 ? 0f : cumulative.get(index - 1));
    }

    /**
     * @return the type it holds most of, or null for no goo at all
     */
    public @Nullable ResourceKey<GooTypeDefinition> largest() {
        return types.isEmpty() ? null : types.getFirst();
    }
}
