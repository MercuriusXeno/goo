package com.mercuriusxeno.goo.client.ability;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.Direction;

/**
 * One layer of a held ghost, drawn in both passes: through its render type
 * over blocks, depth tested, then through its twin that ignores depth.
 * held-visual-ghosts-the-landing-in-two-passes
 *
 * @param overBlocks    the render type the layer draws through over blocks, depth tested
 * @param throughBlocks the render type the layer draws through behind blocks, depth ignored
 * @param emitter       emits the layer's geometry
 */
public record HeldLayer(RenderType overBlocks, RenderType throughBlocks, Emitter emitter) {

    /**
     * Emits a layer at resting size about the block center, in block-local
     * coordinates as a burnout draws, at a share of the landing's opacity.
     */
    @FunctionalInterface
    public interface Emitter {
        /**
         * @param pose       the pose entry
         * @param c          the vertex consumer
         * @param ghost      the ghost being drawn
         * @param face       the face the throw strikes, the landing's placed face
         * @param opacity    the share of the landing's opacity, in [0, 1]
         * @param nowSeconds seconds on the real-time clock, for a shader that animates while held
         */
        void emit(PoseStack.Pose pose, VertexConsumer c, HeldGhost ghost, Direction face, float opacity,
                  double nowSeconds);
    }
}
