package com.mercuriusxeno.goo.client;

import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.resources.ResourceKey;
import java.util.Comparator;
import java.util.Map;

/**
 * The order the item tooltip's goo rows and the plexer HUD's cost rows read in: the highest amount first, ties by
 * type id ascending, the shape of {@link TypeBands#largestFirst}'s order. A crucible's and a canister's HUD
 * holdings keep their own order.
 * Decision value-lists-order-highest-first-ties-by-type-key.
 */
public final class GooAmountOrder {

    private GooAmountOrder() {
    }

    /**
     * @param <A> the amount's type, a whole volume or an exact fraction
     * @return highest amount first, ties by type id ascending
     */
    public static <A extends Comparable<? super A>> Comparator<Map.Entry<ResourceKey<GooTypeDefinition>, A>>
            highestFirst() {
        return Comparator.<Map.Entry<ResourceKey<GooTypeDefinition>, A>, A>comparing(Map.Entry::getValue,
                Comparator.reverseOrder()).thenComparing(entry -> GooTypes.id(entry.getKey()));
    }
}
