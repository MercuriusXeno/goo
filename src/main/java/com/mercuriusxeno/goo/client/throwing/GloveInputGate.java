package com.mercuriusxeno.goo.client.throwing;

import com.mercuriusxeno.goo.ability.AbilityBadge;
import com.mercuriusxeno.goo.ability.Delivery;
import com.mercuriusxeno.goo.ability.HeldRoute;
import com.mercuriusxeno.goo.ability.SelfEatRoute;
import org.jspecify.annotations.Nullable;

/**
 * The glove's right-click input as a press the client resolves off the use
 * key. A press arms the throw and previews the ability's area for as long as
 * the key is held, the arm held in the ability's pose; release throws once
 * and plays the pose's release. A stream runs from the press instead,
 * streaming on every held tick with the arm held in its pose until the key
 * comes up. A self + brew press sends on the press too, while the use key is
 * still down, and then only waits for release: vanilla releases a used item
 * the tick the key comes up, so an eat started on release would be cancelled
 * the tick it began, and letting go mid-eat is what cancels it. A press
 * plays the ability's pose, its release only for a throw that sent a
 * payload, and never for an eat; a blob thrown swings there instead.
 * decision right-click-held-previews-release-throws
 * decision self-brew-goos-eat-before-the-effect
 * decision use-animation-only-when-goo-throws
 * decision use-key-throw-swings-no-more
 */
public final class GloveInputGate {

    /** What the gate does for a press: the client's throw, arm pose and stream. */
    public interface PressActions {

        /**
         * Sends a throw payload for the glove's selection, or a stream's first tick.
         *
         * @param heldTicks the ticks the use key was held before the throw, which a charged ability fires by
         * @return true when a payload was sent
         */
        boolean sendThrow(int heldTicks);

        /** Holds the glove arm in the selected ability's pose while the press is held. */
        void holdPose();

        /** Plays the selected ability's release pose, as a sent throw lets go. */
        void releasePose();

        /** Eases the glove arm back down, for a press let go that sent nothing or a stream ended. */
        void dropPose();

        /** Carries a stream one tick further. */
        void hold();

        /**
         * Whether the selected ability runs from the press while held, a stream,
         * rather than previewing and throwing on release.
         *
         * @return true for a stream
         */
        boolean runsWhileHeld();

        /**
         * Whether the selected ability is eaten: it sends on the press and the
         * player eats while the key stays down.
         *
         * @return true for a self + brew ability
         */
        boolean eatsOnPress();
    }

    /**
     * Whether an ability runs from the press while the use key is held,
     * rather than previewing and throwing on release: a held ability, a
     * stream or a channel (decisions stream-delivery-held-cone,
     * flatten-disc-cursor-breaks-above-the-plane), and a brew, whose eat
     * vanilla releases the tick the use key is up, so an eat begun on release
     * would end the tick it started; the gate eats a brew before it streams.
     * decision self-brew-goos-eat-before-the-effect
     *
     * @param delivery the selected ability's delivery
     * @param badge    the selected ability's badge, or null where the client holds no synced copy
     * @return true for a stream, a channel or a brew
     */
    public static boolean runsFromPress(Delivery delivery, @Nullable AbilityBadge badge) {
        return HeldRoute.runsWhileHeld(delivery, badge) || SelfEatRoute.eats(delivery, badge);
    }

    private boolean armed;
    private boolean streaming;
    private boolean previewing;
    private boolean eating;
    /** Ticks the live press has previewed, which a charged ability's release fires by. */
    private int heldTicks;

    /** Starts a press when the glove's use reaches the client; a live press ignores the repeat. */
    public void arm() {
        if (!armed) {
            armed = true;
            streaming = false;
            previewing = false;
            eating = false;
            heldTicks = 0;
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

    /**
     * Whether the held press shows the ability's area: from the first held tick
     * of a press that throws on release until its release.
     *
     * @return true while the area preview draws
     */
    public boolean isPreviewing() {
        return previewing;
    }

    /**
     * Ticks the live press has held its preview, which a charged ability's
     * charge reads (decision nova-ring-grows-with-the-hold).
     *
     * @return the held ticks, 0 while no press previews
     */
    public int heldTicks() {
        return previewing ? heldTicks : 0;
    }

    /** Drops the live press. */
    public void cancel() {
        armed = false;
        streaming = false;
        previewing = false;
        eating = false;
    }

    /**
     * Advances a live press by one client tick: a stream sends on its first
     * tick and holds on every later held tick (decision
     * stream-delivery-held-cone); any other press previews while held and
     * throws once the use key is up.
     *
     * @param useKeyDown whether the use key is held this tick
     * @param actions    the throw, arm pose and stream the press resolves to
     */
    public void tick(boolean useKeyDown, PressActions actions) {
        if (!armed) {
            return;
        }
        if (eats(actions)) {
            tickEat(useKeyDown, actions);
        } else if (streams(actions)) {
            tickStream(useKeyDown, actions);
        } else {
            tickThrow(useKeyDown, actions);
        }
    }

    private boolean eats(PressActions actions) {
        return eating || (!previewing && !streaming && actions.eatsOnPress());
    }

    private boolean streams(PressActions actions) {
        return streaming || (!previewing && actions.runsWhileHeld());
    }

    private void tickThrow(boolean useKeyDown, PressActions actions) {
        if (useKeyDown) {
            if (!previewing) {
                actions.holdPose();
            }
            previewing = true;
            heldTicks++;
        } else {
            throwAndRelease(actions, heldTicks);
            cancel();
        }
    }

    private void tickEat(boolean useKeyDown, PressActions actions) {
        if (!eating) {
            eating = true;
            actions.sendThrow(0);
        }
        if (!useKeyDown) {
            cancel();
        }
    }

    private void tickStream(boolean useKeyDown, PressActions actions) {
        if (streaming) {
            if (useKeyDown) {
                actions.hold();
            }
        } else {
            streaming = true;
            if (actions.sendThrow(0)) {
                actions.holdPose();
            }
        }
        if (!useKeyDown) {
            actions.dropPose();
            cancel();
        }
    }

    /**
     * Throws a released press: a sent payload plays the pose's release, and
     * a throw that sent nothing lets the held arm down.
     *
     * @param actions   the throw and arm pose the press resolves to
     * @param heldTicks the ticks the use key was held
     */
    private static void throwAndRelease(PressActions actions, int heldTicks) {
        if (actions.sendThrow(heldTicks)) {
            actions.releasePose();
        } else {
            actions.dropPose();
        }
    }
}
