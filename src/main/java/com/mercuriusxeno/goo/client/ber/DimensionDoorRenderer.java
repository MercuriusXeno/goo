package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.block.outsider.DimensionDoorBlockEntity;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.client.outsider.SkyFunnel;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * Draws a room doorway cell as a full cube of the sky funnel's starfield,
 * every face seen from either side, so the doorway's two cells stand the one
 * by one by two cuboid of portal the funnel rises from. The stars sit on
 * each face at the pillar's blocks per repeat, the side faces counting up
 * from the doorway's top as the pillar does, so the door and the funnel read
 * as one material where they meet.
 * decision sky-funnel-points-down-to-the-doorway-from-any-distance
 */
public class DimensionDoorRenderer implements BlockEntityRenderer<DimensionDoorBlockEntity, BlockEntityRenderState> {

    /** The cube's six faces, each four corners in order round it. */
    private static final Vector3f[][] FACES = {
        {new Vector3f(0, 0, 0), new Vector3f(0, 1, 0), new Vector3f(0, 1, 1), new Vector3f(0, 0, 1)},
        {new Vector3f(1, 0, 0), new Vector3f(1, 0, 1), new Vector3f(1, 1, 1), new Vector3f(1, 1, 0)},
        {new Vector3f(0, 0, 0), new Vector3f(0, 0, 1), new Vector3f(1, 0, 1), new Vector3f(1, 0, 0)},
        {new Vector3f(0, 1, 0), new Vector3f(1, 1, 0), new Vector3f(1, 1, 1), new Vector3f(0, 1, 1)},
        {new Vector3f(0, 0, 0), new Vector3f(1, 0, 0), new Vector3f(1, 1, 0), new Vector3f(0, 1, 0)},
        {new Vector3f(0, 0, 1), new Vector3f(0, 1, 1), new Vector3f(1, 1, 1), new Vector3f(1, 0, 1)}};
    /** The axis each face's corners share, in the order FACES runs: east, east, up, up, south, south. */
    private static final int[] FACE_AXES = {0, 0, 1, 1, 2, 2};
    private static final int EAST = 0;
    private static final int UP = 1;

    /**
     * Creates the doorway renderer.
     *
     * @param context the renderer context, which carries nothing the doorway draws from
     */
    public DimensionDoorRenderer(BlockEntityRendererProvider.@Nullable Context context) {
        // The doorway draws from its cell alone.
    }

    @Override
    public BlockEntityRenderState createRenderState() {
        return new BlockEntityRenderState();
    }

    @Override
    public void extractRenderState(DimensionDoorBlockEntity door, BlockEntityRenderState state, float partialTick,
                                   Vec3 cameraPos, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderState.extractBase(door, state, breakProgress);
    }

    @Override
    public void submit(BlockEntityRenderState state, PoseStack poseStack, SubmitNodeCollector nodeCollector,
                       CameraRenderState camera) {
        double belowTheTop = state.blockPos.getY() - SkyFunnel.spoutHeight();
        nodeCollector.submitCustomGeometry(poseStack, GooRenderTypes.SKY_FUNNEL_TYPE, (pose, buffer) -> {
            for (int face = 0; face < FACES.length; face++) {
                Vector3f[] corners = FACES[face];
                for (Vector3f corner : corners) {
                    addCorner(buffer, pose, corner, FACE_AXES[face], belowTheTop);
                }
                for (int index = corners.length - 1; index >= 0; index--) {
                    addCorner(buffer, pose, corners[index], FACE_AXES[face], belowTheTop);
                }
            }
        });
    }

    /**
     * Writes one corner of a face, its stars across the face by place and up
     * a side face by its height above the doorway's top, at full brightness.
     *
     * @param buffer      the doorway's vertex consumer
     * @param pose        the cell's pose
     * @param corner      the corner within the cell
     * @param across      the axis the face's corners share
     * @param belowTheTop the cell's foot's height relative to the doorway's top
     */
    private static void addCorner(VertexConsumer buffer, PoseStack.Pose pose, Vector3f corner, int across,
                                  double belowTheTop) {
        double alongU = across == EAST ? corner.z() : corner.x();
        double alongV = across == UP ? corner.z() : belowTheTop + corner.y();
        buffer.addVertex(pose, corner)
                .setUv((float) (alongU / SkyFunnel.PILLAR_BLOCKS_PER_REPEAT),
                        (float) (alongV / SkyFunnel.PILLAR_BLOCKS_PER_REPEAT))
                .setColor(1F, 1F, 1F, 1F);
    }
}
