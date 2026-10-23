package com.mercuriusxeno.goo.workbench.checks;

import com.mercuriusxeno.goo.data.GooFraction;
import com.mercuriusxeno.goo.data.GooValue;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import java.util.Map;

/**
 * The draft's values as the game's loader reads them, base values with the post-conversions applied and no
 * recipe derived, which every check reads.
 *
 * @param values each item's value
 */
public record DraftValues(Map<Identifier, GooValue> values) {

    /**
     * @param item an item id
     * @param type a goo type
     * @return the item's amount of the type, zero where the draft values the item not at all
     */
    public GooFraction amount(String item, ResourceKey<GooTypeDefinition> type) {
        GooValue value = values.get(Identifier.parse(item));
        return value == null ? GooFraction.of(0) : value.exact(type);
    }

    /**
     * @param item an item id
     * @return whether the draft values the item at all
     */
    public boolean valued(String item) {
        return values.containsKey(Identifier.parse(item));
    }
}
