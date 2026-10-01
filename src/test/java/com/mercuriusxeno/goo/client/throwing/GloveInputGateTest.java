package com.mercuriusxeno.goo.client.throwing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Covers the glove press resolved off the use key: throw on the press, once per hold, swing only on a sent payload (decision right-click-throws-on-press), and every later held tick a hold. */
class GloveInputGateTest {

    private static final int LONG_HOLD_TICKS = 40;

    /** Records what a press resolved to, sending a payload or not as told. */
    private static final class RecordingActions implements GloveInputGate.PressActions {
        private final boolean payloadSent;
        private int throwsSent;
        private int swings;
        private int holds;

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
        public void hold() {
            holds++;
        }
    }

    @Test
    void pressThrowsOnItsOwnTick() {
        GloveInputGate gate = new GloveInputGate();
        RecordingActions actions = new RecordingActions(true);

        gate.arm();
        gate.tick(true, actions);

        assertEquals(1, actions.throwsSent);
        assertEquals(1, actions.swings);
    }

    @Test
    void heldPressThrowsOnce() {
        GloveInputGate gate = new GloveInputGate();
        RecordingActions actions = new RecordingActions(true);

        gate.arm();
        for (int tick = 0; tick < LONG_HOLD_TICKS; tick++) {
            gate.tick(true, actions);
            gate.arm();
        }

        assertEquals(1, actions.throwsSent);
        assertEquals(1, actions.swings);
        assertTrue(gate.isArmed());
    }

    /** Every held tick after the press holds, which a stream streams on (decision stream-delivery-held-cone). */
    @Test
    void everyHeldTickAfterThePressHolds() {
        GloveInputGate gate = new GloveInputGate();
        RecordingActions actions = new RecordingActions(true);

        gate.arm();
        for (int tick = 0; tick < LONG_HOLD_TICKS; tick++) {
            gate.tick(true, actions);
        }

        assertEquals(LONG_HOLD_TICKS - 1, actions.holds);
    }

    @Test
    void releaseHoldsNothing() {
        GloveInputGate gate = new GloveInputGate();
        RecordingActions actions = new RecordingActions(true);

        gate.arm();
        gate.tick(true, actions);
        gate.tick(false, actions);
        gate.tick(false, actions);

        assertEquals(0, actions.holds);
    }

    @Test
    void unsentThrowSwingsNothing() {
        GloveInputGate gate = new GloveInputGate();
        RecordingActions actions = new RecordingActions(false);

        gate.arm();
        gate.tick(true, actions);

        assertEquals(1, actions.throwsSent);
        assertEquals(0, actions.swings);
    }

    @Test
    void releasedKeyEndsThePressAfterItsThrow() {
        GloveInputGate gate = new GloveInputGate();
        RecordingActions actions = new RecordingActions(true);

        gate.arm();
        gate.tick(false, actions);

        assertEquals(1, actions.throwsSent);
        assertFalse(gate.isArmed());
    }

    @Test
    void nextPressAfterReleaseThrowsAgain() {
        GloveInputGate gate = new GloveInputGate();
        RecordingActions actions = new RecordingActions(true);

        gate.arm();
        gate.tick(true, actions);
        gate.tick(false, actions);
        gate.arm();
        gate.tick(true, actions);

        assertEquals(2, actions.throwsSent);
    }

    @Test
    void unarmedGateIgnoresTheKey() {
        GloveInputGate gate = new GloveInputGate();
        RecordingActions actions = new RecordingActions(true);

        gate.tick(true, actions);
        gate.tick(false, actions);

        assertEquals(0, actions.throwsSent);
    }

    @Test
    void cancelledPressThrowsNothing() {
        GloveInputGate gate = new GloveInputGate();
        RecordingActions actions = new RecordingActions(true);

        gate.arm();
        gate.cancel();
        gate.tick(true, actions);

        assertEquals(0, actions.throwsSent);
    }
}
