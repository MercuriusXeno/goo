package com.mercuriusxeno.goo.client.ber;

import net.minecraft.core.BlockPos;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * When each prism last began giving power, as the renderer reads it frame by
 * frame, so a metronome strobes on each beat though the beat's power stands
 * for a single tick.
 * metronome-prism-pulses-at-the-learned-rate
 */
public final class PrismBeats {

    private static final double NANOS_PER_SECOND = 1_000_000_000.0;
    private static final Map<BlockPos, Seen> SEEN = new ConcurrentHashMap<>();

    private PrismBeats() {
    }

    /**
     * What the renderer last saw of one prism.
     *
     * @param powered whether it gave power
     * @param beatAt  the real-time clock at its last beat, in seconds, NaN before any
     */
    record Seen(boolean powered, double beatAt) {

        /** A prism seen for the first time. */
        static final Seen NEVER = new Seen(false, Double.NaN);

        /**
         * What the renderer sees after this frame: power starting is a beat.
         *
         * @param poweredNow whether the prism gives power this frame
         * @param now        the real-time clock, in seconds
         * @return the prism as seen after the frame
         */
        Seen next(boolean poweredNow, double now) {
            return new Seen(poweredNow, poweredNow && !powered ? now : beatAt);
        }

        /**
         * Seconds since the last beat.
         *
         * @param now the real-time clock, in seconds
         * @return the seconds, or {@link Double#MAX_VALUE} before any beat
         */
        double since(double now) {
            return Double.isNaN(beatAt) ? Double.MAX_VALUE : now - beatAt;
        }
    }

    /**
     * Notes the prism's power this frame and answers the seconds since its last beat.
     *
     * @param pos     the prism's position
     * @param powered whether the prism gives power now
     * @return the seconds since it last began giving power, {@link Double#MAX_VALUE} before any
     */
    public static double secondsSinceBeat(BlockPos pos, boolean powered) {
        double now = System.nanoTime() / NANOS_PER_SECOND;
        return SEEN.compute(pos.immutable(), (key, seen) -> (seen == null ? Seen.NEVER : seen).next(powered, now))
                .since(now);
    }

    /** Forgets every prism, as a disconnect does. */
    public static void clear() {
        SEEN.clear();
    }
}
