package com.mercuriusxeno.goo.client.throwing;

import com.mercuriusxeno.goo.item.GooGloveItem;

/**
 * The glove's right-click input as a press the client counts off the held
 * use key, with no using state: a release under
 * {@link GooGloveItem#RADIAL_THRESHOLD_TICKS} throws, a hold to it opens the
 * radial, and the arm swings only for a throw that sent a payload
 * (decision use-animation-only-when-goo-throws).
 */
public final class GloveInputGate {

    /** What the gate does for a press: the client's throw, swing and radial. */
    public interface PressActions {

        /**
         * Sends a throw payload for the glove's selection.
         *
         * @return true when a payload was sent
         */
        boolean sendThrow();

        /** Swings the arm holding the glove. */
        void swing();

        /** Opens the radial to change the glove's selection. */
        void openRadial();
    }

    private boolean armed;
    private int heldTicks;

    /** Starts a press when the glove's use reaches the client; a live press ignores the repeat. */
    public void arm() {
        if (!armed) {
            armed = true;
            heldTicks = 0;
        }
    }

    /**
     * Whether a press is live.
     *
     * @return true between the glove's use and the press's throw, radial or cancel
     */
    public boolean isArmed() {
        return armed;
    }

    /** Drops the live press with no throw and no radial. */
    public void cancel() {
        armed = false;
        heldTicks = 0;
    }

    /**
     * Advances a live press by one client tick.
     *
     * @param useKeyDown whether the use key is held this tick
     * @param actions    the throw, swing and radial the press resolves to
     */
    public void tick(boolean useKeyDown, PressActions actions) {
        if (!armed) {
            return;
        }
        if (!useKeyDown) {
            cancel();
            throwAndSwing(actions);
            return;
        }
        heldTicks++;
        if (heldTicks >= GooGloveItem.RADIAL_THRESHOLD_TICKS) {
            cancel();
            actions.openRadial();
        }
    }

    private static void throwAndSwing(PressActions actions) {
        if (actions.sendThrow()) {
            actions.swing();
        }
    }
}
