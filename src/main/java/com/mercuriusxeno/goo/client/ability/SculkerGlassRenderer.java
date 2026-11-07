package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
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
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Draws Sculker's glass field: every block within the field's radius of the
 * local player's eyes draws its own model and texture, ghosted at the
 * field's strength for its distance, farthest first so the nearer glass
 * lies over it, depth tested so the diver's own body and every entity in
 * front of the glass shows over it. A block draws only its sides where two
 * different block types meet, air among them, one face to each meeting: a
 * block's own face against air, and the farther block's face where two
 * blocks meet, so the diver sees the places where different blocks meet,
 * from inside the stone as from the open, and nothing of the same block
 * carrying on.
 * decision sculker-shows-blocks-translucent-close-up
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class SculkerGlassRenderer {

    /** The opacity the near blocks draw at: mostly solid, the caves behind them showing only faintly. */
    private static final float MOST_OPACITY = 0.85f;
    /** A block this faint is not drawn at all. */
    private static final float FAINTEST = 0.03f;
    private static final int OPAQUE = 255;
    private static final int WHITE_RGB = 0xFFFFFF;

    private SculkerGlassRenderer() {
    }

    /**
     * Draws the glass field while the local player's Sculker stands.
     *
     * @param event the level render stage event
     */
    @SubscribeEvent
    public static void onAfterTranslucentBlocks(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || SculkerVision.presence() < SculkerVision.GONE) {
            return;
        }
        Vec3 eyes = player.getEyePosition();
        List<BlockPos> field = fieldAround(mc, eyes);
        if (field.isEmpty()) {
            return;
        }
        PoseStack poseStack = event.getPoseStack();
        Vec3 camera = mc.gameRenderer.getMainCamera().position();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        RenderType glass = GooRenderTypes.sculkerGlass(mc.getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS).location());
        VertexConsumer consumer = buffers.getBuffer(glass);
        for (BlockPos pos : field) {
            float strength = SculkerGlass.strengthAt(Vec3.atCenterOf(pos).distanceTo(eyes)) * SculkerVision.presence();
            QuadInstance look = FungusXray.instance(ARGB.color(Math.round(MOST_OPACITY * strength * OPAQUE), WHITE_RGB));
            drawBorderingFaces(mc, poseStack, consumer, camera, eyes, pos, look);
        }
        buffers.endBatch(glass);
    }

    /**
     * Draws a block's own quads on the sides that border something else, and
     * its quads that face no side.
     *
     * @param mc        the client, its level loaded
     * @param poseStack the pose stack
     * @param consumer  the vertex consumer
     * @param camera    the camera's position
     * @param eyes      the diver's eyes
     * @param pos       the block
     * @param look      the color, light and overlay the quads take
     */
    private static void drawBorderingFaces(Minecraft mc, PoseStack poseStack, VertexConsumer consumer, Vec3 camera,
                                           Vec3 eyes, BlockPos pos, QuadInstance look) {
        BlockState state = mc.level.getBlockState(pos);
        BlockStateModel model = mc.getModelManager().getBlockStateModelSet().get(state);
        List<BlockStateModelPart> parts = new ArrayList<>();
        model.collectParts(mc.level, pos, state, RandomSource.create(pos.asLong()), parts);
        poseStack.pushPose();
        poseStack.translate(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);
        for (BlockStateModelPart part : parts) {
            for (Direction side : Direction.values()) {
                BlockPos beside = pos.relative(side);
                BlockState besideState = mc.level.getBlockState(beside);
                if (drawsFace(besideState.isAir(), besideState.equals(state),
                        Vec3.atCenterOf(pos).distanceToSqr(eyes), Vec3.atCenterOf(beside).distanceToSqr(eyes))) {
                    part.getQuads(side).forEach(quad -> consumer.putBakedQuad(poseStack.last(), quad, look));
                }
            }
            part.getQuads(null).forEach(quad -> consumer.putBakedQuad(poseStack.last(), quad, look));
        }
        poseStack.popPose();
    }

    /**
     * Whether a block's side draws: only where two different block types
     * meet, never where the same block carries on, and of the two faces where
     * they meet only one, so no seam shows: against air the block's own face,
     * and against another block the face of whichever stands farther from the
     * diver, the one the diver looks at.
     *
     * @param besideIsAir    whether air stands beside that side
     * @param besideIsSame   whether the same block stands beside
     * @param blockDistance  how far the block stands from the diver's eyes
     * @param besideDistance how far the block beside stands from them
     * @return true where the side draws
     */
    static boolean drawsFace(boolean besideIsAir, boolean besideIsSame, double blockDistance, double besideDistance) {
        if (besideIsSame) {
            return false;
        }
        return besideIsAir || blockDistance > besideDistance;
    }

    /**
     * The blocks within the field that draw, farthest first.
     *
     * @param mc   the client, its level loaded
     * @param eyes the player's eyes
     * @return the cells holding a block strong enough to show
     */
    private static List<BlockPos> fieldAround(Minecraft mc, Vec3 eyes) {
        int reach = (int) Math.ceil(SculkerGlass.FIELD_RADIUS);
        BlockPos center = BlockPos.containing(eyes);
        List<BlockPos> field = new ArrayList<>();
        for (BlockPos cell : BlockPos.betweenClosed(center.offset(-reach, -reach, -reach), center.offset(reach, reach, reach))) {
            if (!mc.level.getBlockState(cell).isAir()
                    && SculkerGlass.strengthAt(Vec3.atCenterOf(cell).distanceTo(eyes)) > FAINTEST) {
                field.add(cell.immutable());
            }
        }
        field.sort(Comparator.comparingDouble((BlockPos cell) -> Vec3.atCenterOf(cell).distanceToSqr(eyes)).reversed());
        return field;
    }
}
