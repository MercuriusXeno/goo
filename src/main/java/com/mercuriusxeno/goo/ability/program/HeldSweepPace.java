package com.mercuriusxeno.goo.ability.program;

/**
 * A held sweep's pace: a press starts a pulse at once and a hold starts
 * another every second, even while earlier fronts still travel; each front
 * leaves the caster at its step's own steady pace; and every wall a front
 * hits lights at the hit, holds full for three seconds and fades slowly
 * over three more behind it (operator rulings 2026-11-06).
 * decision held-sweeps-share-one-fast-shell-visual
 * decision scry-ring-crosses-its-reach-in-two-seconds
 *
 * @param range         the radius each front reaches, in blocks
 * @param blocksASecond the blocks a front travels each second
 */
public record HeldSweepPace(double range, double blocksASecond) {

    /** Held ticks between one pulse and the next: a second. */
    public static final int PULSE_EVERY_TICKS = 20;
    /** Ticks a wall a front hit stays lit behind the front: six seconds. */
    public static final int LINGER_TICKS = 120;
    /** Ticks of the linger a hit wall holds full brightness before it fades: three seconds. */
    public static final int FULL_TICKS = 60;
    /** Game ticks in a second. */
    public static final double TICKS_A_SECOND = 20;

    /**
     * A pace whose front crosses its range in a set time.
     *
     * @param range         the radius each front reaches, in blocks
     * @param crossingTicks the ticks the front takes to reach it
     * @return the pace
     */
    public static HeldSweepPace crossing(double range, int crossingTicks) {
        return new HeldSweepPace(range, range * TICKS_A_SECOND / crossingTicks);
    }

    /**
     * The blocks a front travels each tick, which the caster's client
     * carries a front on by between the radii the server sends.
     *
     * @return the blocks a tick
     */
    public double blocksATick() {
        return blocksASecond / TICKS_A_SECOND;
    }

    /**
     * The tick of its pulse a front lands on its range.
     *
     * @return the ticks from the pulse's first to its front reaching its range
     */
    public int ticksToRange() {
        return (int) Math.ceil(range * TICKS_A_SECOND / blocksASecond);
    }

    /**
     * Whether a tick of the hold starts a pulse: the press itself, then
     * every twentieth tick after it.
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
        return Math.clamp(pulseAge * blocksASecond / TICKS_A_SECOND, 0, range);
    }
}
