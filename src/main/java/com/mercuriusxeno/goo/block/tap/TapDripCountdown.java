package com.mercuriusxeno.goo.block.tap;

import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Ticks left until a tap's next drip, saved with the tap so a reload
 * neither skips nor doubles a drip (decision fixed-drip-interval).
 */
final class TapDripCountdown {

    /**
     * Save key for the ticks left.
     */
    static final String TAG_DRIP_COUNTDOWN = "DripCountdown";

    private final int interval;
    private int ticksLeft;

    /**
     * Starts a countdown at a full interval.
     *
     * @param interval ticks between drips
     */
    TapDripCountdown(int interval) {
        this.interval = interval;
        this.ticksLeft = interval;
    }

    /**
     * @return ticks left until the next drip
     */
    int ticksLeft() {
        return ticksLeft;
    }

    /**
     * Counts one tick down; on reaching zero, starts the next interval.
     *
     * @return true on the tick a drip falls due
     */
    boolean tick() {
        if (--ticksLeft > 0) {
            return false;
        }
        ticksLeft = interval;
        return true;
    }

    /**
     * Starts a full interval, as a newly inserted canister does.
     */
    void restart() {
        ticksLeft = interval;
    }

    /**
     * @param output the tap's save output
     */
    void save(ValueOutput output) {
        output.putInt(TAG_DRIP_COUNTDOWN, ticksLeft);
    }

    /**
     * Reads the ticks left, clamped to one interval; a save holding none
     * starts a full interval.
     *
     * @param input the tap's save input
     */
    void load(ValueInput input) {
        ticksLeft = Math.clamp(input.getIntOr(TAG_DRIP_COUNTDOWN, interval), 1, interval);
    }
}
