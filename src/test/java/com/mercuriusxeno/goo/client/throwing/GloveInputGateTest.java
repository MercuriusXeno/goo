package com.mercuriusxeno.goo.client.throwing;

import com.mercuriusxeno.goo.ability.AbilityBadge;
import com.mercuriusxeno.goo.ability.Delivery;
import com.mercuriusxeno.goo.ability.DeliveryKind;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the glove press resolved off the use key: the press sends nothing and
 * previews the area while held, release throws once and swings only on a sent
 * payload (decision right-click-held-previews-release-throws), and a stream
 * runs from the press for as long as the key is held (decision stream-delivery-held-cone).
 */
class GloveInputGateTest {

    private static final int LONG_HOLD_TICKS = 40;

    /** Records what a press resolved to, sending a payload or not as told. */
    private static final class RecordingActions implements GloveInputGate.PressActions {
        private final boolean payloadSent;
        private final boolean stream;
        private int throwsSent;
        private int swings;
        private int holds;

        RecordingActions(boolean payloadSent, boolean stream) {
            this.payloadSent = payloadSent;
            this.stream = stream;
        }

        @Override
        public boolean sendThrow() {
            throwsSent++;
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
    }

    private static RecordingActions thrown() {
        return new RecordingActions(true, false);
    }

    @Nested
    class AThrow {

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
