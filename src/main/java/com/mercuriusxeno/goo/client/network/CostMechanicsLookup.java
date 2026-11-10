package com.mercuriusxeno.goo.client.network;

import com.mercuriusxeno.goo.data.IGooValueLookup;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * What a cost-mechanics format string fills from on the client: the lang
 * resolving its key and the goo values the ability's prices read.
 * decision zero-cost-field-formats-the-cost-mechanics
 *
 * @param lang   resolves a translation key to its text
 * @param values the goo values an ability's price reads its items and prefabs from
 */
public record CostMechanicsLookup(Function<String, String> lang, Supplier<? extends IGooValueLookup> values) {

    /**
     * The text a translation key resolves to.
     *
     * @param key the translation key
     * @return its text
     */
    public String translate(String key) {
        return lang.apply(key);
    }
}
