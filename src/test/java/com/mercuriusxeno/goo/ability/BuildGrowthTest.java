package com.mercuriusxeno.goo.ability;

import com.mercuriusxeno.goo.data.GooFraction;
import com.mercuriusxeno.goo.data.GooRemainders;
import com.mercuriusxeno.goo.data.RemainderLedger;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A Build's plan grows stone into the open cells above each footprint cell, priced at the ability's per-block price
 * for each block grown (decisions spire-grows-stone-out-of-the-top,
 * spire-cost-field-prices-each-cubic-meter-of-stone-at-mundane-block).
 */
class BuildGrowthTest {

    private static final GooFraction PER_BLOCK = GooFraction.of(7);
    private static final BlockPos CORNER = new BlockPos(0, 64, 0);
    private static final BlockPos OPPOSITE = new BlockPos(3, 64, 0);
    /** The footprint cell whose column holds a solid block two above the ground. */
    private static final BlockPos BLOCKED_GROUND = new BlockPos(2, 64, 0);
    private static final int OPEN_COLUMNS = 3;

    private final BlockGetter blocks = mock(BlockGetter.class);

    @BeforeEach
    void standTheGround() {
        BlockState open = mock(BlockState.class);
        when(open.canBeReplaced()).thenReturn(true);
        BlockState solid = mock(BlockState.class);
        when(solid.canBeReplaced()).thenReturn(false);
        when(blocks.getBlockState(any(BlockPos.class))).thenAnswer(call -> {
            BlockPos pos = call.getArgument(0);
            boolean ground = pos.getY() <= CORNER.getY();
            return ground || pos.equals(BLOCKED_GROUND.above(2)) ? solid : open;
        });
    }

    private BuildFootprint footprintAt(int rise) {
        return new BuildFootprint(CORNER, OPPOSITE, rise);
    }

    @Nested
    class Plan {

        @Test
        void growsOnlyTheColumnsOpenThroughTheRise() {
            BuildGrowth growth = BuildGrowth.plan(blocks, footprintAt(3), PER_BLOCK);
            assertEquals(List.of(new BlockPos(0, 64, 0), new BlockPos(1, 64, 0), new BlockPos(3, 64, 0)),
                    growth.columns().stream().map(BuildColumn::ground).toList());
        }

        @ParameterizedTest
        @ValueSource(longs = {7, 12})
        void costsThePerBlockPriceForEachBlockGrown(long perBlock) {
            int rise = 3;
            BuildGrowth growth = BuildGrowth.plan(blocks, footprintAt(rise), GooFraction.of(perBlock));
            assertEquals(GooFraction.of(perBlock * OPEN_COLUMNS * rise), growth.exactCost());
        }
    }

    /**
     * Each column reaches down as far as it rises, stopping at bedrock, and lifts what it reached by the rise on stone.
     * decision build-reaches-down-as-far-as-it-rises-and-lifts-the-ground-on-stone
     */
    @Nested
    class Lift {

        private static final int RISE = 3;
        private static final BlockPos FAR_CORNER = new BlockPos(2, 64, 2);
        /** The column whose bedrock lies two blocks under its ground cell. */
        private static final BlockPos SHALLOW_GROUND = new BlockPos(5, 64, 5);

        private final BlockGetter ground = mock(BlockGetter.class);

        @BeforeEach
        void layTheGroundOverBedrock() {
            BlockState open = mock(BlockState.class);
            when(open.canBeReplaced()).thenReturn(true);
            BlockState dirt = mock(BlockState.class);
            BlockState bedrock = mock(BlockState.class);
            when(bedrock.getDestroySpeed(any(), any())).thenReturn(-1f);
            when(ground.getMinY()).thenReturn(-64);
            when(ground.getBlockState(any(BlockPos.class))).thenAnswer(call -> {
                BlockPos pos = call.getArgument(0);
                if (pos.equals(SHALLOW_GROUND.below(2))) {
                    return bedrock;
                }
                return pos.getY() <= CORNER.getY() ? dirt : open;
            });
        }

        @Test
        void aThreeByThreeRisingThreeLiftsItsTwentySevenBlocksByThreeOnTwentySevenStone() {
            BuildGrowth growth = BuildGrowth.plan(ground, new BuildFootprint(CORNER, FAR_CORNER, RISE), PER_BLOCK);

            List<BlockPos> lifted = growth.columns().stream().flatMap(column -> column.liftedCells().stream()).toList();
            List<BlockPos> stone = growth.columns().stream().flatMap(column -> column.stoneCells().stream()).toList();
            List<BlockPos> reached = BlockPos.betweenClosedStream(new BlockPos(0, 62, 0), FAR_CORNER)
                    .map(BlockPos::immutable).toList();
            assertEquals(Set.copyOf(reached), Set.copyOf(lifted));
            assertEquals(Set.copyOf(reached), Set.copyOf(stone));
            assertEquals(27, stone.size());
            growth.columns().forEach(column -> column.liftedCells()
                    .forEach(cell -> assertEquals(cell.above(RISE), column.liftedTo(cell))));
        }

        @Test
        void aColumnOverBedrockTwoDownLiftsItsTwoBlocksByThreeOnThreeStone() {
            BuildColumn column = BuildGrowth.plan(ground, new BuildFootprint(SHALLOW_GROUND, SHALLOW_GROUND, RISE),
                    PER_BLOCK).columns().getFirst();

            assertEquals(List.of(SHALLOW_GROUND.below(), SHALLOW_GROUND), column.liftedCells());
            assertEquals(List.of(SHALLOW_GROUND.above(2), SHALLOW_GROUND.above(3)),
                    column.liftedCells().stream().map(column::liftedTo).toList());
            assertEquals(List.of(SHALLOW_GROUND.below(), SHALLOW_GROUND, SHALLOW_GROUND.above()), column.stoneCells());
        }
    }

    /**
     * A Build charges the exact per-block price through the player's ledger.
     * decision melt-and-fabricate-move-the-exact-value-through-a-remainder-buffer
     */
    @Nested
    class Charge {

        private static final int HOLDINGS = 1000;
        private static final GooFraction SIXTY_FOUR_THIRDS = GooFraction.of(64, 3);

        private BuildGrowth oneColumnAt(int rise) {
            return BuildGrowth.plan(blocks, new BuildFootprint(CORNER, CORNER, rise), SIXTY_FOUR_THIRDS);
        }

        @Test
        void oneBlockChargesTwentyTwoAndBanksTwoThirds() {
            RemainderLedger ledger = new RemainderLedger();

            assertEquals(OptionalInt.of(22), oneColumnAt(1).chargeThrough(ledger, GooTypes.ROCK, HOLDINGS));
            assertEquals(GooFraction.of(2, 3), ledger.remainders().of(GooTypes.ROCK));
        }

        @Test
        void threeBlocksChargeSixtyFourAndLeaveNoRemainder() {
            RemainderLedger ledger = new RemainderLedger();

            assertEquals(OptionalInt.of(64), oneColumnAt(3).chargeThrough(ledger, GooTypes.ROCK, HOLDINGS));
            assertTrue(ledger.remainders().isEmpty());
        }

        /**
         * The remainder pays first, so holdings of 21 beside a remainder of 2/3 afford one
         * block worth 64/3, are charged 21 and keep 1/3.
         * decision melt-and-fabricate-move-the-exact-value-through-a-remainder-buffer
         */
        @Test
        void theRemainderPaysFirstSoTwentyOneAffordsOneBlock() {
            int holdings = 21;
            RemainderLedger ledger = new RemainderLedger(GooRemainders.EMPTY.with(GooTypes.ROCK, GooFraction.of(2, 3)));

            Optional<BuildGrowth> growth = BuildGrowth.affordable(blocks, new BuildFootprint(CORNER, CORNER, 1),
                    SIXTY_FOUR_THIRDS, GooTypes.ROCK, ledger.remainders(), holdings);

            assertEquals(OptionalInt.of(holdings), growth.orElseThrow().chargeThrough(ledger, GooTypes.ROCK, holdings));
            assertEquals(GooFraction.of(1, 3), ledger.remainders().of(GooTypes.ROCK));
        }
    }

    @Nested
    class Affordable {

        private static final int PER_BLOCK_MB = 7;

        @Test
        void lowersTheRiseToWhatTheHoldingsPayFor() {
            int threeBlocksPerOpenColumn = PER_BLOCK_MB * OPEN_COLUMNS * 3;
            Optional<BuildGrowth> growth = BuildGrowth.affordable(blocks, footprintAt(8), PER_BLOCK,
                    GooTypes.ROCK, GooRemainders.EMPTY, threeBlocksPerOpenColumn);
            assertEquals(3, growth.orElseThrow().footprint().rise());
        }

        @Test
        void growsNothingWhereTheHoldingsPayForNoBlock() {
            Optional<BuildGrowth> growth = BuildGrowth.affordable(blocks, footprintAt(8), PER_BLOCK, GooTypes.ROCK,
                    GooRemainders.EMPTY, PER_BLOCK_MB - 1);
            assertTrue(growth.isEmpty());
        }
    }
}
