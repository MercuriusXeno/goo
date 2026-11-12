package com.mercuriusxeno.goo.client.ability;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import java.util.function.Predicate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Whether an echo lights a cell is read from the world once and kept until
 * a block change reaches the cell or the cells beside it, so the rings that
 * cross a cell again read nothing
 * (decision diagnose-sculkers-lag-and-prove-it-improved-significantly).
 */
class EchoCellsTest {

    private static final BlockPos GROUND = BlockPos.ZERO;
    private static final BlockPos BURIED = new BlockPos(0, -2, 0);

    @Test
    void aCellBesideOpenSpaceThatLightsAnswersTrueAndABuriedOneFalse() {
        EchoCells cells = new EchoCells(new Counting(pos -> pos.getY() <= 0), new Counting(pos -> pos.getY() > 0));
        assertTrue(cells.echoesOff(GROUND), "ground under air lights");
        assertFalse(cells.echoesOff(BURIED), "a block buried in ground lights not");
    }

    @Test
    void aCellAskedAgainReadsTheWorldNoMore() {
        Counting lights = new Counting(pos -> pos.getY() <= 0);
        Counting passesSound = new Counting(pos -> pos.getY() > 0);
        EchoCells cells = new EchoCells(lights, passesSound);
        cells.echoesOff(GROUND);
        int read = lights.asked + passesSound.asked;
        cells.echoesOff(GROUND);
        assertEquals(read, lights.asked + passesSound.asked, "world reads once the cell was answered");
    }

    @Test
    void aBlockChangeBesideACellReadsItAgain() {
        Counting lights = new Counting(pos -> pos.getY() <= 0);
        Counting passesSound = new Counting(pos -> pos.getY() > 0);
        EchoCells cells = new EchoCells(lights, passesSound);
        cells.echoesOff(GROUND);
        int read = lights.asked;
        cells.forgetAround(GROUND.above());
        cells.echoesOff(GROUND);
        assertEquals(read + 1, lights.asked, "the cell under the changed block reads again");
    }

    /** A world test counting how often it is asked. */
    private static final class Counting implements Predicate<BlockPos> {

        private final Predicate<BlockPos> answer;
        private int asked;

        Counting(Predicate<BlockPos> answer) {
            this.answer = answer;
        }

        @Override
        public boolean test(BlockPos pos) {
            asked++;
            return answer.test(pos);
        }
    }
}
