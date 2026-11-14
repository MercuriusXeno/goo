package com.mercuriusxeno.goo.client.throwing;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the charm-like cast's press driven through the glove's input gate:
 * the right click takes the mob highlighted at the press and the release
 * sends that mob whatever the aim does after, once the wind-up is full, while
 * an early release, a left click, a press with no mob highlighted and a taken
 * mob that dies send nothing (decision
 * charm-like-abilities-lock-the-highlighted-mob-on-right-click).
 */
class CharmLikeCastTest {

    private static final int WIND_UP_TICKS = 20;
    private static final int TAKEN_MOB = 7;
    private static final int OTHER_MOB = 9;
    private static final int PART_OF_THE_WIND_UP = 5;

    /** The mob the frame highlights, moved by each test as the aim would move. */
    private OptionalInt highlighted = OptionalInt.of(TAKEN_MOB);
    private final Set<Integer> standing = new HashSet<>(Set.of(TAKEN_MOB, OTHER_MOB));
    private final List<Integer> sent = new ArrayList<>();
    private final GloveInputGate gate = new GloveInputGate();
    private final CharmLikeCast cast = new CharmLikeCast(() -> highlighted, standing::contains);
    private final GloveInputGate.PressActions actions = new SendingActions();

    /** Sends the mob the cast's release answers, as the glove's throw sender does. */
    private final class SendingActions implements GloveInputGate.PressActions {

        @Override
        public boolean sendThrow(int heldTicks) {
            OptionalInt mob = cast.release(heldTicks, WIND_UP_TICKS);
            mob.ifPresent(sent::add);
            return mob.isPresent();
        }

        @Override
        public void holdPose() {
            // the pose plays no part in what the cast sends
        }

        @Override
        public void releasePose() {
            // the pose plays no part in what the cast sends
        }

        @Override
        public void dropPose() {
            // the pose plays no part in what the cast sends
        }

        @Override
        public void hold() {
            // a charm-like cast streams nothing while held
        }

        @Override
        public boolean runsWhileHeld() {
            return false;
        }
    }

    private void hold(int ticks) {
        for (int tick = 0; tick < ticks; tick++) {
            gate.tick(true, actions);
            cast.tick(gate, actions);
        }
    }

    private void release() {
        gate.tick(false, actions);
    }

    @Nested
    class TheTakenMob {

        @Test
        void holdsWhileTheAimMovesToAnotherMobToNoMobAndBehindAWall() {
            assertTrue(cast.press(gate));
            highlighted = OptionalInt.of(OTHER_MOB);
            hold(PART_OF_THE_WIND_UP);
            highlighted = OptionalInt.empty();
            hold(PART_OF_THE_WIND_UP);
            // a wall between the player and the taken mob: the frame highlights nothing, the mob still stands
            hold(WIND_UP_TICKS);
            release();
            assertEquals(List.of(TAKEN_MOB), sent);
        }
    }

    @Nested
    class TheRelease {

        @Test
        void aFullWindUpSendsTheTakenMobOnce() {
            cast.press(gate);
            hold(WIND_UP_TICKS);
            release();
            assertEquals(List.of(TAKEN_MOB), sent);
        }

        @Test
        void aReleaseBeforeTheWindUpFillsSendsNothing() {
            cast.press(gate);
            hold(WIND_UP_TICKS - 1);
            release();
            assertTrue(sent.isEmpty());
        }

        @Test
        void aLeftClickSendsNothingAtTheCancelOrTheRelease() {
            cast.press(gate);
            hold(PART_OF_THE_WIND_UP);
            gate.cancelCharge(actions);
            hold(WIND_UP_TICKS);
            release();
            assertTrue(sent.isEmpty());
        }

        @Test
        void aPressWithNoMobHighlightedArmsNothingAndSendsNothing() {
            highlighted = OptionalInt.empty();
            assertFalse(cast.press(gate));
            assertFalse(gate.isArmed());
            hold(WIND_UP_TICKS);
            release();
            assertTrue(sent.isEmpty());
        }

        @Test
        void aTakenMobThatDiesCancelsTheCast() {
            cast.press(gate);
            hold(PART_OF_THE_WIND_UP);
            standing.remove(TAKEN_MOB);
            hold(WIND_UP_TICKS);
            assertFalse(gate.isArmed());
            release();
            assertTrue(sent.isEmpty());
        }
    }
}
