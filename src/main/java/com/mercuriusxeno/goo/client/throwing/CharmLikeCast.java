package com.mercuriusxeno.goo.client.throwing;

import java.util.OptionalInt;
import java.util.function.IntPredicate;
import java.util.function.Supplier;

/**
 * The charm-like cast's press: right click with a mob highlighted takes that
 * mob, and the cast keeps it whatever the aim does after, the player free to
 * look away, lose the highlight or lose sight of it, until the release sends
 * it, however briefly right click was held. A press with no mob highlighted
 * arms nothing, and a taken mob that dies or unloads cancels the cast as a
 * left click does.
 * decision charm-like-abilities-lock-the-highlighted-mob-on-right-click
 * operator: "It's just a more deliberate, more magical way of selecting a target."
 */
public final class CharmLikeCast {

    private static final int NO_MOB = -1;

    private final Supplier<OptionalInt> highlighted;
    private final IntPredicate stands;
    private int taken = NO_MOB;

    /**
     * @param highlighted answers the id of the mob the frame highlights, empty where it highlights none
     * @param stands      answers whether the mob of an id still stands, alive and loaded
     */
    public CharmLikeCast(Supplier<OptionalInt> highlighted, IntPredicate stands) {
        this.highlighted = highlighted;
        this.stands = stands;
    }

    /**
     * Takes the mob highlighted at the right click and arms the press on it;
     * with no mob highlighted the press arms nothing. A press already live
     * keeps the mob it took.
     *
     * @param press the glove's press
     * @return true where the press stands on a taken mob
     */
    public boolean press(GloveInputGate press) {
        if (press.isArmed()) {
            return taken != NO_MOB;
        }
        taken = highlighted.get().orElse(NO_MOB);
        if (taken != NO_MOB) {
            press.arm();
        }
        return taken != NO_MOB;
    }

    /**
     * Cancels a live press whose taken mob no longer stands, sending nothing.
     *
     * @param press   the glove's press
     * @param actions the arm pose the press resolves to
     */
    public void tick(GloveInputGate press, GloveInputGate.PressActions actions) {
        if (press.isArmed() && !stands.test(taken)) {
            press.cancelCharge(actions);
            taken = NO_MOB;
        }
    }

    /**
     * The mob a release sends: the taken mob, while it still stands. The
     * release ends the cast either way.
     *
     * @return the taken mob's id, empty where the release sends nothing
     */
    public OptionalInt release() {
        int mob = taken;
        taken = NO_MOB;
        return mob != NO_MOB && stands.test(mob) ? OptionalInt.of(mob) : OptionalInt.empty();
    }
}
