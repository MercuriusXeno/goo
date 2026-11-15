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
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A Dowsing cell is a lens onto ore: a gem or metal ore shows where the line
 * from the eye to it passes through the cell's goo, as far past the goo as
 * the cell holds layers, in blocks. The cell lies on the block under it, its
 * goo standing in the cell at the origin, and the eye looks down on it.
 * astral-world-goo-is-dowsing-an-x-ray-of-ore
 * dowsing-shows-ore-one-block-deep-per-layer
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
