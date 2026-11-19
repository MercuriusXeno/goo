package com.mercuriusxeno.goo.client.overlay;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A Dowsing cell is a lens onto ore: a gem or metal ore is a candidate to
 * draw where any part of it lies behind the cell's goo from the eye, as far
 * past the goo as its patch is deep, in blocks. The cell lies on the
 * block under it, its goo standing in the cell at the origin, and the eye
 * looks down on it.
 * astral-world-goo-is-dowsing-an-x-ray-of-ore
 * dowsing-shows-ore-one-block-deep-per-layer
 * dowsing-goo-is-a-window-onto-the-ore-behind-it
 */
class DowsingLensTest {

    private static final TagKey<Block> GEM_ORES = oreTag("gem_ores");
    private static final TagKey<Block> METAL_ORES = oreTag("metal_ores");
    /** The cell the goo stands in. */
    private static final BlockPos CELL = BlockPos.ZERO;
    /** The eye, five blocks over the cell's middle, looking down through it. */
    private static final Vec3 EYE_OVER_THE_CELL = new Vec3(0.5, 5, 0.5);
    private static final int ONE_LAYER = 1;
    private static final int WHOLE_BLOCK = 15;

    private final BlockState stone = mock(BlockState.class);

    @Nested
    class Reach {

        @ParameterizedTest
        @ValueSource(ints = {ONE_LAYER, WHOLE_BLOCK})
        void anOreAsManyBlocksPastTheGooAsItsLayersShowsAndTheNextDoesNot(int layers) {
            BlockPos atTheReach = CELL.below(layers);
            BlockPos pastTheReach = CELL.below(layers + 1);
            Function<BlockPos, BlockState> blocks = lookup(Map.of(atTheReach, gemOre(), pastTheReach, gemOre()));

            assertEquals(Set.of(atTheReach), DowsingLens.oreShown(EYE_OVER_THE_CELL, gooOf(layers), layers, blocks));
        }
    }

    @Nested
    class Line {

        private static final int LAYERS = 4;

        @Test
        void ofTwoOresInReachTheOneBehindTheGooShows() {
            BlockPos behind = CELL.below();
            BlockPos beside = CELL.below().east(2);
            Function<BlockPos, BlockState> blocks = lookup(Map.of(behind, gemOre(), beside, gemOre()));

            assertEquals(Set.of(behind), DowsingLens.oreShown(EYE_OVER_THE_CELL, gooOf(LAYERS), LAYERS, blocks));
        }
    }

    /**
     * An ore any part of which lies behind the goo is a candidate, so a
     * turn of the camera never pops a whole ore block in or out; the window
     * cut at the goo's edge is the draw's, not the lens's.
     * dowsing-goo-is-a-window-onto-the-ore-behind-it
     */
    @Nested
    class Candidates {

        @Test
        void anOreWhoseMiddleIsBesideTheGooButACornerIsBehindItIsACandidate() {
            BlockPos cornerBehind = CELL.below().east();
            Function<BlockPos, BlockState> blocks = lookup(Map.of(cornerBehind, gemOre()));

            assertEquals(Set.of(cornerBehind),
                    DowsingLens.oreShown(EYE_OVER_THE_CELL, gooOf(ONE_LAYER), ONE_LAYER, blocks));
        }

        @Test
        void anOreNoLineThroughTheGooTouchesIsNoCandidate() {
            int layers = 4;
            BlockPos aside = CELL.below().east(3);
            Function<BlockPos, BlockState> blocks = lookup(Map.of(aside, gemOre()));

            assertEquals(Set.of(), DowsingLens.oreShown(EYE_OVER_THE_CELL, gooOf(layers), layers, blocks));
        }
    }

    /**
     * One connected patch of Dowsing goo shows ore as deep as its thickest
     * cell, every cell of it; two cells are one patch where they share a
     * face, and patches that do not touch each keep their own depth.
     * dowsing-patch-shows-ore-as-deep-as-its-thickest-cell
     * dowsing-shows-ore-one-block-deep-per-layer
     */
    @Nested
    class Patches {

        /** West of the cells at the thin goo's height, looking east along their row, so an ore down the row lies behind both. */
        private static final Vec3 EYE_WEST_ALONG_THE_ROW = new Vec3(-20, 0.03, 0.5);

        @Test
        void aOneLayerCellBesideAFifteenLayerCellShowsFifteenBlocksDeepAndNoDeeper() {
            Map<BlockPos, Integer> layers = Map.of(CELL, ONE_LAYER, CELL.east(), WHOLE_BLOCK);
            BlockPos atTheReach = CELL.below(WHOLE_BLOCK);
            BlockPos pastTheReach = CELL.below(WHOLE_BLOCK + 1);
            Function<BlockPos, BlockState> blocks = lookup(Map.of(atTheReach, gemOre(), pastTheReach, gemOre()));

            int depth = DowsingLens.patchDepths(layers.keySet(), pos -> layers.getOrDefault(pos, 0)).get(CELL);

            assertEquals(Set.of(atTheReach), DowsingLens.oreShown(EYE_OVER_THE_CELL, gooOf(ONE_LAYER), depth, blocks));
        }

        @Test
        void aOneLayerCellJoinedToAFifteenLayerCellThroughOneLayerCellsTakesFifteen() {
            Map<BlockPos, Integer> layers = new java.util.HashMap<>();
            for (int east = 0; east < 5; east++) {
                layers.put(CELL.east(east), ONE_LAYER);
            }
            layers.put(CELL.east(5), WHOLE_BLOCK);

            assertEquals(WHOLE_BLOCK, DowsingLens.patchDepths(Set.of(CELL), pos -> layers.getOrDefault(pos, 0))
                    .get(CELL));
        }

        @Test
        void aOneLayerPatchApartFromAFifteenLayerPatchShowsNoOreTenBlocksPast() {
            BlockPos thick = CELL.east(2);
            Map<BlockPos, Integer> layers = Map.of(CELL, ONE_LAYER, thick, WHOLE_BLOCK);
            BlockPos tenPastTheThickGoo = thick.east(11);
            Function<BlockPos, BlockState> blocks = lookup(Map.of(tenPastTheThickGoo, gemOre()));
            Map<BlockPos, Integer> depths = DowsingLens.patchDepths(layers.keySet(),
                    pos -> layers.getOrDefault(pos, 0));

            Set<BlockPos> thinShows = DowsingLens.oreShown(EYE_WEST_ALONG_THE_ROW, gooOf(ONE_LAYER), depths.get(CELL),
                    blocks);
            Set<BlockPos> thickShows = DowsingLens.oreShown(EYE_WEST_ALONG_THE_ROW, gooOf(WHOLE_BLOCK).move(2, 0, 0),
                    depths.get(thick), blocks);

            assertEquals(List.of(Set.of(tenPastTheThickGoo), Set.of()), List.of(thickShows, thinShows));
        }
    }

    @Nested
    class Ores {

        private static final int LAYERS = 3;

        @Test
        void gemAndMetalOresShowAndStoneDoesNot() {
            BlockPos gem = CELL.below(1);
            BlockPos metal = CELL.below(2);
            BlockPos plain = CELL.below(3);
            BlockState metalOre = mock(BlockState.class);
            when(metalOre.is(METAL_ORES)).thenReturn(true);
            Function<BlockPos, BlockState> blocks = lookup(Map.of(gem, gemOre(), metal, metalOre, plain, stone));

            assertEquals(Set.of(gem, metal), DowsingLens.oreShown(EYE_OVER_THE_CELL, gooOf(LAYERS), LAYERS, blocks));
        }
    }

    private static BlockState gemOre() {
        BlockState ore = mock(BlockState.class);
        when(ore.is(GEM_ORES)).thenReturn(true);
        return ore;
    }

    /** The lookup answering the given blocks, and stone everywhere else. */
    private Function<BlockPos, BlockState> lookup(Map<BlockPos, BlockState> placed) {
        return pos -> placed.getOrDefault(pos, stone);
    }

    /** The goo of a cell at the origin holding the layers, each a fifteenth of a block. */
    private static AABB gooOf(int layers) {
        return new AABB(0, 0, 0, 1, layers / 15.0, 1);
    }

    private static TagKey<Block> oreTag(String path) {
        return TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("goo", path));
    }
}
