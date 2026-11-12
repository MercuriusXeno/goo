package com.mercuriusxeno.goo.client.ability;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

/**
 * One echo draw's work, counted at the seam it reads the world through: the
 * block models it collects over a fixed lit set, so a cut to its cost is
 * proven headless (decision diagnose-sculkers-lag-and-prove-it-improved-significantly).
 */
class SculkerEchoRendererTest {

    private static final int LIT_BLOCKS = 64;
    private static final float STRENGTH = 0.5f;

    @Test
    void oneEchoDrawCollectsOneModelForEveryLitBlock() {
        CountingWorld world = new CountingWorld(mock(BlockState.class));
        SculkerEchoRenderer.drawLit(world, new PoseStack(), mock(VertexConsumer.class), Vec3.ZERO, litRow());
        assertEquals(LIT_BLOCKS, world.collected, "block models one echo draw collects over the lit set");
    }

    private static Map<BlockPos, Float> litRow() {
        Map<BlockPos, Float> lit = new LinkedHashMap<>();
        for (int block = 0; block < LIT_BLOCKS; block++) {
            lit.put(new BlockPos(block, 0, 0), STRENGTH);
        }
        return lit;
    }

    /** A world of one solid block everywhere, open to sound on every side, counting the models collected in it. */
    private static final class CountingWorld implements SculkerEchoRenderer.EchoWorld {

        private final BlockState solid;
        private int collected;

        CountingWorld(BlockState solid) {
            this.solid = solid;
        }

        @Override
        public BlockState stateAt(BlockPos pos) {
            return solid;
        }

        @Override
        public void collectParts(BlockPos pos, BlockState state, List<BlockStateModelPart> parts) {
            collected++;
        }

        @Override
        public boolean passesSound(BlockPos pos) {
            return true;
        }
    }
}
