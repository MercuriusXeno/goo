package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.ability.gate.GateSquare;
import com.mercuriusxeno.goo.block.gate.DragonGateBlock;
import com.mercuriusxeno.goo.block.gate.DragonGateBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * Draws a Dragon Gate cell's share of the gate's two by two square as the
 * End portal's starfield, a flat quad lying on the outer side of the cell's
 * layer, seen from either side.
 * Decision end-clears-blocks-and-opens-a-portal.
 */
public class DragonGateRenderer implements BlockEntityRenderer<DragonGateBlockEntity, DragonGateRenderer.State> {

    /** What one frame draws of a gate cell. */
    public static class State extends BlockEntityRenderState {
        /** The share of the square the cell holds, in the cell's own coordinates. */
        public AABB layer = new AABB(0, 0, 0, 1, GateSquare.DEPTH, 1);
        /** The face the gate looks out of. */
        public Direction facing = Direction.UP;
    }

    /**
     * Creates the gate renderer.
     *
     * @param context the renderer context
     */
    public DragonGateRenderer(BlockEntityRendererProvider.Context context) {
        // The square is drawn from the cell's state; the context carries nothing it draws from.
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(DragonGateBlockEntity gate, State state, float partialTick, Vec3 cameraPos,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderState.extractBase(gate, state, breakProgress);
        BlockState cell = gate.getBlockState();
        state.facing = cell.getValue(DragonGateBlock.FACING);
        state.layer = GateSquare.cellLayer(state.facing, cell.getValue(DragonGateBlock.ACROSS),
                cell.getValue(DragonGateBlock.ALONG));
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector nodeCollector,
                       CameraRenderState camera) {
        Vector3f[] corners = outerQuad(state.layer, state.facing);
        nodeCollector.submitCustomGeometry(poseStack, RenderTypes.endPortal(), (pose, buffer) -> {
            for (int index = 0; index < corners.length; index++) {
                buffer.addVertex(pose, corners[index]);
            }
            for (int index = corners.length - 1; index >= 0; index--) {
                buffer.addVertex(pose, corners[index]);
            }
        });
    }

    /**
     * The four corners of the layer's side facing out of the gate, in order
     * around the quad.
     *
     * @param layer  the layer's box in the cell's coordinates
     * @param facing the face the gate looks out of
     * @return the corners
     */
    static Vector3f[] outerQuad(AABB layer, Direction facing) {
        Direction.Axis[] plane = GateSquare.planeOf(facing);
        double out = facing.getAxisDirection() == Direction.AxisDirection.POSITIVE
                ? layer.max(facing.getAxis()) : layer.min(facing.getAxis());
        double[][] spans = {
            {layer.min(plane[0]), layer.min(plane[1])}, {layer.max(plane[0]), layer.min(plane[1])},
            {layer.max(plane[0]), layer.max(plane[1])}, {layer.min(plane[0]), layer.max(plane[1])}};
        Vector3f[] corners = new Vector3f[spans.length];
        for (int index = 0; index < spans.length; index++) {
            float[] point = new float[Direction.Axis.values().length];
            point[facing.getAxis().ordinal()] = (float) out;
            point[plane[0].ordinal()] = (float) spans[index][0];
            point[plane[1].ordinal()] = (float) spans[index][1];
            corners[index] = new Vector3f(point[Direction.Axis.X.ordinal()], point[Direction.Axis.Y.ordinal()],
                    point[Direction.Axis.Z.ordinal()]);
        }
        return corners;
    }
}
