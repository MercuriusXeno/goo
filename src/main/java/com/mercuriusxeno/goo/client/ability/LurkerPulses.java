package com.mercuriusxeno.goo.client.ability;

import net.minecraft.core.BlockPos;
import java.util.HashMap;
import java.util.Map;

/**
 * The lurker pulses this client last heard, one per watching marker: how
 * near its enemy stands and the beat its orb keeps, the beat quickening and
 * the glow brightening as the enemy closes. A marker heard from no longer
 * than {@link #STALE_TICKS} ago rests.
 * decision lurker-blob-brightens-then-detonates
 */
public final class LurkerPulses {

    /** The store the client's pulse handler and the orb renderer share. */
    public static final LurkerPulses CLIENT = new LurkerPulses();

    /** Ticks after the last pulse before a marker's orb rests, a quarter second. */
    static final long STALE_TICKS = 5;
    /** Ticks per beat with the enemy at the watch's edge, two seconds. */
    static final float SLOWEST_BEAT_TICKS = 40f;
    /** Ticks per beat with the enemy on the marker, a fifth of a second. */
    static final float FASTEST_BEAT_TICKS = 4f;
    /** The share of the glow the beat swings; the rest holds steady at the closeness. */
    static final float BEAT_SWING = 0.4f;
    private static final double TWO_PI = 2 * Math.PI;
    /** Centers the sine's swing on [0, 1]. */
    private static final double HALF = 0.5;

    /**
     * One marker's last pulse.
     *
     * @param closeness how near the enemy stood, 0 at the watch's edge to 1 on the marker
     * @param tick      the game time the pulse arrived
     * @param phase     the beats elapsed at that tick
     */
    record Pulse(float closeness, long tick, double phase) {

        /**
         * @param gameTime the game time including the partial tick
         * @return the beats elapsed by then, at this pulse's tempo
         */
        double phaseAt(double gameTime) {
            return phase + (gameTime - tick) / beatTicks(closeness);
        }
    }

    private final Map<BlockPos, Pulse> pulses = new HashMap<>();

    /**
     * Records a pulse, carrying the beat on from the marker's last fresh
     * pulse so a quickening tempo never skips.
     *
     * @param pos       the marker's position
     * @param closeness how near the enemy stands, 0 to 1
     * @param now       the game time the pulse arrived
     */
    public void record(BlockPos pos, float closeness, long now) {
        Pulse prior = pulses.get(pos);
        double phase = prior != null && !isStale(prior, now) ? prior.phaseAt(now) : 0d;
        pulses.put(pos.immutable(), new Pulse(closeness, now, phase));
    }

    /**
     * How brightly the marker's orb glows now: 0 at rest, rising with the
     * enemy's closeness and swinging on the beat.
     *
     * @param pos      the marker's position
     * @param gameTime the game time including the partial tick
     * @return the glow in [0, 1]
     */
    public float glowAt(BlockPos pos, float gameTime) {
        Pulse pulse = pulses.get(pos);
        if (pulse == null) {
            return 0f;
        }
        if (isStale(pulse, (long) gameTime)) {
            pulses.remove(pos);
            return 0f;
        }
        return glow(pulse.closeness(), pulse.phaseAt(gameTime));
    }

    /**
     * Ticks per beat at a closeness, shortening from the slowest beat at the
     * watch's edge to the fastest on the marker.
     *
     * @param closeness how near the enemy stands, 0 to 1
     * @return the ticks one beat lasts
     */
    static float beatTicks(float closeness) {
        return SLOWEST_BEAT_TICKS + (FASTEST_BEAT_TICKS - SLOWEST_BEAT_TICKS) * closeness;
    }

    /**
     * The glow at a closeness and a point in the beat.
     *
     * @param closeness how near the enemy stands, 0 to 1
     * @param phase     the beats elapsed
     * @return the glow in [0, 1]
     */
    static float glow(float closeness, double phase) {
        float beat = (float) (HALF + HALF * Math.sin(TWO_PI * phase));
        return closeness * (1f - BEAT_SWING + BEAT_SWING * beat);
    }

    /**
     * @param pulse the pulse
     * @param now   the game time
     * @return true once the marker has gone quiet long enough to rest
     */
    private static boolean isStale(Pulse pulse, long now) {
        return now - pulse.tick() > STALE_TICKS;
    }
}
