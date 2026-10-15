package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A block drawn mingling over its position matches the block the world draws there:
 * the model picks the variant the world's seed for that position picks, and the faces
 * sit on the block's own faces, winning the depth test by bias rather than by a swell.
 */
class BlockMingleDrawTest {

    private static final BlockPos STONE = new BlockPos(3, 64, 2);
    private static final long WORLD_SEED = 1234L;

    @Test
    void theDrawnModelPicksTheVariantTheWorldPicksThere() {
        BlockState stone = mock(BlockState.class);
        when(stone.getSeed(STONE)).thenReturn(WORLD_SEED);

        assertEquals(WORLD_SEED, BlockMingleRenderer.modelSeed(stone, STONE));
    }

    @Test
    void theDrawnFacesWinTheDepthTestByBiasTowardTheCamera() {
        DepthStencilState depth = GooRenderTypes.BLOCK_MINGLE.getDepthStencilState();

        assertTrue(depth.depthBiasConstant() < 0f && depth.depthBiasScaleFactor() < 0f,
            "depth bias " + depth.depthBiasScaleFactor() + ", " + depth.depthBiasConstant());
    }
}
