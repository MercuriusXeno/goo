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
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Draws Sculker's glass field: every block within the field's radius of the
 * local player's eyes draws its own model and texture, ghosted at the
 * field's strength for its distance, nearest first so only the nearest
 * face shows at each point, depth tested so the diver's own body and every entity in
 * front of the glass shows over it. The stone around the diver shows faintly
 * as its own texture, and where two different blocks meet, air among them,
 * shows a little brighter, one face to each place so no seam shows: the
 * same block carrying on shows only its sides facing the diver, a block's
 * own face against air shows from either side, and where two blocks meet
 * the farther block's face shows.
 * decision sculker-shows-blocks-translucent-close-up
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class SculkerGlassRenderer {

    /** The opacity the stone carrying on around the diver draws at: faint. */
    private static final float STONE_OPACITY = 0.3f;
    /** The opacity a place where two different blocks meet draws at: a little brighter than the stone. */
    private static final float MEET_OPACITY = 0.55f;
    /** Half a block, from a block's center to its face. */
    private static final double HALF = 0.5;
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
            drawShowingFaces(mc, poseStack, consumer, camera, eyes, pos, strength);
        }
        buffers.endBatch(glass);
    }

    /**
     * Draws a block's own quads on the sides that show, each at its opacity,
     * and its quads that face no side at the stone's.
     *
     * @param mc        the client, its level loaded
     * @param poseStack the pose stack
     * @param consumer  the vertex consumer
     * @param camera    the camera's position
     * @param eyes      the diver's eyes
     * @param pos       the block
     * @param strength  the field's strength at the block, zero to one
     */
    private static void drawShowingFaces(Minecraft mc, PoseStack poseStack, VertexConsumer consumer, Vec3 camera,
                                         Vec3 eyes, BlockPos pos, float strength) {
        BlockState state = mc.level.getBlockState(pos);
        BlockStateModel model = mc.getModelManager().getBlockStateModelSet().get(state);
        List<BlockStateModelPart> parts = new ArrayList<>();
        model.collectParts(mc.level, pos, state, RandomSource.create(pos.asLong()), parts);
        poseStack.pushPose();
        poseStack.translate(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);
        for (BlockStateModelPart part : parts) {
            for (Direction side : Direction.values()) {
                QuadInstance look = sideLook(mc, state, pos, side, eyes, strength);
                if (look != null) {
                    part.getQuads(side).forEach(quad -> consumer.putBakedQuad(poseStack.last(), quad, look));
                }
            }
            QuadInstance inner = lookAt(STONE_OPACITY * strength);
            part.getQuads(null).forEach(quad -> consumer.putBakedQuad(poseStack.last(), quad, inner));
        }
        poseStack.popPose();
    }

    /**
     * Whether a block's side shows, one face to each place so no seam shows:
     * where the same block carries on, only the side facing the diver, so the
     * stone the diver is inside shows none and two neighbors never both draw
     * the face they share; against air, the block's own face, seen from either
     * side; where two different blocks meet, the face of whichever stands
     * farther from the diver, the one the diver looks at.
     *
     * @param besideIsAir    whether air stands beside that side
     * @param besideIsSame   whether the same block stands beside
     * @param facesTheDiver  whether that side faces the diver's eyes
     * @param blockDistance  how far the block stands from the diver's eyes
     * @param besideDistance how far the block beside stands from them
     * @return true where the side shows
     */
    static boolean faceShows(boolean besideIsAir, boolean besideIsSame, boolean facesTheDiver, double blockDistance,
                             double besideDistance) {
        if (besideIsSame) {
            return facesTheDiver;
        }
        return besideIsAir || blockDistance > besideDistance;
    }

    /**
     * How opaque a side draws at full strength: the stone carrying on faint,
     * a place where two different blocks meet a little brighter.
     *
     * @param besideIsSame whether the same block stands beside
     * @return the side's opacity, zero to one
     */
    static float faceOpacity(boolean besideIsSame) {
        return besideIsSame ? STONE_OPACITY : MEET_OPACITY;
    }

    /**
     * How a block's side draws, or null where it does not show.
     *
     * @param mc       the client, its level loaded
     * @param state    the block
     * @param pos      its cell
     * @param side     the side
     * @param eyes     the diver's eyes
     * @param strength the field's strength at the block
     * @return the side's color, light and overlay, or null where it does not show
     */
    private static @Nullable QuadInstance sideLook(Minecraft mc, BlockState state, BlockPos pos, Direction side,
                                                   Vec3 eyes, float strength) {
        BlockPos beside = pos.relative(side);
        BlockState besideState = mc.level.getBlockState(beside);
        boolean same = besideState.equals(state);
        Vec3 normal = Vec3.atLowerCornerOf(side.getUnitVec3i());
        Vec3 center = Vec3.atCenterOf(pos);
        boolean facesTheDiver = eyes.subtract(center.add(normal.scale(HALF))).dot(normal) > 0;
        boolean shows = faceShows(besideState.isAir(), same, facesTheDiver, center.distanceToSqr(eyes),
                Vec3.atCenterOf(beside).distanceToSqr(eyes));
        return shows ? lookAt(faceOpacity(same) * strength) : null;
    }

    private static QuadInstance lookAt(float opacity) {
        return FungusXray.instance(ARGB.color(Math.round(opacity * OPAQUE), WHITE_RGB));
    }

    /**
     * The blocks within the field that draw, nearest first, so with depth
     * written the nearest face wins each point and no layers stack.
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
        field.sort(Comparator.comparingDouble((BlockPos cell) -> Vec3.atCenterOf(cell).distanceToSqr(eyes)));
        return field;
    }
}
