package com.mercuriusxeno.goo.client.throwing;

/**
 * The glove's right-click input as a press the client resolves off the use
 * key, with no using state: the press throws on the tick it arms, and a use
 * key held after it throws nothing more, leaving the hold readable through
 * the use key for a stream delivery. The arm swings only for a throw that
 * sent a payload.
 * decision right-click-throws-on-press
 * decision use-animation-only-when-goo-throws
 */
public final class GloveInputGate {

    /** What the gate does for a press: the client's throw and swing. */
    public interface PressActions {

        /**
         * Sends a throw payload for the glove's selection.
         *
         * @return true when a payload was sent
         */
        boolean sendThrow();

        /** Swings the arm holding the glove. */
        void swing();
    }

    private boolean armed;
    private boolean thrown;

    /** Starts a press when the glove's use reaches the client; a live press ignores the repeat. */
    public void arm() {
        if (!armed) {
            armed = true;
            thrown = false;
        }
    }

    /**
     * Whether a press is live.
     *
     * @return true from the glove's use until the use key is released or the press is cancelled
     */
    public boolean isArmed() {
        return armed;
    }

    /** Drops the live press. */
    public void cancel() {
        armed = false;
        thrown = false;
    }

    /**
     * Advances a live press by one client tick: throws on its first tick,
     * and ends once the use key is up.
     *
     * @param useKeyDown whether the use key is held this tick
     * @param actions    the throw and swing the press resolves to
     */
    public void tick(boolean useKeyDown, PressActions actions) {
        if (!armed) {
            return;
        }
        if (!thrown) {
            thrown = true;
            throwAndSwing(actions);
        }
        if (!useKeyDown) {
            cancel();
        }
    }

    private static void throwAndSwing(PressActions actions) {
        if (actions.sendThrow()) {
            actions.swing();
        }
    }
}
