package com.mercuriusxeno.goo.ability;

import com.mercuriusxeno.goo.data.GooFraction;
import com.mercuriusxeno.goo.data.GooRemainders;
import com.mercuriusxeno.goo.data.RemainderLedger;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
     * Each column on ground solid through its rise reaches down as far as it rises, stopping at bedrock, and lifts what
     * it reached by the rise on stone, its deepest block pushed from behind.
     * decision build-reaches-down-as-far-as-it-rises-and-lifts-the-ground-on-stone
     * decision build-pushes-from-behind-at-full-depth-with-no-air
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
     * A footprint on a wall reaches into the wall and pushes what it reached out along the wall's normal on stone.
     * decision build-goes-sideways
     */
    @Nested
    class Sideways {

        private static final int RISE = 2;
        /** The wall's face layer, its blocks filling every cell from here south. */
        private static final int WALL_Z = 10;
        private static final BlockPos WALL_CORNER = new BlockPos(0, 64, WALL_Z);
        private static final BlockPos WALL_OPPOSITE = new BlockPos(1, 65, WALL_Z);

        private final BlockGetter wall = mock(BlockGetter.class);

        @BeforeEach
        void standTheWall() {
            BlockState open = mock(BlockState.class);
            when(open.canBeReplaced()).thenReturn(true);
            BlockState brick = mock(BlockState.class);
            when(wall.getMinY()).thenReturn(-64);
            when(wall.getBlockState(any(BlockPos.class)))
                    .thenAnswer(call -> call.<BlockPos>getArgument(0).getZ() >= WALL_Z ? brick : open);
        }

        @Test
        void aRiseOfTwoOnANorthFacingWallPushesTheReachedBlocksTwoNorthWithStoneBehindThem() {
            BuildGrowth growth = BuildGrowth.plan(wall,
                    new BuildFootprint(WALL_CORNER, WALL_OPPOSITE, RISE, Direction.NORTH), PER_BLOCK);

            assertEquals(4, growth.columns().size());
            for (BuildColumn column : growth.columns()) {
                BlockPos face = column.ground();
                BlockPos behind = face.south();
                assertEquals(List.of(behind, face), column.liftedCells());
                assertEquals(List.of(behind.north(RISE), face.north(RISE)),
                        column.liftedCells().stream().map(column::liftedTo).toList());
                assertEquals(List.of(behind, face), column.stoneCells());
            }
        }
    }

    /**
     * A column stops at the first block with a replaceable cell behind it: that block stays, the blocks in front of it
     * lift, and the stone grows from its near face.
     * decision build-extends-the-first-block-with-air-behind-it
     * decision air-behind-a-block-is-anything-stone-can-replace
     */
    @Nested
    class AirBacked {

        private static final int RISE = 3;
        /** The wall's north face layer, its blocks filling the layers south of it the test names. */
        private static final int WALL_Z = 10;
        private static final BlockPos FACE = new BlockPos(0, 64, WALL_Z);

        /** What fills the cell behind the wall, each answering canBeReplaced true. */
        enum Backing { AIR, WATER }

        private BuildColumn planTheColumn(int thickness, Backing backing) {
            BlockState air = mock(BlockState.class);
            when(air.canBeReplaced()).thenReturn(true);
            when(air.isAir()).thenReturn(true);
            BlockState water = mock(BlockState.class);
            when(water.canBeReplaced()).thenReturn(true);
            when(water.isAir()).thenReturn(false);
            BlockState brick = mock(BlockState.class);
            BlockGetter wall = mock(BlockGetter.class);
            when(wall.getMinY()).thenReturn(-64);
            when(wall.getBlockState(any(BlockPos.class))).thenAnswer(call -> {
                int z = call.<BlockPos>getArgument(0).getZ();
                if (z >= WALL_Z && z < WALL_Z + thickness) {
                    return brick;
                }
                return z >= WALL_Z + thickness && backing == Backing.WATER ? water : air;
            });
            return BuildGrowth.plan(wall, new BuildFootprint(FACE, FACE, RISE, Direction.NORTH), PER_BLOCK)
                    .columns().getFirst();
        }

        @ParameterizedTest
        @EnumSource(Backing.class)
        void aOneThickWallLiftsNothingAndGrowsItsStoneInFrontOfTheFace(Backing backing) {
            BuildColumn column = planTheColumn(1, backing);

            assertEquals(List.of(), column.liftedCells());
            assertEquals(List.of(FACE.north(), FACE.north(2), FACE.north(3)), column.stoneCells());
        }

        @ParameterizedTest
        @EnumSource(Backing.class)
        void aTwoThickWallLiftsTheFaceAloneAndGrowsItsStoneFromTheSecondBlocksNearFace(Backing backing) {
            BuildColumn column = planTheColumn(2, backing);

            assertEquals(List.of(FACE), column.liftedCells());
            assertEquals(FACE.north(RISE), column.liftedTo(FACE));
            assertEquals(List.of(FACE, FACE.north(), FACE.north(2)), column.stoneCells());
        }
    }

    /**
     * A block a piston does not push anchors the column: it stays, the blocks in front of it lift and the stone grows
     * from its near face.
     * decision build-leaves-what-a-piston-cannot-push-and-grows-from-it
     */
    @Nested
    class Unpushable {

        private static final int RISE = 3;
        private static final TagKey<Block> ANCHORS =
                TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("goo", "build_anchors"));

        /** The kinds of block a piston leaves, each standing one under the ground cell. */
        enum Kind { NAMED_LIKE_OBSIDIAN, PUSH_REACTION_BLOCK, PUSH_REACTION_DESTROY, UNBREAKABLE }

        private BlockState unpushable(Kind kind) {
            BlockState state = mock(BlockState.class);
            switch (kind) {
                case NAMED_LIKE_OBSIDIAN -> when(state.is(ANCHORS)).thenReturn(true);
                case PUSH_REACTION_BLOCK -> when(state.getPistonPushReaction()).thenReturn(PushReaction.BLOCK);
                case PUSH_REACTION_DESTROY -> when(state.getPistonPushReaction()).thenReturn(PushReaction.DESTROY);
                case UNBREAKABLE -> when(state.getDestroySpeed(any(), any())).thenReturn(-1f);
            }
            return state;
        }

        @ParameterizedTest
        @EnumSource(Kind.class)
        void aBlockAPistonLeavesStaysAndTheStoneGrowsFromItsNearFace(Kind kind) {
            BlockState open = mock(BlockState.class);
            when(open.canBeReplaced()).thenReturn(true);
            BlockState dirt = mock(BlockState.class);
            BlockState anchor = unpushable(kind);
            BlockGetter ground = mock(BlockGetter.class);
            when(ground.getMinY()).thenReturn(-64);
            when(ground.getBlockState(any(BlockPos.class))).thenAnswer(call -> {
                BlockPos pos = call.getArgument(0);
                if (pos.equals(CORNER.below())) {
                    return anchor;
                }
                return pos.getY() <= CORNER.getY() ? dirt : open;
            });

            BuildColumn column = BuildGrowth.plan(ground, new BuildFootprint(CORNER, CORNER, RISE), PER_BLOCK)
                    .columns().getFirst();

            assertEquals(List.of(CORNER), column.liftedCells());
            assertEquals(List.of(CORNER, CORNER.above(), CORNER.above(2)), column.stoneCells());
        }
    }

    /**
     * A column whose walk meets a block entity fizzles before any cost; one lying past the walk leaves it growing.
     * decision column-meeting-a-block-entity-fizzles-at-no-cost
     */
    @Nested
    class BlockEntities {

        private static final int RISE = 3;
        private static final BlockPos FAR_GROUND = new BlockPos(2, 64, 0);
        /** The middle column, a chest the depth the test names under its ground cell. */
        private static final BlockPos CHEST_GROUND = new BlockPos(1, 64, 0);

        private BlockGetter groundWithAChestUnder(int depth) {
            BlockState open = mock(BlockState.class);
            when(open.canBeReplaced()).thenReturn(true);
            BlockState dirt = mock(BlockState.class);
            BlockState chest = mock(BlockState.class);
            when(chest.hasBlockEntity()).thenReturn(true);
            BlockGetter ground = mock(BlockGetter.class);
            when(ground.getMinY()).thenReturn(-64);
            when(ground.getBlockState(any(BlockPos.class))).thenAnswer(call -> {
                BlockPos pos = call.getArgument(0);
                if (pos.equals(CHEST_GROUND.below(depth))) {
                    return chest;
                }
                return pos.getY() <= CORNER.getY() ? dirt : open;
            });
            return ground;
        }

        @Test
        void aChestOneUnderAGroundCellFizzlesItsColumnAndItsCost() {
            BuildGrowth growth = BuildGrowth.plan(groundWithAChestUnder(1),
                    new BuildFootprint(CORNER, FAR_GROUND, RISE), PER_BLOCK);

            assertEquals(List.of(CORNER, FAR_GROUND), growth.columns().stream().map(BuildColumn::ground).toList());
            assertEquals(PER_BLOCK.times(2L * RISE), growth.exactCost());
        }

        @Test
        void aChestBehindTheDeepestPushedBlockLeavesTheColumnGrowing() {
            BuildGrowth growth = BuildGrowth.plan(groundWithAChestUnder(RISE),
                    new BuildFootprint(CHEST_GROUND, CHEST_GROUND, RISE), PER_BLOCK);

            BuildColumn column = growth.columns().getFirst();
            assertEquals(List.of(CHEST_GROUND.below(2), CHEST_GROUND.below(), CHEST_GROUND), column.liftedCells());
            assertEquals(PER_BLOCK.times(RISE), growth.exactCost());
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
