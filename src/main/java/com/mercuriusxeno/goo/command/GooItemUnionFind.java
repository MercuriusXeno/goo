package com.mercuriusxeno.goo.command;

import net.minecraft.resources.Identifier;
import java.util.Map;

/**
 * Joins items into families by the recipes linking them: each family is one representative, the item every
 * member's parent chain ends at. The clique tables and the type provenance rule share it.
 */
final class GooItemUnionFind {

    private GooItemUnionFind() {
    }

    /**
     * @param parent each item's parent, filled as items are met
     * @param item   the item
     * @return the representative of the item's family
     */
    static Identifier find(Map<Identifier, Identifier> parent, Identifier item) {
        parent.putIfAbsent(item, item);
        Identifier above = parent.get(item);
        if (above.equals(item)) {
            return item;
        }
        Identifier root = find(parent, above);
        parent.put(item, root);
        return root;
    }

    /**
     * Joins two items' families.
     *
     * @param parent each item's parent
     * @param first  one item
     * @param second another
     */
    static void union(Map<Identifier, Identifier> parent, Identifier first, Identifier second) {
        Identifier firstRoot = find(parent, first);
        Identifier secondRoot = find(parent, second);
        if (!firstRoot.equals(secondRoot)) {
            parent.put(firstRoot, secondRoot);
        }
    }
}
