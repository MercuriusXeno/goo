package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.program.ConvokeStep;
import com.mercuriusxeno.goo.ability.program.Variables;
import com.mercuriusxeno.goo.block.ability.AbilityBlockEntity;

/**
 * The convoke blob's throb and echo: on each pulse the orb swells and
 * settles back, and an echo of it grows outward and fades over the period,
 * timed to the tries its convoke step makes.
 * Decision convoke-blob-throbs-until-a-mob-arrives.
 */
public final class ConvokeThrob {

    /** How far past resting size the orb swells on each pulse. */
    static final float SWELL = 0.35f;
    /** How many times its own size the echo has grown by the time it fades. */
    static final float ECHO_GROWTH = 3f;
    /** The power the swell settles back by, so it snaps up and eases down. */
    private static final int SETTLE_POWER = 3;

    private ConvokeThrob() {
    }

    /**
     * The ticks between a marker's convoke tries, read from its synced program.
     *
     * @param be the marker's block entity
     * @return the period, zero for a marker that convokes nothing
     */
    public static int periodOf(AbilityBlockEntity be) {
        return SyncedSteps.first(be, ConvokeStep.class)
                .map(step -> Math.max(1, step.every().evaluateInt(Variables.NONE))).orElse(0);
    }

    /**
     * How far through its pulse the blob stands.
     *
     * @param period   the ticks between pulses
     * @param gameTime the game time with the partial tick
     * @return 0 at the pulse, rising to 1 at the next
     */
    static float phase(int period, float gameTime) {
        return (gameTime % period) / period;
    }

    /**
     * The orb's scale through a pulse: swollen at the pulse, settling back to
     * resting size by the next.
     *
     * @param period   the ticks between pulses, zero for no throb
     * @param gameTime the game time with the partial tick
     * @return the scale factor, exactly 1 with no throb
     */
    public static float throb(int period, float gameTime) {
        return period <= 0 ? 1f : 1f + SWELL * (float) Math.pow(1f - phase(period, gameTime), SETTLE_POWER);
    }

    /**
     * How large the echo stands relative to the orb's shell.
     *
     * @param period   the ticks between pulses
     * @param gameTime the game time with the partial tick
     * @return the echo's size factor, 1 at the pulse
     */
    public static float echoGrowth(int period, float gameTime) {
        return 1f + (ECHO_GROWTH - 1f) * phase(period, gameTime);
    }

    /**
     * How strongly the echo draws.
     *
     * @param period   the ticks between pulses
     * @param gameTime the game time with the partial tick
     * @return 1 at the pulse, fading to 0 by the next
     */
    public static float echoStrength(int period, float gameTime) {
        return 1f - phase(period, gameTime);
    }
}
