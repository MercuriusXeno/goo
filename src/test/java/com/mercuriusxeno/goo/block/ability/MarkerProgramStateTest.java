package com.mercuriusxeno.goo.block.ability;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A black hole takes its sphere over ticks: the take runs until every cell
 * is in, and a sphere asked for meanwhile is put off until then, so it holds
 * every block the hole takes (decision black-hole-leaves-a-compression-sphere).
 */
class MarkerProgramStateTest {

    private static final int RADIUS = 4;
    private static final int TOTAL = 10;

    @Test
    void aTakeRunsUntilEveryCellIsIn() {
        MarkerProgramState state = new MarkerProgramState();
        state.beginTaking(RADIUS);
        assertTrue(state.taking());
        assertFalse(state.tookTo(TOTAL / 2, TOTAL));
        assertTrue(state.taking());
        assertEquals(TOTAL / 2, state.taken());
        state.tookTo(TOTAL, TOTAL);
        assertFalse(state.taking());
    }

    @Test
    void aSphereAskedForMidTakeIsPutOffUntilTheTakeEnds() {
        MarkerProgramState state = new MarkerProgramState();
        state.beginTaking(RADIUS);
        assertTrue(state.putOffDrop());
        assertFalse(state.tookTo(TOTAL / 2, TOTAL));
        assertTrue(state.tookTo(TOTAL, TOTAL));
    }

    @Test
    void aSphereAskedForWithNoTakeRunningIsDueAtOnce() {
        MarkerProgramState state = new MarkerProgramState();
        assertFalse(state.putOffDrop());
        state.beginTaking(RADIUS);
        assertFalse(state.tookTo(TOTAL, TOTAL));
    }

    @Test
    void theSphereCellsAreBuiltOnceFromTheRadius() {
        MarkerProgramState state = new MarkerProgramState();
        state.beginTaking(RADIUS);
        int[] built = new int[1];
        List<BlockPos> first = state.takeCells(BlockPos.ZERO, (center, radius) -> {
            built[0]++;
            assertEquals(RADIUS, radius);
            return List.of(center.above());
        });
        assertSame(first, state.takeCells(BlockPos.ZERO, (center, radius) -> List.of()));
        assertEquals(1, built[0]);
    }
}
