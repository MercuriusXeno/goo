package com.mercuriusxeno.goo.client.radial;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.joml.Matrix3x2f;
import org.jspecify.annotations.Nullable;
import java.util.List;

/**
 * One petal layer the GUI draws as quads: the fill, sampling the fluid
 * sprite on the block atlas so the atlas's animation ticks it, or the edge
 * strip in one color with no texture.
 * decision petals-render-the-live-fluid
 *
 * @param pipeline     the GUI pipeline, textured for the fill
 * @param textureSetup the atlas for the fill, none for the edge
 * @param pose         the GUI pose at submit time
 * @param vertices     the quads' corners in screen space, four per quad
 * @param color        the ARGB vertex color every corner carries
 * @param scissorArea  the scissor in force at submit time
 * @param bounds       the screen area the layer covers
 */
record PetalRenderState(RenderPipeline pipeline, TextureSetup textureSetup, Matrix3x2f pose,
                        List<ScreenVertex> vertices, int color, @Nullable ScreenRectangle scissorArea,
                        @Nullable ScreenRectangle bounds) implements GuiElementRenderState {

    /**
     * One quad corner in screen space.
     *
     * @param x the screen x
     * @param y the screen y
     * @param u the atlas u
     * @param v the atlas v
     */
    record ScreenVertex(float x, float y, float u, float v) {
    }

    /**
     * Whether the layer samples a texture, so its corners carry atlas UVs.
     *
     * @return true for the fill
     */
    boolean isTextured() {
        return textureSetup.texure0() != null;
    }

    @Override
    public void buildVertices(VertexConsumer vertexConsumer) {
        for (ScreenVertex vertex : vertices) {
            VertexConsumer corner = vertexConsumer.addVertexWith2DPose(pose, vertex.x(), vertex.y());
            if (isTextured()) {
                corner.setUv(vertex.u(), vertex.v());
            }
            corner.setColor(color);
        }
    }
}
