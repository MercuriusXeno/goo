package com.mercuriusxeno.goo.client.ability;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * One echo draw's work, counted at the seam it reads the world through, and
 * the quads and tints it submits, recorded at a fake vertex consumer, so a
 * cut to its cost is proven headless and proven to draw what it drew
 * (decision diagnose-sculkers-lag-and-prove-it-improved-significantly).
 */
class SculkerEchoRendererTest {

    private static final int LIT_BLOCKS = 64;
    private static final float STRENGTH = 0.5f;
    /** The lit row's blocks standing in view, where a test puts the rest out of it. */
    private static final int IN_VIEW = 32;

    @Test
    void oneEchoDrawCollectsOneModelForEveryLitBlock() {
        CountingWorld world = new CountingWorld(mock(BlockState.class));
        SculkerEchoRenderer.drawLit(world, new EchoQuads(), new PoseStack(), mock(VertexConsumer.class), Vec3.ZERO,
                litRow());
        assertEquals(LIT_BLOCKS, world.collected, "block models one echo draw collects over the lit set");
    }

    @Test
    void aSecondDrawOverAnUnchangedLitSetCollectsNoModelAgain() {
        CountingWorld world = new CountingWorld(mock(BlockState.class));
        EchoQuads kept = new EchoQuads();
        SculkerEchoRenderer.drawLit(world, kept, new PoseStack(), mock(VertexConsumer.class), Vec3.ZERO, litRow());
        assertEquals(LIT_BLOCKS, world.collected, "block models the first draw collects");
        SculkerEchoRenderer.drawLit(world, kept, new PoseStack(), mock(VertexConsumer.class), Vec3.ZERO, litRow());
        assertEquals(LIT_BLOCKS, world.collected, "block models collected once the second draw has run");
    }

    @Test
    void aBlockChangeBesideALitBlockCollectsItsModelAgain() {
        CountingWorld world = new CountingWorld(mock(BlockState.class));
        EchoQuads kept = new EchoQuads();
        SculkerEchoRenderer.drawLit(world, kept, new PoseStack(), mock(VertexConsumer.class), Vec3.ZERO, litRow());
        kept.forgetAround(new BlockPos(10, 1, 0));
        SculkerEchoRenderer.drawLit(world, kept, new PoseStack(), mock(VertexConsumer.class), Vec3.ZERO, litRow());
        assertEquals(LIT_BLOCKS + 1, world.collected, "the lit block under the changed one collects again");
    }

    /**
     * The lit set a frame draws holds the lit blocks in view alone, and the
     * blocks of a section out of view are passed over unasked.
     */
    @Nested
    class InView {

        private static final long NOW = 100L;
        private static final double REACH = 160;
        private final SculkerEchoRenderer.Sight sight = new SculkerEchoRenderer.Sight(Vec3.ZERO, NOW, REACH, 1f);

        @Test
        void aSectionOutOfViewIsPassedOverWithoutAskingOfItsBlocks() {
            CountingView view = new CountingView(SculkerEchoRenderer.Sighting.OUT, pos -> true);
            assertEquals(Map.of(), SculkerEchoRenderer.litInView(litSection(), view, sight), "lit blocks in view");
            assertEquals(0, view.asked, "blocks asked of the view");
        }

        @Test
        void aSectionWhollyInViewLightsEveryBlockWithoutAskingOfThem() {
            CountingView view = new CountingView(SculkerEchoRenderer.Sighting.WHOLLY, pos -> false);
            assertEquals(LIT_BLOCKS / 4, SculkerEchoRenderer.litInView(litSection(), view, sight).size(),
                    "lit blocks in view");
            assertEquals(0, view.asked, "blocks asked of the view");
        }

        @Test
        void aSectionTheViewCutsThroughLightsTheBlocksTheViewHolds() {
            CountingView view = new CountingView(SculkerEchoRenderer.Sighting.PARTLY, pos -> pos.getX() < IN_VIEW / 4);
            assertEquals(IN_VIEW / 4, SculkerEchoRenderer.litInView(litSection(), view, sight).size(),
                    "lit blocks in view");
        }

        private Map<Long, Map<BlockPos, SculkerEchoes.Lit>> litSection() {
            Map<BlockPos, SculkerEchoes.Lit> faces = new HashMap<>();
            for (int block = 0; block < LIT_BLOCKS / 4; block++) {
                faces.put(new BlockPos(block, 0, 0), new SculkerEchoes.Lit(NOW, 1f));
            }
            return Map.of(0L, faces);
        }
    }

    /**
     * A view answering every section one way and each block by a test,
     * counting the blocks asked of it.
     */
    private static final class CountingView implements SculkerEchoRenderer.EchoView {

        private final SculkerEchoRenderer.Sighting sighting;
        private final Predicate<BlockPos> holds;
        private int asked;

        CountingView(SculkerEchoRenderer.Sighting sighting, Predicate<BlockPos> holds) {
            this.sighting = sighting;
            this.holds = holds;
        }

        @Override
        public SculkerEchoRenderer.Sighting section(long section) {
            return sighting;
        }

        @Override
        public boolean holds(BlockPos pos) {
            asked++;
            return holds.test(pos);
        }
    }

    private static Map<BlockPos, Float> litRow() {
        Map<BlockPos, Float> lit = new LinkedHashMap<>();
        for (int block = 0; block < LIT_BLOCKS; block++) {
            lit.put(new BlockPos(block, 0, 0), STRENGTH);
        }
        return lit;
    }

    /**
     * A draw over a fixed lit set, camera and strengths submits the same
     * quads at the same tints and poses as the parent commit of task
     * echo-rendering-draws-as-fast-as-it-can drew, captured under the test resources.
     */
    @Nested
    class SameQuads {

        @Test
        void aDrawSubmitsTheQuadsAndTintsItDidBeforeTheCut() throws IOException {
            assertEquals(capture(), recordedDraw(), "quads, tints and poses one echo draw submits");
        }

        private List<String> capture() throws IOException {
            try (InputStream stream = SculkerEchoRendererTest.class.getResourceAsStream("sculker-echo-quads.txt")) {
                assertNotNull(stream, "capture sculker-echo-quads.txt stands on the classpath");
                return new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).lines().toList();
            }
        }
    }

    /**
     * Draws a varied lit cube once through a world of two-part blocks with
     * air among them and sides open and shut, recording each quad submitted.
     *
     * @return each quad's label, tint and pose translation, in submit order
     */
    static List<String> recordedDraw() {
        PartedWorld world = new PartedWorld();
        List<String> submitted = new ArrayList<>();
        VertexConsumer consumer = mock(VertexConsumer.class);
        doAnswer(call -> {
            PoseStack.Pose pose = call.getArgument(0);
            Vector3f moved = pose.pose().getTranslation(new Vector3f());
            QuadInstance look = call.getArgument(2);
            submitted.add(String.format(Locale.ROOT, "%s %08x %.3f %.3f %.3f", world.labelOf(call.getArgument(1)),
                    look.getColor(0), moved.x(), moved.y(), moved.z()));
            return null;
        }).when(consumer).putBakedQuad(any(), any(), any());
        SculkerEchoRenderer.drawLit(world, new EchoQuads(), new PoseStack(), consumer, new Vec3(1.25, 2.5, -0.75),
                litCube());
        return submitted;
    }

    private static Map<BlockPos, Float> litCube() {
        Map<BlockPos, Float> lit = new LinkedHashMap<>();
        int side = 4;
        for (int x = 0; x < side; x++) {
            for (int y = 0; y < side; y++) {
                for (int z = 0; z < side; z++) {
                    lit.put(new BlockPos(x, y, z), 0.1f + 0.8f * lit.size() / (side * side * side));
                }
            }
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

    /**
     * A world whose every fifth block is air, whose blocks pass sound in a
     * fixed scatter, and whose every block's model holds two parts, each
     * with one quad a side and one facing no side, every quad labelled.
     */
    private static final class PartedWorld implements SculkerEchoRenderer.EchoWorld {

        private static final int AIR_EVERY = 5;
        private static final int PARTS = 2;

        private final BlockState air = mock(BlockState.class);
        private final BlockState solid = mock(BlockState.class);
        private final Map<BakedQuad, String> labels = new HashMap<>();
        private final List<BlockStateModelPart> parts = new ArrayList<>();

        PartedWorld() {
            when(air.isAir()).thenReturn(true);
            for (int part = 0; part < PARTS; part++) {
                BlockStateModelPart modelPart = mock(BlockStateModelPart.class);
                for (Direction side : Direction.values()) {
                    when(modelPart.getQuads(side)).thenReturn(List.of(labelled("part" + part + ":" + side)));
                }
                when(modelPart.getQuads(null)).thenReturn(List.of(labelled("part" + part + ":none")));
                parts.add(modelPart);
            }
        }

        private BakedQuad labelled(String label) {
            BakedQuad quad = mock(BakedQuad.class);
            labels.put(quad, label);
            return quad;
        }

        String labelOf(BakedQuad quad) {
            return labels.get(quad);
        }

        @Override
        public BlockState stateAt(BlockPos pos) {
            return Math.floorMod(pos.getX() + pos.getY() * 3 + pos.getZ() * 7, AIR_EVERY) == 0 ? air : solid;
        }

        @Override
        public void collectParts(BlockPos pos, BlockState state, List<BlockStateModelPart> collected) {
            collected.addAll(parts);
        }

        @Override
        public boolean passesSound(BlockPos pos) {
            return (pos.getX() * 7 + pos.getY() * 13 + pos.getZ() * 31 & 3) != 0;
        }
    }
}
