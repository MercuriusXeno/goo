package com.mercuriusxeno.goo.client.fade;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A set of traces, one per key, each lingering after its state is gone as an
 * {@link Afterglow} does, in the order each key was first seen standing so a
 * fading trace keeps its place beside the standing ones.
 * brew-effect-visuals-linger-and-fade-over-a-second-after-the-effect-ends
 *
 * @param <K> what tells one trace from another
 * @param <S> the state a trace draws from
 */
public final class Afterglows<K, S> {

    private final Map<K, Afterglow<S>> traces = new LinkedHashMap<>();

    /**
     * Reads every trace's state as the client sees it this frame.
     *
     * @param standing the states standing now, by key
     * @param now      the game time, partial tick included
     * @return what each trace draws, in the order its key was first seen
     */
    public List<Afterglow.Glow<S>> read(Map<K, S> standing, float now) {
        standing.keySet().forEach(key -> traces.computeIfAbsent(key, unseen -> new Afterglow<>()));
        List<Afterglow.Glow<S>> glows = new ArrayList<>();
        Iterator<Map.Entry<K, Afterglow<S>>> entries = traces.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<K, Afterglow<S>> entry = entries.next();
            entry.getValue().read(standing.get(entry.getKey()), now).ifPresentOrElse(glows::add, entries::remove);
        }
        return glows;
    }
}
