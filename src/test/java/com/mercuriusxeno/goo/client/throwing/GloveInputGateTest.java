package com.mercuriusxeno.goo.client.throwing;

import com.mercuriusxeno.goo.item.GooGloveItem;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** Covers the glove press counted off the held use key: throw on release, radial on hold, swing only on a sent payload. */
class GloveInputGateTest {

    private static final int UNDER_THRESHOLD = GooGloveItem.RADIAL_THRESHOLD_TICKS - 1;

    /** Records what a press resolved to, sending a payload or not as told. */
    private static final class RecordingActions implements GloveInputGate.PressActions {
        private final boolean payloadSent;
        private int throwsSent;
        private int swings;
        private int radials;

        RecordingActions(boolean payloadSent) {
            this.payloadSent = payloadSent;
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
        public void openRadial() {
            radials++;
        }
    }

    private static void holdFor(GloveInputGate gate, int ticks, RecordingActions actions) {
        for (int tick = 0; tick < ticks; tick++) {
            gate.tick(true, actions);
        }
    }

    @Nested
    class Swing {

        @Test
        void pressThatSendsNoPayloadSwingsNothing() {
            GloveInputGate gate = new GloveInputGate();
            RecordingActions actions = new RecordingActions(false);

            gate.arm();
            gate.tick(false, actions);

            assertEquals(1, actions.throwsSent);
            assertEquals(0, actions.swings);
        }

        @Test
        void pressThatSendsAPayloadSwingsOnce() {
            GloveInputGate gate = new GloveInputGate();
            RecordingActions actions = new RecordingActions(true);

            gate.arm();
            gate.tick(false, actions);

            assertEquals(1, actions.swings);
        }

        @Test
        void heldPressSwingsNothingBeforeRelease() {
            GloveInputGate gate = new GloveInputGate();
            RecordingActions actions = new RecordingActions(true);

            gate.arm();
            holdFor(gate, UNDER_THRESHOLD, actions);

            assertEquals(0, actions.throwsSent);
            assertEquals(0, actions.swings);
        }
    }

    @Nested
    class HoldAndThrow {

        @Test
        void shortPressThrowsOnRelease() {
            GloveInputGate gate = new GloveInputGate();
            RecordingActions actions = new RecordingActions(true);

            gate.arm();
            holdFor(gate, UNDER_THRESHOLD, actions);
            gate.tick(false, actions);

            assertEquals(1, actions.throwsSent);
            assertEquals(0, actions.radials);
            assertFalse(gate.isArmed());
        }

        @Test
        void holdToThresholdOpensRadialAndThrowsNothing() {
            GloveInputGate gate = new GloveInputGate();
            RecordingActions actions = new RecordingActions(true);

            gate.arm();
            holdFor(gate, GooGloveItem.RADIAL_THRESHOLD_TICKS, actions);
            gate.tick(false, actions);

            assertEquals(1, actions.radials);
            assertEquals(0, actions.throwsSent);
            assertEquals(0, actions.swings);
        }

        @Test
        void repeatedUseDuringPressKeepsTheCount() {
            GloveInputGate gate = new GloveInputGate();
            RecordingActions actions = new RecordingActions(true);

            gate.arm();
            holdFor(gate, UNDER_THRESHOLD, actions);
            gate.arm();
            gate.tick(true, actions);

            assertEquals(1, actions.radials);
        }

        @Test
        void unarmedGateIgnoresTheKey() {
            GloveInputGate gate = new GloveInputGate();
            RecordingActions actions = new RecordingActions(true);

            holdFor(gate, GooGloveItem.RADIAL_THRESHOLD_TICKS, actions);
            gate.tick(false, actions);

            assertEquals(0, actions.radials);
            assertEquals(0, actions.throwsSent);
        }

        @Test
        void cancelledPressThrowsNothingOnRelease() {
            GloveInputGate gate = new GloveInputGate();
            RecordingActions actions = new RecordingActions(true);

            gate.arm();
            gate.cancel();
            gate.tick(false, actions);

            assertEquals(0, actions.throwsSent);
        }
    }
}
