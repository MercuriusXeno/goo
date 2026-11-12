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
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.data.AtlasIds;
import net.minecraft.util.ARGB;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import java.util.ArrayList;
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
        if (player == null || mc.level == null || SculkerSenses.echoes().remembered().isEmpty()) {
            return;
        }
        Map<BlockPos, Float> lit = litBlocks(player.position(), mc.level.getGameTime());
        if (lit.isEmpty()) {
            return;
        }
        Vec3 camera = mc.gameRenderer.getMainCamera().position();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        RenderType echo = GooRenderTypes.fungusXray(mc.getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS).location());
        drawLit(new LevelEchoWorld(mc), poseStack, buffers.getBuffer(echo), camera, lit);
        buffers.endBatch(echo);
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
     * Draws every lit block at its strength.
     *
     * @param world     the world the blocks stand in
     * @param poseStack the pose stack
     * @param consumer  the vertex consumer
     * @param camera    the camera's position
     * @param lit       each lit block and how strongly it draws
     */
    static void drawLit(EchoWorld world, PoseStack poseStack, VertexConsumer consumer, Vec3 camera,
                        Map<BlockPos, Float> lit) {
        lit.forEach((pos, strength) -> drawLitSides(world, poseStack, consumer, camera, pos, strength));
    }

    /**
     * Each lit block's strength now, leaving out the faint.
     *
     * @param standing the player's position
     * @param now      the game time
     * @return each lit block and how strongly it draws
     */
    private static Map<BlockPos, Float> litBlocks(Vec3 standing, long now) {
        Map<BlockPos, Float> lit = new HashMap<>();
        float presence = SculkerVision.presence();
        double reach = SculkerSenses.sightReach();
        SculkerSenses.echoes().remembered().forEach((block, lighting) -> {
            float strength = SculkerEchoes.strength(block, lighting, standing, now, reach) * presence;
            if (strength > FAINTEST) {
                lit.put(block, strength);
            }
        });
        return lit;
    }

    /**
     * Draws a lit block's own quads on every side of it facing a block sound
     * passes through, tinted teal at its strength.
     *
     * @param world     the world the block stands in
     * @param poseStack the pose stack
     * @param consumer  the vertex consumer
     * @param camera    the camera's position
     * @param pos       the block
     * @param strength  how strongly it draws, zero to one
     */
    private static void drawLitSides(EchoWorld world, PoseStack poseStack, VertexConsumer consumer, Vec3 camera,
                                     BlockPos pos, float strength) {
        BlockState state = world.stateAt(pos);
        if (state.isAir()) {
            return;
        }
        List<BlockStateModelPart> parts = new ArrayList<>();
        world.collectParts(pos, state, parts);
        QuadInstance look = FungusXray.instance(ARGB.color(Math.round(MOST_OPACITY * strength * OPAQUE),
                SculkerSenses.SCULK_RGB));
        poseStack.pushPose();
        poseStack.translate(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);
        for (BlockStateModelPart part : parts) {
            for (Direction side : Direction.values()) {
                if (world.passesSound(pos.relative(side))) {
                    part.getQuads(side).forEach(quad -> consumer.putBakedQuad(poseStack.last(), quad, look));
                }
            }
            part.getQuads(null).forEach(quad -> consumer.putBakedQuad(poseStack.last(), quad, look));
        }
        poseStack.popPose();
    }
}
