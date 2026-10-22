package com.mercuriusxeno.goo.client.network;

import com.mercuriusxeno.goo.data.GooFraction;
import net.minecraft.resources.Identifier;
import java.util.function.Function;

/**
 * What a cost-mechanics format string fills from on the client: the lang
 * resolving its key and the goo value of an item in the ability's own type.
 * decision zero-cost-field-formats-the-cost-mechanics
 *
 * @param lang  resolves a translation key to its text
 * @param value the exact goo value of an item in the ability's type
 */
public record CostMechanicsLookup(Function<String, String> lang, Function<Identifier, GooFraction> value) {

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
     * The exact goo value of an item in the ability's type.
     * decision tooltip-and-plexer-hud-show-a-mixed-number
     *
     * @param item the item id
     * @return the exact value
     */
    public GooFraction valueOf(Identifier item) {
        return value.apply(item);
    }
}
