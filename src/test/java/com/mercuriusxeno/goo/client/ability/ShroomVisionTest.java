package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.data.FungusBlocks;
import com.mercuriusxeno.goo.data.GooBaseValuesPreview;
import com.mercuriusxeno.goo.data.GooValueTable;
import com.mercuriusxeno.goo.data.IGooValueLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

/**
 * Under Sculker the sense view draws sculk and shroom blocks with no echo, at any noise level, and leaves the rest
 * to the echoes: a fungus block, one whose block item's goo value holds sculk in the shipped base values, draws.
 * decision sculker-absorbs-sight-as-shroom-vision
 */
class ShroomVisionTest {

    private static final String BASE_VALUES = "/data/goo/goo_values/base_values.json";
    private static final BlockPos SCULK_AT = new BlockPos(1, 64, 0);
    private static final BlockPos MUSHROOM_BLOCK_AT = new BlockPos(2, 64, 0);
    private static final BlockPos STONE_AT = new BlockPos(3, 64, 0);
    private static final float PRESENCE = 1f;
    private static IGooValueLookup shipped;

    @BeforeAll
    static void loadShippedBaseValues() throws IOException {
        try (InputStream in = ShroomVisionTest.class.getResourceAsStream(BASE_VALUES)) {
            assertNotNull(in, BASE_VALUES);
            shipped = GooValueTable.ofEffectiveValues(GooBaseValuesPreview.load(
                    new String(in.readAllBytes(), StandardCharsets.UTF_8)).baseValues());
        }
    }

    @Test
    void sculkAndShroomBlocksDrawWithNoEchoAndStoneDoesNot() {
        BlockState sculk = mock(BlockState.class);
        BlockState mushroomBlock = mock(BlockState.class);
        BlockState stone = mock(BlockState.class);
        Map<BlockState, String> ids = Map.of(sculk, "sculk", mushroomBlock, "brown_mushroom_block", stone, "stone");
        Set<BlockState> fungus = FungusBlocks.containingSculk(ids.keySet(),
                state -> Identifier.withDefaultNamespace(ids.get(state)), shipped);
        Map<BlockPos, BlockState> nearby = Map.of(SCULK_AT, sculk, MUSHROOM_BLOCK_AT, mushroomBlock, STONE_AT, stone);

        Map<BlockPos, Float> drawn = ShroomVision.drawn(Map.of(), nearby, fungus::contains, PRESENCE);

        assertEquals(Set.of(SCULK_AT, MUSHROOM_BLOCK_AT), drawn.keySet());
    }
}
