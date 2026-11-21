package com.mercuriusxeno.goo.client;

import org.jspecify.annotations.Nullable;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Function;

/**
 * Prepares each key the first time outside any open render pass. A render type's first prepare loads and uploads
 * its textures, which Minecraft 26.3 refuses while a pass stands, and goo's stage draws run inside the stage's open
 * pass, so a key first met inside one waits, unprepared and undrawn, for the next frame's start.
 * decision minecraft-26-3-update-is-a-port-to-parity
 *
 * @param <K> the key prepared, a render type
 * @param <P> what preparing it answers
 */
public final class FirstPreparedOutsideAPass<K, P> {

    private final Function<K, P> prepare;
    private final Set<K> prepared = new HashSet<>();
    private final Set<K> waiting = new LinkedHashSet<>();

    /**
     * @param prepare prepares one key
     */
    public FirstPreparedOutsideAPass(Function<K, P> prepare) {
        this.prepare = prepare;
    }

    /**
     * @param key      the key a draw prepares
     * @param passOpen whether a render pass stands open where the draw runs
     * @return the prepared key, or null where its first prepare waits for the next frame's start
     */
    public @Nullable P prepared(K key, boolean passOpen) {
        if (passOpen && !prepared.contains(key)) {
            waiting.add(key);
            return null;
        }
        prepared.add(key);
        return prepare.apply(key);
    }

    /**
     * Prepares each key a draw met inside an open pass, at a frame's start, where no pass stands.
     */
    public void prepareWaiting() {
        for (K key : waiting) {
            prepare.apply(key);
            prepared.add(key);
        }
        waiting.clear();
    }
}
