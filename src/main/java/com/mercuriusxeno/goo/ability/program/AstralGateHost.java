package com.mercuriusxeno.goo.ability.program;

/**
 * A host over a player Astral's gate can fix on the moon or the blue overworld star.
 * decision gate-fixates-your-view-on-the-moon-until-it-takes-you
 */
public interface AstralGateHost extends StepHost {

    /**
     * Fixes the player's gaze on the moon or the star their look holds, for the fixation's ticks, paying the
     * gate's cost as it begins; a look holding neither is refused with a tell and pays nothing.
     *
     * @param fixation the ticks the fixation runs
     * @param cost     the astral the gate costs (mB)
     */
    void fixateOnTheSky(int fixation, int cost);
}
