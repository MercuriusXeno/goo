package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.block.outsider.VoidstoneBlockEntity;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import org.jspecify.annotations.Nullable;

/**
 * Draws each exposed face of a voidstone through the voidstone shader, whose
 * stars are placed by world position, over the block's own dark faces.
 * decision outsider-is-a-channeled-pocket-with-a-door-voidstone-and-a-spiral-platform
 */
public class VoidstoneRenderer implements BlockEntityRenderer<VoidstoneBlockEntity, VoidstoneRenderer.State> {

    /** Blocks out a voidstone's stars still draw: a grown platform reaches far. */
    private static final int STAR_DISTANCE = 96;
    private static final int OPAQUE_WHITE = 0xFFFFFFFF;
    private static final int X = 0;
    private static final int Y = 1;
    private static final int Z = 2;

    /**
     * Which of a voidstone's faces show.
     */
    public static class State extends BlockEntityRenderState {
        /** One bit per {@link Direction} ordinal, set where that face shows. */
        public int shownFaces;
    }

    /**
     * @param context the renderer context, which the stars draw nothing from
     */
    public VoidstoneRenderer(BlockEntityRendererProvider.Context context) {
        // The faces are drawn from the block's neighbours alone.
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(VoidstoneBlockEntity voidstone, State state, float partialTick, Vec3 cameraPos,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderState.extractBase(voidstone, state, breakProgress);
        state.shownFaces = shownFaces(voidstone.getLevel(), voidstone.getBlockPos());
    }

    private static int shownFaces(@Nullable Level level, BlockPos pos) {
        int shown = 0;
        for (Direction face : Direction.values()) {
            BlockState beside = level == null ? null : level.getBlockState(pos.relative(face));
            // A face shows unless its neighbour covers it whole, as the world culls a block's faces.
            if (beside == null || beside.getFaceOcclusionShape(face.getOpposite()) != Shapes.block()) {
                shown |= 1 << face.ordinal();
            }
        }
        return shown;
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector nodeCollector,
                       CameraRenderState camera) {
        if (state.shownFaces == 0) {
            return;
        }
        nodeCollector.submitCustomGeometry(poseStack, GooRenderTypes.VOIDSTONE_TYPE, (pose, buffer) -> {
            for (Direction face : Direction.values()) {
                if ((state.shownFaces & 1 << face.ordinal()) != 0) {
                    addFace(pose, buffer, face);
                }
            }
        });
    }

    private static void addFace(PoseStack.Pose pose, VertexConsumer buffer, Direction face) {
        float[][] corners = cornersOf(face);
        for (float[] corner : corners) {
            buffer.addVertex(pose, corner[X], corner[Y], corner[Z]).setColor(OPAQUE_WHITE)
                    .setNormal(pose, face.getStepX(), face.getStepY(), face.getStepZ());
        }
    }

    /**
     * The four corners of one face of the unit block, wound counter-clockwise seen from outside.
     *
     * @param face the face
     * @return the corners
     */
    static float[][] cornersOf(Direction face) {
        return switch (face) {
            case DOWN -> new float[][]{{0, 0, 0}, {1, 0, 0}, {1, 0, 1}, {0, 0, 1}};
            case UP -> new float[][]{{0, 1, 0}, {0, 1, 1}, {1, 1, 1}, {1, 1, 0}};
            case NORTH -> new float[][]{{0, 0, 0}, {0, 1, 0}, {1, 1, 0}, {1, 0, 0}};
            case SOUTH -> new float[][]{{0, 0, 1}, {1, 0, 1}, {1, 1, 1}, {0, 1, 1}};
            case WEST -> new float[][]{{0, 0, 0}, {0, 0, 1}, {0, 1, 1}, {0, 1, 0}};
            case EAST -> new float[][]{{1, 0, 0}, {1, 1, 0}, {1, 1, 1}, {1, 0, 1}};
        };
    }

    @Override
    public int getViewDistance() {
        return STAR_DISTANCE;
    }
}
