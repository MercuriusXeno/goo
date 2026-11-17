package com.mercuriusxeno.goo.client.throwing;

import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.ability.FormSounds;
import com.mercuriusxeno.goo.ability.program.SoundCue;
import com.mercuriusxeno.goo.client.ability.PalmBlobHop;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The sound each turn of Dart's javelin plays, fed by the glove press's turns:
 * the form shink as a javelin begins forming, again for the next after a
 * throw, nothing for the throw, and the lower revert shink as releasing right
 * click begins the revert
 * (decision dart-shinks-as-it-forms-and-pitched-down-as-it-reverts).
 */
class JavelinTurnTest {

    private final FormSounds dart = AbilityJson.decode("metal_dart").delivery().formSounds();

    /** A held throw's press recording each turn its javelin takes. */
    private static final class TurningActions implements GloveInputGate.PressActions {
        private final List<JavelinTurn> turns = new ArrayList<>();

        @Override
        public boolean sendThrow(int heldTicks) {
            return true;
        }

        @Override
        public void holdPose() {
            // the arm's pose is not this test's
        }

        @Override
        public void releasePose() {
            // the arm's pose is not this test's
        }

        @Override
        public void dropPose() {
            // the arm's pose is not this test's
        }

        @Override
        public void hold() {
            // a held throw runs no stream
        }

        @Override
        public boolean runsWhileHeld() {
            return false;
        }

        @Override
        public boolean throwsOnAttack() {
            return true;
        }

        @Override
        public void javelinTurned(JavelinTurn turn) {
            turns.add(turn);
        }

        @Override
        public boolean holdsAFormedJavelin() {
            return true;
        }
    }

    private static void holdPastTheLift(GloveInputGate gate, TurningActions actions) {
        for (int tick = 0; tick <= PalmBlobHop.LIFT_TICKS; tick++) {
            gate.tick(true, actions);
        }
    }

    @Test
    void aFormAThrowTheNextFormAndAReleaseEachSoundOnceAsTheirTurnAsks() {
        GloveInputGate gate = new GloveInputGate();
        TurningActions actions = new TurningActions();
        gate.arm();
        holdPastTheLift(gate, actions);
        gate.throwHeld(actions);
        holdPastTheLift(gate, actions);
        gate.tick(false, actions);

        List<Optional<SoundCue>> sounds = actions.turns.stream().map(turn -> turn.soundOf(dart)).toList();

        assertEquals(List.of(JavelinTurn.FORMS, JavelinTurn.THROWN, JavelinTurn.FORMS, JavelinTurn.REVERTS),
                actions.turns);
        assertEquals(List.of(dart.form(), Optional.empty(), dart.form(), dart.revert()), sounds);
    }

    @Test
    void aReleaseBeforeTheJavelinIsWholeStillShinksTheRevert() {
        GloveInputGate gate = new GloveInputGate();
        TurningActions actions = new TurningActions();
        gate.arm();
        gate.tick(true, actions);
        gate.tick(false, actions);

        assertEquals(List.of(dart.form(), dart.revert()),
                actions.turns.stream().map(turn -> turn.soundOf(dart)).toList());
    }

    @Test
    void theThrowSoundsNothing() {
        assertEquals(Optional.empty(), JavelinTurn.THROWN.soundOf(dart));
    }
}
