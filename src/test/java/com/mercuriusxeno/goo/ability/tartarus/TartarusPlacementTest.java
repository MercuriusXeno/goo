package com.mercuriusxeno.goo.ability.tartarus;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Covers where Tartarus' placement sets a player down from a start, read over a block lookup the test builds, every
 * cell it leaves unset standing solid.
 */
class TartarusPlacementTest {

    /** The nether's height under its bedrock roof: Y 0 to 127. */
    private static final HeightSpan NETHER = HeightSpan.of(0, 128);
    private static final BlockPos START = new BlockPos(100, 60, -200);
    private static final int REACH = 4;

    /** A column of cells, solid wherever the test stood nothing. */
    private static final class Column implements TartarusCells {

        private final Map<BlockPos, Cell> cells = new HashMap<>();

        @Override
        public Cell at(BlockPos pos) {
            return cells.getOrDefault(pos, Cell.FLOOR);
        }

        /** Stands two blocks of air on the floor below the feet. */
        Column standing(BlockPos feet) {
            cells.put(feet, Cell.OPEN);
            cells.put(feet.above(), Cell.OPEN);
            return this;
        }

        Column cell(BlockPos pos, Cell cell) {
            cells.put(pos, cell);
            return this;
        }
    }

    /** decision tartarus-sets-you-on-the-nearest-safe-floor-at-the-scaled-spot-or-refuses */
    @Nested
    class Nearest {

        @Test
        void aSafeFloorFourAboveWinsOverOneNineBelowTheStart() {
            Column column = new Column().standing(START.above(4)).standing(START.below(9));

            assertEquals(Optional.of(START.above(4)), TartarusPlacement.nearest(column, START, REACH, NETHER));
        }

        @Test
        void theSameInputsAnswerTheSameSpot() {
            Column column = new Column().standing(START.above(4)).standing(START.below(9));

            assertEquals(TartarusPlacement.nearest(column, START, REACH, NETHER),
                    TartarusPlacement.nearest(column, START, REACH, NETHER));
        }

        @Test
        void aSafeFloorNineBelowAnswersWhereNoneStandsNearer() {
            Column column = new Column().standing(START.below(9));

            assertEquals(Optional.of(START.below(9)), TartarusPlacement.nearest(column, START, REACH, NETHER));
        }

        @Test
        void aColumnBesideTheStartAnswersWhereTheStartsOwnHoldsNone() {
            BlockPos beside = START.east(2).above(3);
            Column column = new Column().standing(beside);

            assertEquals(Optional.of(beside), TartarusPlacement.nearest(column, START, REACH, NETHER));
        }

        @Test
        void aColumnPastTheReachAnswersNone() {
            Column column = new Column().standing(START.east(REACH + 1));

            assertEquals(Optional.empty(), TartarusPlacement.nearest(column, START, REACH, NETHER));
        }
    }

    /** decision tartarus-sets-you-on-the-nearest-safe-floor-at-the-scaled-spot-or-refuses */
    @Nested
    class Safe {

        @Test
        void theSoundSpotWinsOverNearerOnesOverLavaUnderOneAirAndAboveTheRoof() {
            BlockPos roofStart = new BlockPos(100, 124, -200);
            BlockPos overLava = roofStart.below(1);
            BlockPos oneAir = roofStart.below(4);
            BlockPos aboveRoof = new BlockPos(100, 128, -200);
            BlockPos sound = roofStart.below(8);
            Column column = new Column()
                    .standing(overLava).cell(overLava.below(), TartarusCells.Cell.BURNING)
                    .cell(oneAir, TartarusCells.Cell.OPEN)
                    .standing(aboveRoof)
                    .standing(sound);

            assertEquals(Optional.of(sound), TartarusPlacement.nearest(column, roofStart, REACH, NETHER));
        }

        @Test
        void aSpotBesideFireIsPassedOver() {
            BlockPos besideFire = START.above(1);
            BlockPos sound = START.below(6);
            Column column = new Column().standing(besideFire).cell(besideFire.north(), TartarusCells.Cell.BURNING)
                    .standing(sound);

            assertEquals(Optional.of(sound), TartarusPlacement.nearest(column, START, REACH, NETHER));
        }
    }
}
