package com.mercuriusxeno.goo.ability.program;

/**
 * The pace every held sweep shares: a press starts a pulse at once and a
 * hold starts another every half second, even while earlier fronts still
 * travel; each front leaves the caster at one steady pace, 48 blocks a
 * second, so Scry's 96 blocks take about two seconds; and every wall a
 * front hits lights at the hit and fades over about two seconds behind it.
 * decision held-sweeps-share-one-fast-shell-visual
 *
 * @param range the radius each front reaches, in blocks
 */
public record HeldSweepPace(double range) {

    /** Blocks a front travels each second. */
    public static final double BLOCKS_A_SECOND = 48;
    /** Held ticks between one pulse and the next: half a second. */
    public static final int PULSE_EVERY_TICKS = 10;
    /** Ticks a wall a front hit stays lit, fading, behind the front: two seconds. */
    public static final int LINGER_TICKS = 40;
    private static final double TICKS_A_SECOND = 20;

    /**
     * The tick of its pulse a front lands on its range.
     *
     * @return the ticks from the pulse's first to its front reaching its range
     */
    public int ticksToRange() {
        return (int) Math.ceil(range * TICKS_A_SECOND / BLOCKS_A_SECOND);
    }

    /**
     * Whether a tick of the hold starts a pulse: the press itself, then
     * every tenth tick after it.
     *
     * @param held the hold's age, 1 on the press
     * @return true when the tick sends a new front
     */
    public boolean startsPulse(int held) {
        return held >= ChannelAim.FIRST_TICK && (held - ChannelAim.FIRST_TICK) % PULSE_EVERY_TICKS == 0;
    }

    /**
     * A front's radius on a tick of its pulse: the same blocks further each
     * tick, held at the range once reached.
     *
     * @param pulseAge the pulse's tick, 1 on its first and 0 before it
     * @return the radius in blocks
     */
    public double radiusAt(int pulseAge) {
        return Math.clamp(pulseAge * BLOCKS_A_SECOND / TICKS_A_SECOND, 0, range);
    }
}
