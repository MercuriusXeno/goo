package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.block.ability.VolumeCellBlock;
import com.mercuriusxeno.goo.client.GooSubmitter;
import com.mercuriusxeno.goo.client.ImmediateBuffers;
import com.mercuriusxeno.goo.client.TranslucentVisual.LevelFrame;
import com.mercuriusxeno.goo.client.TranslucentVisual.SortedDraw;
import com.mercuriusxeno.goo.network.MortarCarryPayload;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Draws the Mortar each Level hold carries: each pile as Mortar's own model
 * at its layer count, tesselated in its cell over the disc as the world would
 * tesselate a cell standing there, though no block stands; the opaque quads
 * with the opaque blocks and the translucent core sorted with the translucent
 * visuals.
 * carried-mortar-stops-being-a-block-on-the-level-plane
 * decision translucent-goo-visuals-draw-through-one-checked-sorting-path
 */
public final class CarriedMortarRenderer {

    private CarriedMortarRenderer() {
    }

    /**
     * Takes a carry the server sent into the client's store.
     *
     * @param payload the carry
     * @param context the network context
     */
    public static void onPayload(MortarCarryPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientLevel level = Minecraft.getInstance().level;
            if (level != null) {
                CarriedMortar.CLIENT.accept(payload, level.getGameTime());
            }
        });
    }

    /**
     * Hands the shared path the carried piles' opaque quads, drawn with the
     * opaque blocks, where order is irrelevant.
     *
     * @param frame the frame being drawn
     * @param draws where the draw goes
     */
    public static void collectSolid(LevelFrame frame, Consumer<SortedDraw> draws) {
        Map<BlockPos, BlockState> piles = pilesThisFrame();
        if (!piles.isEmpty()) {
            draws.accept(new SortedDraw(0, () -> drawPass(frame.poseStack(), piles,
                    GooSubmitter.movingBlockCutout(), false)));
        }
    }

    /**
     * Hands the shared path the carried piles' translucent core, sorted by the nearest pile.
     *
     * @param frame the frame being drawn
     * @param draws where the draw goes
     */
    public static void collect(LevelFrame frame, Consumer<SortedDraw> draws) {
        Map<BlockPos, BlockState> piles = pilesThisFrame();
        if (!piles.isEmpty()) {
            draws.accept(SortedDraw.nearestOf(piles.keySet().stream().map(Vec3::atCenterOf),
                    Minecraft.getInstance().gameRenderer.mainCamera().position(),
                    () -> drawPass(frame.poseStack(), piles, GooSubmitter.movingBlockTranslucent(), true)));
        }
    }

    private static Map<BlockPos, BlockState> pilesThisFrame() {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return Map.of();
        }
        return CarriedMortar.CLIENT.drawn(layers -> GooBlocks.ROCK_MORTAR.get().defaultBlockState()
                .setValue(VolumeCellBlock.LAYERS, layers), level.getGameTime());
    }

    /**
     * Draws one batch of the piles: the opaque quads, or the translucent ones.
     *
     * @param poseStack   the level's pose stack, camera relative
     * @param piles       each pile's cell with the Mortar state drawn there
     * @param renderType  the batch's render type
     * @param translucent true for the translucent quads, false for the rest
     */
    private static void drawPass(PoseStack poseStack, Map<BlockPos, BlockState> piles, RenderType renderType,
                                 boolean translucent) {
        Minecraft mc = Minecraft.getInstance();
        ImmediateBuffers buffers = ImmediateBuffers.shared();
        VertexConsumer consumer = buffers.getBuffer(renderType);
        Vec3 camera = mc.gameRenderer.mainCamera().position();
        ModelBlockRenderer tesselator = new ModelBlockRenderer(mc.options.ambientOcclusion().get(), true,
                mc.getBlockColors());
        piles.forEach((pos, state) -> {
            poseStack.pushPose();
            poseStack.translate(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);
            PoseStack.Pose pose = poseStack.last();
            BlockStateModel model = mc.getModelManager().getBlockStateModelSet().get(state);
            tesselator.tesselateBlock((x, y, z, quad, instance) -> {
                if (BlockMingleRenderer.drawsTranslucent(quad) == translucent) {
                    consumer.putBakedQuad(pose, quad, instance);
                }
            }, 0f, 0f, 0f, mc.level, pos, state, model, state.getSeed(pos));
            poseStack.popPose();
        });
        buffers.endBatch(renderType);
    }
}
