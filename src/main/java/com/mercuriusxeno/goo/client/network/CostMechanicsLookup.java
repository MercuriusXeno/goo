package com.mercuriusxeno.goo.client.network;

import net.minecraft.resources.Identifier;
import java.util.function.Function;
import java.util.function.ToIntFunction;

/**
 * What a cost-mechanics format string fills from on the client: the lang
 * resolving its key and the goo value of an item in the ability's own type.
 * decision zero-cost-field-formats-the-cost-mechanics
 *
 * @param lang  resolves a translation key to its text
 * @param value the goo value of an item in the ability's type, in mB
 */
public record CostMechanicsLookup(Function<String, String> lang, ToIntFunction<Identifier> value) {

    /**
     * The text a translation key resolves to.
     *
     * @param key the translation key
     * @return its text
     */
    public String translate(String key) {
        return lang.apply(key);
    }

    /**
     * The goo value of an item in the ability's type.
     *
     * @param item the item id
     * @return the value in mB
     */
    public int valueOf(Identifier item) {
        return value.applyAsInt(item);
    }
}
