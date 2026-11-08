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
        VertexConsumer consumer = buffers.getBuffer(echo);
        lit.forEach((pos, block) -> drawLitSides(mc, poseStack, consumer, camera, pos, block));
        buffers.endBatch(echo);
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
     * Draws a lit block's own quads on every side of it open to air, tinted
     * teal at its strength.
     *
     * @param mc        the client, its level loaded
     * @param poseStack the pose stack
     * @param consumer  the vertex consumer
     * @param camera    the camera's position
     * @param pos       the block
     * @param strength  how strongly it draws, zero to one
     */
    private static void drawLitSides(Minecraft mc, PoseStack poseStack, VertexConsumer consumer, Vec3 camera,
                                     BlockPos pos, float strength) {
        BlockState state = mc.level.getBlockState(pos);
        if (state.isAir()) {
            return;
        }
        BlockStateModel model = mc.getModelManager().getBlockStateModelSet().get(state);
        List<BlockStateModelPart> parts = new ArrayList<>();
        model.collectParts(mc.level, pos, state, RandomSource.create(pos.asLong()), parts);
        QuadInstance look = FungusXray.instance(ARGB.color(Math.round(MOST_OPACITY * strength * OPAQUE),
                SculkerSenses.SCULK_RGB));
        poseStack.pushPose();
        poseStack.translate(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);
        for (BlockStateModelPart part : parts) {
            for (Direction side : Direction.values()) {
                if (mc.level.getBlockState(pos.relative(side)).isAir()) {
                    part.getQuads(side).forEach(quad -> consumer.putBakedQuad(poseStack.last(), quad, look));
                }
            }
            part.getQuads(null).forEach(quad -> consumer.putBakedQuad(poseStack.last(), quad, look));
        }
        poseStack.popPose();
    }
}
