package com.mercuriusxeno.goo.ability.pulse;

/**
 * The beat a metronome prism learns: the game times of the last two redstone
 * signals it started receiving, and whether a signal holds now. The gap
 * between the two is the interval it pulses at, counted from the last; each
 * new signal moves the pair on, so two new signals set another interval.
 * metronome-prism-pulses-at-the-learned-rate
 *
 * @param previousEdge the game time of the signal before the last, or {@link #NEVER}
 * @param lastEdge     the game time of the last signal, or {@link #NEVER}
 * @param heard        whether a signal holds on the prism now
 */
public record RedstoneBeat(long previousEdge, long lastEdge, boolean heard) {

    /** A signal time not yet heard. */
    public static final long NEVER = Long.MIN_VALUE;
    /** A prism that has heard nothing. */
    public static final RedstoneBeat SILENT = new RedstoneBeat(NEVER, NEVER, false);

    /**
     * The beat after the prism reads its input: a signal starting now moves
     * the pair on; a signal holding or ending moves nothing.
     *
     * @param powered whether a signal reaches the prism now
     * @param now     the game time
     * @return the beat after
     */
    public RedstoneBeat hear(boolean powered, long now) {
        if (powered && !heard) {
            return new RedstoneBeat(lastEdge, now, true);
        }
        return powered == heard ? this : new RedstoneBeat(previousEdge, lastEdge, false);
    }

    /**
     * Whether the beat is learned: two signals heard, the later after the earlier.
     *
     * @return true once two signals set an interval
     */
    public boolean learned() {
        return previousEdge != NEVER && lastEdge > previousEdge;
    }

    /**
     * The learned interval, the gap between the last two signals.
     *
     * @return the ticks between them, 0 while the beat is unlearned
     */
    public long interval() {
        return learned() ? lastEdge - previousEdge : 0;
    }

    /**
     * Whether the prism pulses on a tick: every interval after the last
     * signal, once the beat is learned.
     *
     * @param now the game time
     * @return true on a pulsing tick
     */
    public boolean pulsesAt(long now) {
        long since = now - lastEdge;
        return learned() && since > 0 && since % interval() == 0;
    }
}
