package com.mercuriusxeno.goo.ability.program;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Covers what Level's disc does with the Mortar it carries: each gap under the
 * disc takes a stone block for a layer, and the layers left lie back over the
 * disc in whole cells of fifteen.
 * level-dragged-under-rock-goo-carries-it-and-deposits-stone-under-the-disc
 * layer-of-rock-goo-is-a-stone-block
 */
class MortarPavingTest {

    private static final BlockPos FIRST = new BlockPos(0, 0, 0);
    private static final BlockPos SECOND = new BlockPos(1, 0, 0);
    private static final BlockPos THIRD = new BlockPos(2, 0, 0);
    private static final int TEN_LAYERS = 10;
    private static final int SEVEN_LAYERS = 7;
    private static final int TWO_LAYERS = 2;
    private static final int FULL = 15;
    private static final int TWENTY_LAYERS = 20;

    @Nested
    class Deposit {

        @Test
        void eachGapTakesAStoneForALayer() {
            MortarPaving.Deposit deposit = MortarPaving.deposit(List.of(FIRST, SECOND, THIRD), TEN_LAYERS);
            assertEquals(List.of(FIRST, SECOND, THIRD), deposit.paved());
            assertEquals(SEVEN_LAYERS, deposit.layersLeft());
        }

        @Test
        void theCarrySpentLeavesTheLastGapsOpen() {
            MortarPaving.Deposit deposit = MortarPaving.deposit(List.of(FIRST, SECOND, THIRD), TWO_LAYERS);
            assertEquals(List.of(FIRST, SECOND), deposit.paved());
            assertEquals(0, deposit.layersLeft());
        }
    }

    @Nested
    class Lay {

        @Test
        void theLayersLeftFillTheFirstCellsWholeFirst() {
            assertEquals(Map.of(FIRST, FULL, SECOND, TWENTY_LAYERS - FULL),
                    MortarPaving.lay(List.of(FIRST, SECOND, THIRD), TWENTY_LAYERS));
        }

        @Test
        void noLayersLayNoCell() {
            assertEquals(Map.of(), MortarPaving.lay(List.of(FIRST, SECOND), 0));
        }
    }
}
