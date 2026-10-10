package com.mercuriusxeno.goo.client.throwing;

import com.mercuriusxeno.goo.ability.AbilityBadge;
import com.mercuriusxeno.goo.ability.Delivery;
import com.mercuriusxeno.goo.ability.DeliveryKind;
import com.mercuriusxeno.goo.ability.SelfEatRoute;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the glove press resolved off the use key: the press sends nothing and
 * previews the area while held, release throws once and swings only on a sent
 * payload (decision right-click-held-previews-release-throws), and a stream
 * runs from the press for as long as the key is held (decision stream-delivery-held-cone),
 * and a self + brew eat sends on the press while the key is still down
 * (decision self-brew-goos-eat-before-the-effect).
 */
class GloveInputGateTest {

    private static final int LONG_HOLD_TICKS = 40;

    /** Records what a press resolved to, sending a payload or not as told. */
    private static final class RecordingActions implements GloveInputGate.PressActions {
        private final boolean payloadSent;
        private final boolean stream;
        private final boolean eats;
        private int throwsSent;
        private int swings;
        private int holds;
        private int lastHeldTicks = -1;

        RecordingActions(boolean payloadSent, boolean stream) {
            this(payloadSent, stream, false);
        }

        RecordingActions(boolean payloadSent, boolean stream, boolean eats) {
            this.payloadSent = payloadSent;
            this.stream = stream;
            this.eats = eats;
        }

        @Override
        public boolean sendThrow(int heldTicks) {
            throwsSent++;
            lastHeldTicks = heldTicks;
            return payloadSent;
        }

        @Override
        public void swing() {
            swings++;
        }

        @Override
        public void hold() {
            holds++;
        }

        @Override
        public boolean runsWhileHeld() {
            return stream;
        }

        @Override
        public boolean eatsOnPress() {
            return eats;
        }
    }

    private static RecordingActions thrown() {
        return new RecordingActions(true, false);
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
        void thePressSendsNothing() {
            GloveInputGate gate = new GloveInputGate();
            RecordingActions actions = thrown();

            gate.arm();
            gate.tick(true, actions);

            assertEquals(0, actions.throwsSent);
            assertEquals(0, actions.swings);
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
        }

        @Test
        void releaseSendsOnceAndSwings() {
            GloveInputGate gate = new GloveInputGate();
            RecordingActions actions = thrown();

            gate.arm();
            gate.tick(true, actions);
            gate.tick(true, actions);
            gate.tick(false, actions);
            gate.tick(false, actions);

            assertEquals(1, actions.throwsSent);
            assertEquals(1, actions.swings);
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
        void anUnsentThrowSwingsNothing() {
            GloveInputGate gate = new GloveInputGate();
            RecordingActions actions = new RecordingActions(false, false);

            gate.arm();
            gate.tick(true, actions);
            gate.tick(false, actions);

            assertEquals(1, actions.throwsSent);
            assertEquals(0, actions.swings);
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
            assertEquals(1, actions.swings);
            assertEquals(LONG_HOLD_TICKS - 1, actions.holds);
            assertFalse(gate.isPreviewing());
        }

        @Test
        void releaseEndsTheStreamWithNoFurtherSend() {
            GloveInputGate gate = new GloveInputGate();
            RecordingActions actions = new RecordingActions(true, true);

            gate.arm();
            gate.tick(true, actions);
            gate.tick(false, actions);
            gate.tick(false, actions);

            assertEquals(1, actions.throwsSent);
            assertEquals(0, actions.holds);
            assertFalse(gate.isArmed());
        }
    }

    /**
     * A self + brew press starts the eat while the use key is still down, since
     * vanilla releases a used item the tick the key comes up, which would cancel
     * an eat started on release (decision self-brew-goos-eat-before-the-effect).
     */
    @Nested
    class AnEat {

        private RecordingActions eaten() {
            return new RecordingActions(true, false, SelfEatRoute.eats(Delivery.of(DeliveryKind.SELF), AbilityBadge.BREW));
        }

        @Test
        void thePressSendsWhileTheKeyIsStillDown() {
            GloveInputGate gate = new GloveInputGate();
            RecordingActions actions = eaten();

            gate.arm();
            gate.tick(true, actions);

            assertEquals(1, actions.throwsSent);
        }

        @Test
        void holdingTheKeySendsOnceSwingsNothingAndPreviewsNothing() {
            GloveInputGate gate = new GloveInputGate();
            RecordingActions actions = eaten();

            gate.arm();
            for (int tick = 0; tick < LONG_HOLD_TICKS; tick++) {
                gate.tick(true, actions);
            }

            assertEquals(1, actions.throwsSent);
            assertEquals(0, actions.swings);
            assertEquals(0, actions.holds);
            assertFalse(gate.isPreviewing());
            assertTrue(gate.isArmed());
        }

        @Test
        void releaseEndsThePressAndTheNextPressSendsAgain() {
            GloveInputGate gate = new GloveInputGate();
            RecordingActions actions = eaten();

            gate.arm();
            gate.tick(true, actions);
            gate.tick(false, actions);
            gate.arm();
            gate.tick(true, actions);

            assertEquals(2, actions.throwsSent);
        }
    }

    /**
     * A brew eats from the press while the use key is down, since vanilla
     * releases any item use the tick the use key is up, and an eat started
     * on release would end the tick it began (decision self-brew-goos-eat-before-the-effect).
     */
    @Nested
    class WhatRunsFromThePress {

        @Test
        void aBrewOnASelfDeliveryRunsFromThePress() {
            assertTrue(GloveInputGate.runsFromPress(Delivery.of(DeliveryKind.SELF), AbilityBadge.BREW));
        }

        @Test
        void aStreamRunsFromThePress() {
            assertTrue(GloveInputGate.runsFromPress(Delivery.of(DeliveryKind.STREAM), AbilityBadge.CHANNELED));
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

    @Test
    void anUnarmedGateIgnoresTheKey() {
        GloveInputGate gate = new GloveInputGate();
        RecordingActions actions = thrown();

        gate.tick(true, actions);
        gate.tick(false, actions);

        assertEquals(0, actions.throwsSent);
    }
}
