package com.mercuriusxeno.goo.client.throwing;

import com.mercuriusxeno.goo.ability.AbilityBadge;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.ability.Delivery;
import com.mercuriusxeno.goo.ability.DeliveryKind;
import com.mercuriusxeno.goo.client.ability.PalmBlobHop;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the glove press resolved off the use key: the press sends nothing and
 * previews the area while held with the arm held in its pose, release throws
 * once and plays the pose's release only on a sent payload, swinging nothing
 * (decisions right-click-held-previews-release-throws, use-key-throw-swings-no-more), and a stream
 * runs from the press for as long as the key is held (decision stream-delivery-held-cone),
 * and a durative press charges and sends once on release
 * (decision durative-self-abilities-wash-over-the-view-in-place-of-eating).
 */
class GloveInputGateTest {

    private static final int LONG_HOLD_TICKS = 40;

    /** Records what a press resolved to, sending a payload or not as told. */
    private static final class RecordingActions implements GloveInputGate.PressActions {
        private final boolean payloadSent;
        private final boolean stream;
        private int throwsSent;
        private int poseHolds;
        private int poseReleases;
        private int poseDrops;
        private int holds;
        private int lastHeldTicks = -1;
        private final boolean throwsOnAttack;
        private int nextJavelins;

        RecordingActions(boolean payloadSent, boolean stream) {
            this(payloadSent, stream, false);
        }

        RecordingActions(boolean payloadSent, boolean stream, boolean throwsOnAttack) {
            this.payloadSent = payloadSent;
            this.stream = stream;
            this.throwsOnAttack = throwsOnAttack;
        }

        @Override
        public boolean throwsOnAttack() {
            return throwsOnAttack;
        }

        @Override
        public void javelinTurned(JavelinTurn turn) {
            if (turn == JavelinTurn.THROWN) {
                nextJavelins++;
            }
        }

        @Override
        public boolean sendThrow(int heldTicks) {
            throwsSent++;
            lastHeldTicks = heldTicks;
            return payloadSent;
        }

        @Override
        public void holdPose() {
            poseHolds++;
        }

        @Override
        public void releasePose() {
            poseReleases++;
        }

        @Override
        public void dropPose() {
            poseDrops++;
        }

        @Override
        public void hold() {
            holds++;
        }

        @Override
        public boolean runsWhileHeld() {
            return stream;
        }

    }

    private static RecordingActions thrown() {
        return new RecordingActions(true, false);
    }

    /**
     * Dart's press: right click held forms one javelin in the hand over the
     * lift, left click throws it once formed, and releasing right click
     * reverts it, throwing nothing
     * (decision dart-forms-on-right-click-and-throws-on-left-click).
     */
    @Nested
    class AJavelin {

        private final RecordingActions actions = new RecordingActions(true, false, true);
        private final GloveInputGate gate = new GloveInputGate();

        private void holdPastTheLift() {
            for (int tick = 0; tick <= PalmBlobHop.LIFT_TICKS; tick++) {
                gate.tick(true, actions);
            }
        }

        @Test
        void releasingRightClickRevertsTheFormedJavelinAndSendsNoThrow() {
            gate.arm();
            holdPastTheLift();
            assertEquals(1f, gate.formShare(), "the javelin is whole past the lift");

            gate.tick(false, actions);

            assertEquals(0, actions.throwsSent);
            assertEquals(0f, gate.formShare());
            assertFalse(gate.isArmed());
        }

        @Test
        void leftClickThrowsTheFormedJavelinOnceAndCancelsNothing() {
            gate.arm();
            holdPastTheLift();

            assertTrue(gate.throwHeld(actions));

            assertEquals(1, actions.throwsSent);
            assertEquals(1, actions.poseReleases);
            assertTrue(gate.isArmed(), "the throw leaves right click's press standing");
        }

        @Test
        void leftClickBeforeTheJavelinIsWholeSendsNone() {
            gate.arm();
            for (int tick = 0; tick < PalmBlobHop.LIFT_TICKS; tick++) {
                gate.tick(true, actions);
            }

            assertFalse(gate.throwHeld(actions));
            assertEquals(0, actions.throwsSent);
            assertTrue(gate.isArmed());
        }

        @Test
        void rightClickStillHeldFormsTheNextJavelinWithNoNewPress() {
            gate.arm();
            holdPastTheLift();
            gate.throwHeld(actions);

            assertEquals(1, actions.nextJavelins);
            assertEquals(0f, gate.formShare(), "the next javelin starts on the palm");
            assertFalse(gate.throwHeld(actions), "nothing is formed to throw yet");
            holdPastTheLift();
            assertEquals(1f, gate.formShare());
            assertTrue(gate.throwHeld(actions));
            assertEquals(2, actions.throwsSent);
        }

        @Test
        void anAbilityThrownOnReleaseThrowsNothingOnLeftClick() {
            RecordingActions released = thrown();
            gate.arm();
            for (int tick = 0; tick <= PalmBlobHop.LIFT_TICKS; tick++) {
                gate.tick(true, released);
            }

            assertFalse(gate.throwHeld(released));
            assertEquals(0, released.throwsSent);
        }
    }

    @Nested
    class AChannel {

        // terrakinesis-shakes-the-blocks-before-the-tear: the wind-up's shake reads how long the channel has run
        @Test
        void aHeldChannelCountsEachTickItRunsAndForgetsThemOnRelease() {
            GloveInputGate gate = new GloveInputGate();
            RecordingActions actions = new RecordingActions(true, true);
            gate.arm();
            for (int tick = 1; tick <= LONG_HOLD_TICKS; tick++) {
                gate.tick(true, actions);
                assertEquals(tick, gate.channelTicks(), "channel ticks on held tick " + tick);
            }
            gate.tick(false, actions);
            assertEquals(0, gate.channelTicks());
        }
    }

    @Nested
    class AThrow {

        // nova-ring-grows-with-the-hold
        @Test
        void theReleaseCarriesTheTicksTheKeyWasHeld() {
            GloveInputGate gate = new GloveInputGate();
            RecordingActions actions = thrown();
            gate.arm();
            for (int tick = 0; tick < 5; tick++) {
                gate.tick(true, actions);
            }
            gate.tick(false, actions);
            assertEquals(5, actions.lastHeldTicks);
        }

        @Test
        void aFreshPressCountsItsHoldFromZero() {
            GloveInputGate gate = new GloveInputGate();
            RecordingActions actions = thrown();
            gate.arm();
            gate.tick(true, actions);
            gate.tick(true, actions);
            gate.tick(false, actions);
            gate.arm();
            gate.tick(true, actions);
            gate.tick(false, actions);
            assertEquals(1, actions.lastHeldTicks);
        }

        @Test
        void thePressSendsNothingAndHoldsThePose() {
            GloveInputGate gate = new GloveInputGate();
            RecordingActions actions = thrown();

            gate.arm();
            gate.tick(true, actions);

            assertEquals(0, actions.throwsSent);
            assertEquals(1, actions.poseHolds);
            assertEquals(0, actions.poseReleases);
        }

        @Test
        void everyHeldTickPreviewsTheAreaAndSendsNothing() {
            GloveInputGate gate = new GloveInputGate();
            RecordingActions actions = thrown();

            gate.arm();
            for (int tick = 0; tick < LONG_HOLD_TICKS; tick++) {
                gate.tick(true, actions);
                assertTrue(gate.isPreviewing(), "held tick " + tick);
                gate.arm();
            }

            assertEquals(0, actions.throwsSent);
            assertEquals(0, actions.holds);
            assertEquals(1, actions.poseHolds);
        }

        // use-key-throw-swings-no-more
        @Test
        void releaseSendsOnceAndPlaysThePoseRelease() {
            GloveInputGate gate = new GloveInputGate();
            RecordingActions actions = thrown();

            gate.arm();
            gate.tick(true, actions);
            gate.tick(true, actions);
            gate.tick(false, actions);
            gate.tick(false, actions);

            assertEquals(1, actions.throwsSent);
            assertEquals(1, actions.poseReleases);
            assertEquals(0, actions.poseDrops);
            assertFalse(gate.isArmed());
            assertFalse(gate.isPreviewing());
        }

        @Test
        void aTapReleasedOnItsFirstTickStillThrows() {
            GloveInputGate gate = new GloveInputGate();
            RecordingActions actions = thrown();

            gate.arm();
            gate.tick(false, actions);

            assertEquals(1, actions.throwsSent);
        }

        @Test
        void anUnsentThrowLetsThePoseDownAndReleasesNothing() {
            GloveInputGate gate = new GloveInputGate();
            RecordingActions actions = new RecordingActions(false, false);

            gate.arm();
            gate.tick(true, actions);
            gate.tick(false, actions);

            assertEquals(1, actions.throwsSent);
            assertEquals(0, actions.poseReleases);
            assertEquals(1, actions.poseDrops);
        }

        @Test
        void theNextPressAfterReleaseThrowsAgain() {
            GloveInputGate gate = new GloveInputGate();
            RecordingActions actions = thrown();

            gate.arm();
            gate.tick(true, actions);
            gate.tick(false, actions);
            gate.arm();
            gate.tick(true, actions);
            gate.tick(false, actions);

            assertEquals(2, actions.throwsSent);
        }

        @Test
        void aCancelledPressThrowsNothingOnRelease() {
            GloveInputGate gate = new GloveInputGate();
            RecordingActions actions = thrown();

            gate.arm();
            gate.tick(true, actions);
            gate.cancel();
            gate.tick(false, actions);

            assertEquals(0, actions.throwsSent);
            assertFalse(gate.isPreviewing());
        }
    }

    @Nested
    class AStream {

        @Test
        void sendsOnThePressAndHoldsEveryLaterHeldTick() {
            GloveInputGate gate = new GloveInputGate();
            RecordingActions actions = new RecordingActions(true, true);

            gate.arm();
            for (int tick = 0; tick < LONG_HOLD_TICKS; tick++) {
                gate.tick(true, actions);
            }

            assertEquals(1, actions.throwsSent);
            assertEquals(1, actions.poseHolds);
            assertEquals(0, actions.poseReleases);
            assertEquals(LONG_HOLD_TICKS - 1, actions.holds);
            assertFalse(gate.isPreviewing());
        }

        @Test
        void releaseEndsTheStreamWithNoFurtherSendAndLetsThePoseDown() {
            GloveInputGate gate = new GloveInputGate();
            RecordingActions actions = new RecordingActions(true, true);

            gate.arm();
            gate.tick(true, actions);
            gate.tick(false, actions);
            gate.tick(false, actions);

            assertEquals(1, actions.throwsSent);
            assertEquals(0, actions.holds);
            assertEquals(1, actions.poseDrops);
            assertEquals(0, actions.poseReleases);
            assertFalse(gate.isArmed());
        }
    }

    /**
     * A durative press charges the lifted blob while right click is held and
     * sends its use once, on release, with no eat
     * (decision durative-self-abilities-wash-over-the-view-in-place-of-eating).
     */
    @Nested
    class ADurativeCharge {

        private RecordingActions durative() {
            return new RecordingActions(true, GloveInputGate.runsFromPress(Delivery.of(DeliveryKind.SELF),
                    AbilityBadge.DURATIVE));
        }

        @Test
        void heldTicksSendNothingAndTheReleaseSendsOnce() {
            GloveInputGate gate = new GloveInputGate();
            RecordingActions actions = durative();

            gate.arm();
            for (int tick = 0; tick < LONG_HOLD_TICKS; tick++) {
                gate.tick(true, actions);
            }

            assertEquals(0, actions.throwsSent, "a durative charge held should send nothing");
            assertTrue(gate.isPreviewing(), "a durative charge held should preview, lifting the blob");

            gate.tick(false, actions);

            assertEquals(1, actions.throwsSent, "the release should send the use once");
            assertFalse(gate.isArmed());
        }
    }

    /**
     * A stream or a channel runs from the press; a durative ability, a self
     * ability on command and an arc each charge and fire on release.
     */
    @Nested
    class WhatRunsFromThePress {

        @Test
        void aDurativeOnASelfDeliveryFiresOnRelease() {
            assertFalse(GloveInputGate.runsFromPress(Delivery.of(DeliveryKind.SELF), AbilityBadge.DURATIVE));
        }

        // residual-badge-marks-the-four-goo-sprays
        @ParameterizedTest
        @EnumSource(value = AbilityBadge.class, names = {"CHANNELED", "RESIDUAL"})
        void aStreamRunsFromThePress(AbilityBadge badge) {
            assertTrue(GloveInputGate.runsFromPress(Delivery.of(DeliveryKind.STREAM), badge));
        }

        @Test
        void aSelfAbilityOnCommandThrowsOnRelease() {
            assertFalse(GloveInputGate.runsFromPress(Delivery.of(DeliveryKind.SELF), AbilityBadge.SELF));
        }

        @Test
        void anArcThrowsOnRelease() {
            assertFalse(GloveInputGate.runsFromPress(Delivery.ARC, AbilityBadge.MOB));
        }
    }

    /**
     * The attack key cancels a charge: nothing is sent at the cancel or at the
     * later release, and the use key's repeats while still held arm nothing; a
     * channeled press stays armed through it
     * (decision abilities-not-channeled-charge-and-cancel-on-left-click).
     */
    @Nested
    class TheAttackKeyCancelsACharge {

        @Test
        void aCancelledChargeSendsNothingAtTheCancelOrTheRelease() {
            GloveInputGate gate = new GloveInputGate();
            RecordingActions actions = thrown();
            gate.arm();
            for (int tick = 0; tick < LONG_HOLD_TICKS; tick++) {
                gate.tick(true, actions);
            }

            assertTrue(gate.cancelCharge(actions), "a charge standing should cancel");
            gate.arm();
            gate.tick(true, actions);
            gate.tick(false, actions);

            assertEquals(0, actions.throwsSent, "the cancel and the release should send nothing");
            assertFalse(gate.isArmed(), "the cancelled press should stay disarmed");
            assertEquals(1, actions.poseDrops, "the cancel should let the arm down");
        }

        @Test
        void thePressAfterTheReleaseChargesAgain() {
            GloveInputGate gate = new GloveInputGate();
            RecordingActions actions = thrown();
            gate.arm();
            gate.tick(true, actions);
            gate.cancelCharge(actions);
            gate.tick(false, actions);

            gate.arm();
            gate.tick(true, actions);
            gate.tick(false, actions);

            assertEquals(1, actions.throwsSent, "a fresh press after the release should throw");
        }

        /** Blast's hold charges its size rather than running from the press, so the attack key cancels it. */
        @Test
        void blastsHoldIsAChargeTheAttackKeyCancels() {
            AbilityDefinition blast = AbilityJson.decode("unstable_blast");
            assertFalse(GloveInputGate.runsFromPress(blast.delivery(), blast.badge()));
        }

        @Test
        void aChanneledPressStaysArmedThroughTheAttackKey() {
            GloveInputGate gate = new GloveInputGate();
            RecordingActions actions = new RecordingActions(true, true);
            gate.arm();
            gate.tick(true, actions);

            assertFalse(gate.cancelCharge(actions), "a channel should not cancel");
            assertTrue(gate.isArmed(), "a channel should stay armed");
        }
    }

    @Test
    void anUnarmedGateIgnoresTheKey() {
        GloveInputGate gate = new GloveInputGate();
        RecordingActions actions = thrown();

        gate.tick(true, actions);
        gate.tick(false, actions);

        assertEquals(0, actions.throwsSent);
    }
}
