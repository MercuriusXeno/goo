package com.mercuriusxeno.goo.client.ability;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * How far each of a set of gates has grown open since the client first saw
 * it: from nothing as it is placed to its full size a moment later, swelling
 * a little past full before it settles, the whole of its opening, with no
 * particles. A gate already standing as the level is joined shows full size.
 * hole-closes-the-space-to-an-opening-ahead
 * portals-link-by-looking-through-portal-vision
 *
 * @param <K> what each gate is known by
 */
public final class GrowOpen<K> {

    /** Ticks a gate takes to grow open, the span a portable hole's mouth opens over at least. */
    public static final float OPENING_TICKS = 12F;
    /** How far past full size a gate swells as it opens. */
    private static final float OVERSHOOT = 1.7F;

    /** The game time each standing gate was first seen, which it grows open from. */
    private final Map<K, Long> opened = new HashMap<>();

    /**
     * Takes the gates standing now.
     *
     * @param standing the gates the server last told of
     * @param gameTime the client's game time
     * @param joined   whether the level was joined since the last sight, its gates already standing
     */
    void seen(Collection<K> standing, long gameTime, boolean joined) {
        opened.keySet().retainAll(standing);
        long since = joined ? gameTime - (long) Math.ceil(OPENING_TICKS) : gameTime;
        standing.forEach(gate -> opened.putIfAbsent(gate, since));
    }

    /**
     * How far a gate has grown open.
     *
     * @param gate        the gate
     * @param gameTime    the client's game time
     * @param partialTick the frame's partial tick
     * @return its share of its full size, one once open
     */
    float grown(K gate, long gameTime, float partialTick) {
        Long since = opened.get(gate);
        if (since == null) {
            return 1F;
        }
        float age = (gameTime - since + partialTick) / OPENING_TICKS;
        return age >= 1F ? 1F : easeOutBack(Math.max(0F, age));
    }

    private static float easeOutBack(float t) {
        float shifted = t - 1F;
        return 1F + (OVERSHOOT + 1F) * shifted * shifted * shifted + OVERSHOOT * shifted * shifted;
    }
}
