package com.mercuriusxeno.goo.client;

import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.GooTypes;
import com.mercuriusxeno.goo.item.GooContents;
import net.minecraft.resources.ResourceKey;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Builds the type bands a mingled fluid surface draws (decision
 * noise-mingled-type-textures): the bands partition [0, 1], one per goo
 * type, largest volume first, each as wide as its type's volume ratio and
 * numbered by the layer it draws on.
 */
public final class TypeBands {

    /** Largest volume first, ties broken in the order the dominant type uses. */
    private static final Comparator<Map.Entry<ResourceKey<GooTypeDefinition>, Integer>> LARGEST_FIRST =
        Map.Entry.<ResourceKey<GooTypeDefinition>, Integer>comparingByValue().reversed()
            .thenComparing(Map.Entry.comparingByKey(GooTypes.ORDER));

    private TypeBands() {
    }

    /**
     * Answers one band per goo type the contents hold.
     *
     * @param contents the goo the surface shows
     * @return the bands, largest type first; empty for empty contents
     */
    public static List<TypeBand> over(GooContents contents) {
        long total = contents.totalVolume();
        List<TypeBand> bands = new ArrayList<>();
        if (total <= 0) {
            return bands;
        }
        List<Map.Entry<ResourceKey<GooTypeDefinition>, Integer>> ordered =
            new ArrayList<>(contents.getAll().entrySet());
        ordered.sort(LARGEST_FIRST);
        long below = 0;
        float lo = 0f;
        for (Map.Entry<ResourceKey<GooTypeDefinition>, Integer> entry : ordered) {
            below += entry.getValue();
            float hi = (float) ((double) below / total);
            bands.add(new TypeBand(entry.getKey(), lo, hi, bands.size()));
            lo = hi;
        }
        return bands;
    }
}
