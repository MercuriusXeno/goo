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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A Spire's plan grows stone into the open cells above each footprint cell, priced at the ability's per-block price
 * for each block grown (decisions spire-grows-stone-out-of-the-top,
 * spire-cost-field-prices-each-cubic-meter-of-stone-at-mundane-block).
 */
class SpireGrowthTest {

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

    private SpireFootprint footprintAt(int rise) {
        return new SpireFootprint(CORNER, OPPOSITE, rise);
    }

    @Nested
    class Plan {

        @Test
        void growsOnlyTheColumnsOpenThroughTheRise() {
            SpireGrowth growth = SpireGrowth.plan(blocks, footprintAt(3), PER_BLOCK);
            assertEquals(List.of(new BlockPos(0, 64, 0), new BlockPos(1, 64, 0), new BlockPos(3, 64, 0)),
                    growth.columns());
        }

        @ParameterizedTest
        @ValueSource(longs = {7, 12})
        void costsThePerBlockPriceForEachBlockGrown(long perBlock) {
            int rise = 3;
            SpireGrowth growth = SpireGrowth.plan(blocks, footprintAt(rise), GooFraction.of(perBlock));
            assertEquals(GooFraction.of(perBlock * OPEN_COLUMNS * rise), growth.exactCost());
        }
    }

    /**
     * A Spire charges the exact per-block price through the player's ledger.
     * decision melt-and-fabricate-move-the-exact-value-through-a-remainder-buffer
     */
    @Nested
    class Charge {

        private static final int HOLDINGS = 1000;
        private static final GooFraction SIXTY_FOUR_THIRDS = GooFraction.of(64, 3);

        private SpireGrowth oneColumnAt(int rise) {
            return SpireGrowth.plan(blocks, new SpireFootprint(CORNER, CORNER, rise), SIXTY_FOUR_THIRDS);
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

            Optional<SpireGrowth> growth = SpireGrowth.affordable(blocks, new SpireFootprint(CORNER, CORNER, 1),
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
            Optional<SpireGrowth> growth = SpireGrowth.affordable(blocks, footprintAt(8), PER_BLOCK,
                    GooTypes.ROCK, GooRemainders.EMPTY, threeBlocksPerOpenColumn);
            assertEquals(3, growth.orElseThrow().footprint().rise());
        }

        @Test
        void growsNothingWhereTheHoldingsPayForNoBlock() {
            Optional<SpireGrowth> growth = SpireGrowth.affordable(blocks, footprintAt(8), PER_BLOCK, GooTypes.ROCK,
                    GooRemainders.EMPTY, PER_BLOCK_MB - 1);
            assertTrue(growth.isEmpty());
        }
    }
}
