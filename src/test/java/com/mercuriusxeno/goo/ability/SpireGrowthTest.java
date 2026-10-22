package com.mercuriusxeno.goo.ability;

import com.mercuriusxeno.goo.data.GooFraction;
import com.mercuriusxeno.goo.data.GooRemainders;
import com.mercuriusxeno.goo.data.GooValue;
import com.mercuriusxeno.goo.data.IGooValueLookup;
import com.mercuriusxeno.goo.data.RemainderLedger;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

import static com.mercuriusxeno.goo.data.TestRecipeBuilder.fraction;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** A Spire's plan grows stone into the open cells above each footprint cell, priced at stone's rock value per block (decision spire-grows-stone-out-of-the-top). */
class SpireGrowthTest {

    private static final int STONE_ROCK_VALUE = 7;
    private static final BlockPos CORNER = new BlockPos(0, 64, 0);
    private static final BlockPos OPPOSITE = new BlockPos(3, 64, 0);
    /** The footprint cell whose column holds a solid block two above the ground. */
    private static final BlockPos BLOCKED_GROUND = new BlockPos(2, 64, 0);

    private final BlockGetter blocks = mock(BlockGetter.class);
    private final IGooValueLookup values = mock(IGooValueLookup.class);

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
        when(values.lookup(Identifier.withDefaultNamespace("stone")))
                .thenReturn(new GooValue(Map.of(GooTypes.ROCK, STONE_ROCK_VALUE)));
    }

    private SpireFootprint footprintAt(int rise) {
        return new SpireFootprint(CORNER, OPPOSITE, rise);
    }

    @Nested
    class Plan {

        @Test
        void growsOnlyTheColumnsOpenThroughTheRise() {
            SpireGrowth growth = SpireGrowth.plan(blocks, footprintAt(3), values, GooTypes.ROCK);
            assertEquals(List.of(new BlockPos(0, 64, 0), new BlockPos(1, 64, 0), new BlockPos(3, 64, 0)),
                    growth.columns());
        }

        @Test
        void costsStoneRockValuePerStoneBlockGrown() {
            SpireGrowth growth = SpireGrowth.plan(blocks, footprintAt(3), values, GooTypes.ROCK);
            assertEquals(STONE_ROCK_VALUE * 3 * 3, growth.cost());
        }
    }

    /**
     * A Spire charges stone's exact value per block through the player's ledger.
     * decision melt-and-fabricate-move-the-exact-value-through-a-remainder-buffer
     */
    @Nested
    class Charge {

        private static final int HOLDINGS = 1000;

        @BeforeEach
        void priceStoneAtSixtyFourThirds() {
            when(values.lookup(Identifier.withDefaultNamespace("stone"))).thenReturn(fraction(GooTypes.ROCK, 64, 3));
        }

        private SpireGrowth oneColumnAt(int rise) {
            return SpireGrowth.plan(blocks, new SpireFootprint(CORNER, CORNER, rise), values, GooTypes.ROCK);
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
                    values, GooTypes.ROCK, ledger.remainders(), holdings);

            assertEquals(OptionalInt.of(holdings), growth.orElseThrow().chargeThrough(ledger, GooTypes.ROCK, holdings));
            assertEquals(GooFraction.of(1, 3), ledger.remainders().of(GooTypes.ROCK));
        }
    }

    @Nested
    class Affordable {

        @Test
        void lowersTheRiseToWhatTheHoldingsPayFor() {
            int threeBlocksPerOpenColumn = STONE_ROCK_VALUE * 3 * 3;
            Optional<SpireGrowth> growth = SpireGrowth.affordable(blocks, footprintAt(8), values,
                    GooTypes.ROCK, GooRemainders.EMPTY, threeBlocksPerOpenColumn);
            assertEquals(3, growth.orElseThrow().footprint().rise());
        }

        @Test
        void growsNothingWhereTheHoldingsPayForNoBlock() {
            Optional<SpireGrowth> growth = SpireGrowth.affordable(blocks, footprintAt(8), values, GooTypes.ROCK,
                    GooRemainders.EMPTY, STONE_ROCK_VALUE - 1);
            assertTrue(growth.isEmpty());
        }
    }
}
