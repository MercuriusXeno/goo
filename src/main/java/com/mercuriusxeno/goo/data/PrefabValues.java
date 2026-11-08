package com.mercuriusxeno.goo.data;

import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.Nullable;
import java.util.Map;

/**
 * Each prefab's resolved value, as the values the server last loaded hold it: a scalar prefab's one number and a
 * per-type prefab's amount of each goo type, which an ability's cost reads a prefab through
 * (decision ability-cost-builds-as-a-one-type-by-type-value).
 *
 * @param scalars each scalar prefab's number, by name without its $
 * @param trees   each per-type prefab's value, by name without its $
 */
public record PrefabValues(Map<String, GooFraction> scalars, Map<String, GooValue> trees) {

    /** No prefab at all. */
    public static final PrefabValues EMPTY = new PrefabValues(Map.of(), Map.of());

    /**
     * Copies the maps, so no later load reaches them.
     */
    public PrefabValues {
        scalars = Map.copyOf(scalars);
        trees = Map.copyOf(trees);
    }

    /**
     * @param name a prefab's name, without its $
     * @param type the one goo type a cost reads
     * @return a scalar prefab's number, a per-type prefab's amount of the type, or null for a name no prefab holds
     */
    public @Nullable GooFraction in(String name, ResourceKey<GooTypeDefinition> type) {
        GooFraction scalar = scalars.get(name);
        if (scalar != null) {
            return scalar;
        }
        GooValue tree = trees.get(name);
        return tree == null ? null : tree.exact(type);
    }
}
