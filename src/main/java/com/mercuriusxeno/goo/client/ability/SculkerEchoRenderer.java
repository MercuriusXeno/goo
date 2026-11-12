package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.client.overlay.FungusXray;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.data.AtlasIds;
import net.minecraft.util.ARGB;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Draws what Sculker's echoes lit: each block an echo touched draws its own
 * model and texture, tinted sculk teal and glowing, on every side open to
 * air, through the x-ray pipeline Fungal Sight draws through so an echo
 * shows through the stone the diver swims in. A block draws as strongly as
 * its strongest lit side, fading as its echo fades and the farther it stands.
 * decision sculker-sight-persists-where-echoes-have-touched
 */
public final class SculkerEchoRenderer {

    /** The opacity a lit block draws at beside the player, as its echo is fresh. */
    private static final float MOST_OPACITY = 0.9f;
    /** A lit block this faint is not drawn at all. */
    private static final float FAINTEST = 0.02f;
    private static final int OPAQUE = 255;
    /** The last block along a chunk section's side, counted from its first. */
    private static final int SECTION_LAST = SectionPos.SECTION_SIZE - 1;
    /** What the frustum answers for a box wholly within it, and for one it cuts through. */
    private static final int WHOLLY_INSIDE = -2;
    private static final int CUT_THROUGH = -1;
    /**
     * The profiler counters a frame's remembered blocks and the lit ones drawn of them add to.
     * decision diagnose-sculkers-lag-and-prove-it-improved-significantly
     */
    private static final String REMEMBERED_COUNTER = "goo_sculker_echo_remembered";
    private static final String DRAWN_COUNTER = "goo_sculker_echo_drawn";

    private SculkerEchoRenderer() {
    }

    /**
     * Draws every lit block within sight, over the world the vision has
     * already dimmed, so an echo shows at its own strength wherever it stands.
     *
     * @param event the level render stage event
     */
    static void draw(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        SculkerEchoes echoes = SculkerSenses.echoes();
        if (player == null || mc.level == null || echoes.remembered().isEmpty()) {
            return;
        }
        Frustum frustum = mc.gameRenderer.getMainCamera().getCullFrustum();
        Map<BlockPos, Float> lit = litInView(echoes.bySection(), new FrustumView(frustum),
                new Sight(player.position(), mc.level.getGameTime(), SculkerSenses.sightReach(),
                        SculkerVision.presence()));
        ProfilerFiller profiler = Profiler.get();
        profiler.incrementCounter(REMEMBERED_COUNTER, echoes.remembered().size());
        profiler.incrementCounter(DRAWN_COUNTER, lit.size());
        if (lit.isEmpty()) {
            return;
        }
        Vec3 camera = mc.gameRenderer.getMainCamera().position();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        RenderType echo = GooRenderTypes.fungusXray(mc.getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS).location());
        drawLit(new LevelEchoWorld(mc), SculkerSenses.echoQuads(), event.getPoseStack(), buffers.getBuffer(echo),
                camera, lit);
        buffers.endBatch(echo);
    }

    /**
     * How far a chunk section stands in the camera's view.
     * decision diagnose-sculkers-lag-and-prove-it-improved-significantly
     */
    enum Sighting {
        /** No block of it can land a pixel. */
        OUT,
        /** The view cuts through it, so each block asks for itself. */
        PARTLY,
        /** Every block of it may land a pixel. */
        WHOLLY
    }

    /**
     * The camera's view, asked of a chunk section first and of a block only
     * where the view cuts through its section, so the blocks of a section out
     * of view cost nothing a frame.
     * decision diagnose-sculkers-lag-and-prove-it-improved-significantly
     */
    interface EchoView {

        /**
         * @param section a packed chunk section position
         * @return how far the section stands in view
         */
        Sighting section(long section);

        /**
         * @param pos a block
         * @return true where any of the block's cell may land in view
         */
        boolean holds(BlockPos pos);
    }

    /**
     * The camera's cull frustum.
     *
     * @param frustum the frustum
     */
    private record FrustumView(Frustum frustum) implements EchoView {

        @Override
        public Sighting section(long section) {
            int minX = SectionPos.sectionToBlockCoord(SectionPos.x(section));
            int minY = SectionPos.sectionToBlockCoord(SectionPos.y(section));
            int minZ = SectionPos.sectionToBlockCoord(SectionPos.z(section));
            int seen = frustum.cubeInFrustum(new BoundingBox(minX, minY, minZ, minX + SECTION_LAST,
                    minY + SECTION_LAST, minZ + SECTION_LAST));
            if (seen == WHOLLY_INSIDE) {
                return Sighting.WHOLLY;
            }
            return seen == CUT_THROUGH ? Sighting.PARTLY : Sighting.OUT;
        }

        @Override
        public boolean holds(BlockPos pos) {
            return frustum.isVisible(new AABB(pos));
        }
    }

    /**
     * Where and when the echoes are seen from.
     *
     * @param standing the player's position
     * @param now      the game time
     * @param reach    the render distance, in blocks
     * @param presence how fully the vision stands, zero to one
     */
    record Sight(Vec3 standing, long now, double reach, float presence) {
    }

    /**
     * Each lit block in view and its strength now, leaving out the faint;
     * a section out of view is passed over without a look at its blocks.
     *
     * @param sections each section's lit faces and their lighting
     * @param view     the camera's view
     * @param sight    where and when the echoes are seen from
     * @return each lit block in view and how strongly it draws
     */
    static Map<BlockPos, Float> litInView(Map<Long, Map<BlockPos, SculkerEchoes.Lit>> sections, EchoView view,
                                          Sight sight) {
        Map<BlockPos, Float> lit = new HashMap<>();
        for (Map.Entry<Long, Map<BlockPos, SculkerEchoes.Lit>> section : sections.entrySet()) {
            Sighting seen = view.section(section.getKey());
            if (seen != Sighting.OUT) {
                section.getValue().forEach((block, lighting) -> {
                    if (seen == Sighting.WHOLLY || view.holds(block)) {
                        float strength = SculkerEchoes.strength(block, lighting, sight.standing(), sight.now(),
                                sight.reach()) * sight.presence();
                        if (strength > FAINTEST) {
                            lit.put(block, strength);
                        }
                    }
                });
            }
        }
        return lit;
    }

    /**
     * What an echo draw reads of the world: each lit block's state, its
     * model's parts and which of its neighbours sound passes through, so a
     * test counts one draw's work headless.
     * decision diagnose-sculkers-lag-and-prove-it-improved-significantly
     */
    interface EchoWorld {

        /**
         * @param pos the block
         * @return the block standing there
         */
        BlockState stateAt(BlockPos pos);

        /**
         * Collects the parts of a block's model, as the world draws it there.
         *
         * @param pos   the block
         * @param state the block standing there
         * @param parts where the parts are gathered
         */
        void collectParts(BlockPos pos, BlockState state, List<BlockStateModelPart> parts);

        /**
         * @param pos the block
         * @return true for air or a block that does not fill its cell
         */
        boolean passesSound(BlockPos pos);
    }

    /**
     * The loaded level, read through the client's model set.
     *
     * @param mc the client, its level loaded
     */
    private record LevelEchoWorld(Minecraft mc) implements EchoWorld {

        @Override
        public BlockState stateAt(BlockPos pos) {
            return mc.level.getBlockState(pos);
        }

        @Override
        public void collectParts(BlockPos pos, BlockState state, List<BlockStateModelPart> parts) {
            BlockStateModel model = mc.getModelManager().getBlockStateModelSet().get(state);
            model.collectParts(mc.level, pos, state, RandomSource.create(pos.asLong()), parts);
        }

        @Override
        public boolean passesSound(BlockPos pos) {
            return SculkerSenses.passesSound(mc.level, pos);
        }
    }

    /**
     * Draws every lit block at its strength, each block's quads collected
     * the first frame it draws and kept until it or a block beside it changes.
     *
     * @param world     the world the blocks stand in
     * @param quads     each block's quads, kept across frames
     * @param poseStack the pose stack
     * @param consumer  the vertex consumer
     * @param camera    the camera's position
     * @param lit       each lit block and how strongly it draws
     */
    static void drawLit(EchoWorld world, EchoQuads quads, PoseStack poseStack, VertexConsumer consumer, Vec3 camera,
                        Map<BlockPos, Float> lit) {
        Brush brush = new Brush(poseStack, consumer, camera, FungusXray.instance(0));
        lit.forEach((pos, strength) -> drawLitSides(brush, quads.at(world, pos), pos, strength));
    }

    /**
     * Draws a lit block's own quads on every side of it facing a block sound
     * passes through, tinted teal at its strength.
     *
     * @param brush    where the frame draws: the pose stack, the consumer, the camera and the tint
     * @param quads    the block's quads, kept from the frame they were collected
     * @param pos      the block
     * @param strength how strongly it draws, zero to one
     */
    private static void drawLitSides(Brush brush, List<BakedQuad> quads, BlockPos pos, float strength) {
        if (quads.isEmpty()) {
            return;
        }
        PoseStack poseStack = brush.poseStack();
        Vec3 camera = brush.camera();
        brush.look().setColor(ARGB.color(Math.round(MOST_OPACITY * strength * OPAQUE), SculkerSenses.SCULK_RGB));
        poseStack.pushPose();
        poseStack.translate(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);
        for (BakedQuad quad : quads) {
            brush.consumer().putBakedQuad(poseStack.last(), quad, brush.look());
        }
        poseStack.popPose();
    }

    /**
     * Where one frame's echoes draw.
     *
     * @param poseStack the pose stack
     * @param consumer  the vertex consumer
     * @param camera    the camera's position
     * @param look      the tint, light and overlay every quad draws with, its color set block by block
     */
    private record Brush(PoseStack poseStack, VertexConsumer consumer, Vec3 camera, QuadInstance look) {
    }
}
