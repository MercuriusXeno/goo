package com.mercuriusxeno.goo.client.throwing;

import com.mercuriusxeno.goo.item.GooGloveItem;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** Covers the glove press counted off the held use key: throw on release, radial on hold, swing only on a sent payload. */
class GloveInputGateTest {

    private static final int UNDER_THRESHOLD = GooGloveItem.RADIAL_THRESHOLD_TICKS - 1;

    /** Records what a press resolved to, sending a payload or not as told. */
    private static final class RecordingActions implements GloveInputGate.PressActions {
        private final BooleanSupplier payloadSent;
        private int throwsSent;
        private int swings;
        private int radials;

        RecordingActions(boolean payloadSent) {
            this(() -> payloadSent);
        }

        RecordingActions(BooleanSupplier payloadSent) {
            this.payloadSent = payloadSent;
        }

        @Override
        public boolean sendThrow() {
            throwsSent++;
            return payloadSent.getAsBoolean();
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

    /** The hold opens the radial irrespective of affordability or selection (decision hold-opens-radial-regardless-of-cost). */
    @Nested
    class HoldWhenBroke {

        private static final int NO_HOLDINGS = 0;

        /** Throws only what zero holdings afford, as GloveThrowSender prices it. */
        private static RecordingActions brokeActions() {
            return new RecordingActions(() -> GloveThrowSender.affordsThrow(null, 0, amount -> NO_HOLDINGS >= amount));
        }

        @Test
        void heldPressWithZeroHoldingsOpensRadialAndSendsNoThrow() {
            GloveInputGate gate = new GloveInputGate();
            RecordingActions actions = brokeActions();

            gate.arm();
            holdFor(gate, GooGloveItem.RADIAL_THRESHOLD_TICKS, actions);

            assertEquals(1, actions.radials);
            assertEquals(0, actions.throwsSent);
        }

        @Test
        void heldPressWithNoSelectionOpensRadialAndSendsNoThrow() {
            GloveInputGate gate = new GloveInputGate();
            RecordingActions noSelection = new RecordingActions(false);

            gate.arm();
            holdFor(gate, GooGloveItem.RADIAL_THRESHOLD_TICKS, noSelection);

            assertEquals(1, noSelection.radials);
            assertEquals(0, noSelection.throwsSent);
        }

        @Test
        void shortPressWithZeroHoldingsSwingsNothing() {
            GloveInputGate gate = new GloveInputGate();
            RecordingActions actions = brokeActions();

            gate.arm();
            gate.tick(false, actions);

            assertEquals(0, actions.swings);
            assertEquals(0, actions.radials);
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
