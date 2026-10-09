package com.mercuriusxeno.goo.client.ability;

import org.jspecify.annotations.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * The last value seen for each key, answered after the value itself is gone,
 * and kept until the key is no longer among those to keep: how a drink's
 * stream keeps drawing the block it was after the block is air.
 * decision unmake-waves-dissolve-by-crucible-cost
 *
 * @param <K> the key
 * @param <V> the value remembered
 */
final class Remembered<K, V> {

    private final Map<K, V> seen = new HashMap<>();

    /**
     * The value remembered for a key, remembering the one seen now first.
     *
     * @param key the key
     * @param now the value seen now, null while none is
     * @return the value remembered, or null while none was ever seen
     */
    @Nullable V of(K key, @Nullable V now) {
        if (now != null) {
            seen.put(key, now);
        }
        return seen.get(key);
    }

    /**
     * Forgets every key but these.
     *
     * @param keys the keys to keep
     */
    void keepOnly(Set<K> keys) {
        seen.keySet().retainAll(keys);
    }
}
