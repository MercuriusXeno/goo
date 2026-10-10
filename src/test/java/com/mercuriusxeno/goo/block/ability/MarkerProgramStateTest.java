package com.mercuriusxeno.goo.block.ability;

import com.mercuriusxeno.goo.ability.program.ShellWalk;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A black hole takes its sphere over ticks: the take runs from a cursor
 * until every cell is in, and a sphere asked for meanwhile is put off until
 * then, so it holds every block the hole takes
 * (decision black-hole-leaves-a-compression-sphere).
 */
class MarkerProgramStateTest {

    private static final int RADIUS = 4;
    private static final ShellWalk.Cursor MIDWAY = new ShellWalk.Cursor(2, 0, 1);

    @Test
    void aTakeRunsFromItsCursorUntilEveryCellIsIn() {
        MarkerProgramState state = new MarkerProgramState();
        state.beginTaking(RADIUS);
        assertTrue(state.taking());
        assertEquals(RADIUS, state.takeRadius());
        assertEquals(ShellWalk.START, state.takeCursor());
        assertFalse(state.tookTo(MIDWAY));
        assertTrue(state.taking());
        assertEquals(MIDWAY, state.takeCursor());
        state.tookTo(null);
        assertFalse(state.taking());
    }

    @Test
    void aSphereAskedForMidTakeIsPutOffUntilTheTakeEnds() {
        MarkerProgramState state = new MarkerProgramState();
        state.beginTaking(RADIUS);
        assertTrue(state.putOffDrop());
        assertFalse(state.tookTo(MIDWAY));
        assertTrue(state.tookTo(null));
    }

    @Test
    void aSphereAskedForWithNoTakeRunningIsDueAtOnce() {
        MarkerProgramState state = new MarkerProgramState();
        assertFalse(state.putOffDrop());
        state.beginTaking(RADIUS);
        assertFalse(state.tookTo(null));
    }
}
