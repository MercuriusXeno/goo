package com.mercuriusxeno.goo.ability.program;

/**
 * A host over a player Lunar can fix on the moon or the blue overworld star.
 * decision gate-fixates-your-view-on-the-moon-until-it-takes-you
 */
public interface AstralLunarHost extends StepHost {

    /**
     * One held tick of Lunar: the press's first tick fixes the player's gaze on the moon or the star their look
     * holds, for the fixation's ticks, and each tick after keeps the pull standing; Lunar's cost is paid only as
     * the moon or the star takes the player. A look holding neither is refused with a tell.
     * decision holding-right-click-pulls-the-moon-and-early-release-cancels
     *
     * @param fixation the ticks the fixation runs
     * @param cost     the astral Lunar costs at the carry (mB)
     */
    void fixateOnTheSky(int fixation, int cost);
}
