package com.mercuriusxeno.goo.ability.program;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A Zap's Signal wave runs from the landing cell into the struck face and on
 * through the struck block, reaching the cells behind it out to the range and
 * none in front of the landing (decision zap-disperses-into-signal).
 */
class SignalWaveStepTest {

    /** A blob landed in (1, 2, 0) on the west face of a wall at (2, 2, 0). */
    private static final BlockPos CELL = new BlockPos(1, 2, 0);
    private static final double RANGE = 8;
    private static final double CONE = 40;

    private static List<BlockPos> wave() {
        return SignalWaveStep.cellsBehind(CELL, Direction.WEST, RANGE, CONE);
    }

    @Test
    void theWavePassesThroughTheStruckBlock() {
        assertTrue(wave().contains(new BlockPos(4, 2, 0)));
    }

    @Test
    void theWaveStopsAtTheRange() {
        assertFalse(wave().contains(new BlockPos(11, 2, 0)));
    }

    @Test
    void theWaveRunsAwayFromTheThrower() {
        assertFalse(wave().contains(new BlockPos(-1, 2, 0)));
    }
}
