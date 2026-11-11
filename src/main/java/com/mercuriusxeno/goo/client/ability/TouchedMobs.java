package com.mercuriusxeno.goo.client.ability;

import java.util.HashMap;
import java.util.Map;

/**
 * The mobs Heal or Drain touched lately, each until the tick its life mark
 * lapses: a payload naming a mob touches it, and a later payload renews it.
 * The mark holds whole while touched and fades over its last ticks.
 * touched-mobs-show-hearts-or-a-count-over-max
 */
public final class TouchedMobs {

    /** The client's touched mobs, fed by the Heal and Drain payloads and read by the head marks. */
    public static final TouchedMobs CLIENT = new TouchedMobs();
    /** Ticks a mob wears its life mark after the last touch, longer than Drain's gap between trickles. */
    static final int MARK_TICKS = 40;
    /** The last ticks before the lapse, over which the mark fades out. */
    static final int FADE_TICKS = 15;

    private final Map<Integer, Long> lapseTicks = new HashMap<>();

    /**
     * Marks a mob as touched now, and forgets every mark already lapsed.
     *
     * @param entityId the mob's entity id
     * @param tick     the game tick it was touched at
     */
    public void touch(int entityId, long tick) {
        lapseTicks.values().removeIf(lapse -> lapse <= tick);
        lapseTicks.put(entityId, tick + MARK_TICKS);
    }

    /**
     * Whether a mob wears its life mark at a tick.
     *
     * @param entityId the mob's entity id
     * @param tick     the game tick asked about
     * @return true before its mark lapses
     */
    public boolean marked(int entityId, long tick) {
        return lapseTicks.getOrDefault(entityId, Long.MIN_VALUE) > tick;
    }

    /**
     * How strongly a mob's life mark draws: whole until its fade starts,
     * then falling to nothing at the lapse.
     *
     * @param entityId the mob's entity id
     * @param time     the game time including the partial tick
     * @return the opacity, zero to one; zero for a mob unmarked
     */
    public float opacity(int entityId, float time) {
        Long lapse = lapseTicks.get(entityId);
        return lapse == null ? 0f : Math.clamp((lapse - time) / FADE_TICKS, 0f, 1f);
    }
}
