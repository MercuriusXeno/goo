package com.mercuriusxeno.goo.client.throwing;

import com.mercuriusxeno.goo.ability.AbilityBadge;
import com.mercuriusxeno.goo.ability.Delivery;
import com.mercuriusxeno.goo.ability.HeldRoute;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.Nullable;

/**
 * The glove's right-click input as a press the client resolves off the use
 * key. A press arms the throw and previews the ability's area for as long as
 * the key is held, the arm held in the ability's pose; release throws once
 * and plays the pose's release. A stream runs from the press instead,
 * streaming on every held tick with the arm held in its pose until the key
 * comes up. A durative press charges like a throw, sending its use once on
 * release. A press plays the ability's pose, its release only for a throw
 * that sent a payload; a blob thrown swings there instead.
 * decision right-click-held-previews-release-throws
 * decision durative-self-abilities-wash-over-the-view-in-place-of-eating
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
    }

    /**
     * Whether an ability runs from the press while the use key is held,
     * rather than previewing and throwing on release: a held ability, a
     * stream or a channel (decisions stream-delivery-held-cone,
     * flatten-disc-cursor-breaks-above-the-plane). A durative ability charges
     * and fires on release (decision durative-self-abilities-wash-over-the-view-in-place-of-eating).
     *
     * @param delivery the selected ability's delivery
     * @param badge    the selected ability's badge, or null where the client holds no synced copy
     * @return true for a stream or a channel
     */
    public static boolean runsFromPress(Delivery delivery, @Nullable AbilityBadge badge) {
        return HeldRoute.runsWhileHeld(delivery, badge);
    }

    /** Where each press rolls its scatter seed from. */
    private static final RandomSource SEEDS = RandomSource.create();

    private boolean armed;
    private boolean streaming;
    private boolean previewing;
    /** Ticks the live press has previewed, which a charged ability's release fires by. */
    private int heldTicks;
    /** Ticks the live press has run a held channel or stream, which Terrakinesis's wind-up reads. */
    private int channelTicks;
    /** The scatter the live press slings its knives by. */
    private long scatterSeed;
    /** Whether a charge was cancelled with the use key still down, so no press arms until it comes up. */
    private boolean cancelledUntilRelease;

    /** Starts a press when the glove's use reaches the client; a live press ignores the repeat. */
    public void arm() {
        if (!armed && !cancelledUntilRelease) {
            armed = true;
            streaming = false;
            previewing = false;
            heldTicks = 0;
            channelTicks = 0;
            scatterSeed = SEEDS.nextLong();
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
     * Whether the held press runs a stream or a channel: from its first tick
     * until the use key comes up.
     *
     * @return true while a stream or a channel runs from the press
     */
    public boolean isStreaming() {
        return streaming;
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

    /**
     * Ticks the live press has run a held channel or stream, the first counted 1
     * (decision terrakinesis-shakes-the-blocks-before-the-tear).
     *
     * @return the ticks, 0 while no channel runs
     */
    public int channelTicks() {
        return streaming ? channelTicks : 0;
    }

    /**
     * The scatter the live press slings its knives by, rolled anew each
     * press and held through it, so the lines hold still while held and the
     * server throws the knives along them.
     * knives-aims-a-spread-fan-not-a-blob-line
     *
     * @return the press's scatter seed
     */
    public long scatterSeed() {
        return scatterSeed;
    }

    /**
     * Cancels a charge standing, the attack key's way out of a mistaken cast:
     * the arm drops, nothing is sent, and the use key's repeats arm nothing
     * until it comes up, so letting go fires nothing. A channel or a stream
     * runs on untouched.
     * abilities-not-channeled-charge-and-cancel-on-left-click
     *
     * @param actions the arm pose the press resolves to
     * @return true where a charge stood and was cancelled
     */
    public boolean cancelCharge(PressActions actions) {
        if (!armed || !previewing) {
            return false;
        }
        actions.dropPose();
        cancel();
        cancelledUntilRelease = true;
        return true;
    }

    /**
     * Whether the use key belongs to the glove: a press is live, or a
     * cancelled charge waits for the key to come up.
     *
     * @return true while the use key's turns are the glove's
     */
    public boolean holdsTheUseKey() {
        return armed || cancelledUntilRelease;
    }

    /** Drops the live press. */
    public void cancel() {
        armed = false;
        channelTicks = 0;
        streaming = false;
        previewing = false;
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
            cancelledUntilRelease &= useKeyDown;
            return;
        }
        if (streams(actions)) {
            tickStream(useKeyDown, actions);
        } else {
            tickThrow(useKeyDown, actions);
        }
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

    private void tickStream(boolean useKeyDown, PressActions actions) {
        channelTicks++;
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
